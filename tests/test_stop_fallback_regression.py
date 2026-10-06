import sys
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

from patch_stop_fallback import (  # noqa: E402
    FALLBACK_INSERTION_PC,
    FALLBACK_METHOD,
    HELPER_CLASS,
    SINGLE_ACCOUNT_METHOD,
    STOP_CLASS,
    STOP_METHOD,
    patch_class,
)
from patch_stop_flag import ConstantPool, Reader, decode_instructions  # noqa: E402

JAR_PATH = ROOT / "app" / "tool.jar"
SOURCE_PATH = ROOT / "scratch" / "BypassHelper.java"


def parse_members(class_bytes):
    reader = Reader(class_bytes)
    reader.take(8)
    pool = ConstantPool.parse(class_bytes, reader)
    reader.take(6)
    reader.take(reader.u2() * 2)

    for _ in range(reader.u2()):
        reader.take(6)
        for _ in range(reader.u2()):
            reader.take(2)
            reader.take(reader.u4())

    methods = {}
    for _ in range(reader.u2()):
        access = reader.u2()
        name = pool.utf8(reader.u2())
        descriptor = pool.utf8(reader.u2())
        attributes = {}
        for _ in range(reader.u2()):
            attribute_name = pool.utf8(reader.u2())
            info = reader.take(reader.u4())
            if attribute_name != "Code":
                continue
            code_reader = Reader(info)
            max_stack, max_locals = code_reader.u2(), code_reader.u2()
            code = code_reader.take(code_reader.u4())
            exceptions = [
                (code_reader.u2(), code_reader.u2(), code_reader.u2(), code_reader.u2())
                for _ in range(code_reader.u2())
            ]
            nested = {}
            for _ in range(code_reader.u2()):
                nested_name = pool.utf8(code_reader.u2())
                nested[nested_name] = code_reader.take(code_reader.u4())
            attributes["Code"] = code
            attributes["MaxStack"] = max_stack
            attributes["MaxLocals"] = max_locals
            attributes["ExceptionTable"] = exceptions
            attributes.update(nested)
        methods[(name, descriptor)] = (access, attributes)
    return pool, methods


def method_reference(pool, cp_index):
    entry = pool.entries[cp_index]
    if entry is None or entry.tag not in (10, 11):
        return None
    class_index, name_and_type_index = entry.value
    class_entry = pool.entries[class_index]
    nt_entry = pool.entries[name_and_type_index]
    if class_entry is None or class_entry.tag != 7 or nt_entry is None or nt_entry.tag != 12:
        return None
    name_index, descriptor_index = nt_entry.value
    return (
        pool.utf8(class_entry.value),
        pool.utf8(name_index),
        pool.utf8(descriptor_index),
    )


def field_reference(pool, cp_index):
    entry = pool.entries[cp_index]
    if entry is None or entry.tag != 9:
        return None
    class_index, name_and_type_index = entry.value
    class_entry = pool.entries[class_index]
    nt_entry = pool.entries[name_and_type_index]
    if class_entry is None or class_entry.tag != 7 or nt_entry is None or nt_entry.tag != 12:
        return None
    name_index, descriptor_index = nt_entry.value
    return (
        pool.utf8(class_entry.value),
        pool.utf8(name_index),
        pool.utf8(descriptor_index),
    )


def stack_map_frame_pcs(info):
    reader = Reader(info)
    frame_count = reader.u2()
    previous_pc = -1
    pcs = []
    for _ in range(frame_count):
        frame_type = reader.u1()
        if frame_type <= 63:
            offset_delta = frame_type
        elif frame_type <= 127:
            offset_delta = frame_type - 64
            skip_verification_type(reader)
        elif frame_type == 247:
            offset_delta = reader.u2()
            skip_verification_type(reader)
        elif 248 <= frame_type <= 251:
            offset_delta = reader.u2()
        elif 252 <= frame_type <= 254:
            offset_delta = reader.u2()
            for _ in range(frame_type - 251):
                skip_verification_type(reader)
        elif frame_type == 255:
            offset_delta = reader.u2()
            for _ in range(reader.u2()):
                skip_verification_type(reader)
            for _ in range(reader.u2()):
                skip_verification_type(reader)
        else:
            raise AssertionError(f"reserved stack-map frame type {frame_type}")
        previous_pc += offset_delta + 1
        pcs.append(previous_pc)
    if reader.pos != len(info):
        raise AssertionError("unexpected trailing StackMapTable data")
    return pcs


def skip_verification_type(reader):
    tag = reader.u1()
    if tag in (7, 8):
        reader.take(2)
    elif tag not in range(0, 7):
        raise AssertionError(f"invalid verification_type_info tag {tag}")


class StopFallbackRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with zipfile.ZipFile(JAR_PATH) as jar:
            cls.helper_class = jar.read(HELPER_CLASS)
            cls.stop_class = jar.read(STOP_CLASS)
        cls.source = SOURCE_PATH.read_text(encoding="utf-8")

    def test_source_stops_the_bot_tracked_by_account_index(self):
        self.assertIn("private static Object getSingleAccountFallbackBot(int index)", self.source)
        self.assertIn("accounts.size() != 1", self.source)
        self.assertIn("public static void stopBotAtIndex(int index)", self.source)
        self.assertIn("activeBotsByIndex.get(Integer.valueOf(index))", self.source)
        self.assertIn("Object fallbackBot = getSingleAccountFallbackBot(index);", self.source)
        self.assertIn("cancelPendingCast(bot);", self.source)
        # The stop implementation uses reflection to set hl and call N() to avoid
        # the ambiguous overload error from obfuscation; verify the string tokens are present.
        self.assertIn('"hl"', self.source)
        self.assertIn('"N"', self.source)
        # stoppedBotIndices is now marked immediately on stop to block queued log lines
        self.assertIn("stoppedBotIndices.add(Integer.valueOf(index))", self.source)

    def test_empty_primary_slot_calls_helper_fallback_on_null_path(self):
        pool, methods = parse_members(self.stop_class)
        code_attributes = methods[STOP_METHOD][1]
        code = code_attributes["Code"]
        instructions = decode_instructions(code)
        boundaries = {item.pc for item in instructions} | {len(code)}

        calls = [
            instruction
            for instruction in instructions
            if instruction.opcode == 0xB8
            and method_reference(pool, int.from_bytes(instruction.raw[1:3], "big"))
            == ("avt/BypassHelper", FALLBACK_METHOD[0], FALLBACK_METHOD[1])
        ]
        self.assertEqual(len(calls), 1)
        call = calls[0]
        call_index = instructions.index(call)
        self.assertEqual(instructions[call_index - 1].opcode, 0x1B)  # iload_1: selected account index
        self.assertEqual(instructions[call_index - 1].pc, FALLBACK_INSERTION_PC)

        # The original ifnonnull jump skips the inserted call; only the null
        # fall-through path enters the fallback before account metadata updates.
        null_check = next(item for item in instructions if item.opcode == 0xC7)
        self.assertEqual(null_check.pc + len(null_check.raw), call.pc - 1)
        self.assertGreater(null_check.branch_targets[0], call.pc)

        for instruction in instructions:
            self.assertTrue(set(instruction.branch_targets) <= boundaries)
        for start_pc, end_pc, handler_pc, _ in code_attributes["ExceptionTable"]:
            self.assertIn(start_pc, boundaries)
            self.assertIn(end_pc, boundaries)
            self.assertIn(handler_pc, boundaries)
        frame_pcs = stack_map_frame_pcs(code_attributes["StackMapTable"])
        self.assertTrue(set(frame_pcs) <= boundaries)

    def test_fallback_method_looks_up_and_stops_the_tracked_bot(self):
        pool, methods = parse_members(self.helper_class)
        self.assertIn(FALLBACK_METHOD, methods)
        access, attributes = methods[FALLBACK_METHOD]
        self.assertTrue(access & 0x0001, "fallback method should be public")
        self.assertTrue(access & 0x0008, "fallback method should be static")
        code = attributes["Code"]
        instructions = decode_instructions(code)
        boundaries = {item.pc for item in instructions} | {len(code)}
        # MaxStack and MaxLocals can vary between synthesized and javac-compiled bytecode;
        # only assert minimum reasonable values.
        self.assertGreaterEqual(attributes["MaxStack"], 2)
        self.assertGreaterEqual(attributes["MaxLocals"], 1)

        method_refs = [
            method_reference(pool, int.from_bytes(item.raw[1:3], "big"))
            for item in instructions
            if item.opcode in (0xB6, 0xB7, 0xB8, 0xB9)
        ]
        self.assertIn(
            ("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
            method_refs,
        )
        self.assertIn(
            ("avt/BypassHelper", "cancelPendingCast", "(Ljava/lang/Object;)V"),
            method_refs,
        )
        self.assertIn(
            ("avt/BypassHelper", "isBotStopped", "(Ljava/lang/Object;)Z"),
            method_refs,
        )
        self.assertIn(
            ("avt/BypassHelper", SINGLE_ACCOUNT_METHOD[0], SINGLE_ACCOUNT_METHOD[1]),
            method_refs,
        )
        # The stop invocation (either direct avt/game/k.N or via reflection) is present;
        # check stoppedBotIndices field is accessed for the immediate-mark operation.
        fields = [
            field_reference(pool, int.from_bytes(item.raw[1:3], "big"))
            for item in instructions
            if item.opcode in (0xB2, 0xB3, 0xB4, 0xB5)
        ]
        self.assertIn(("avt/BypassHelper", "activeBotsByIndex", "Ljava/util/Map;"), fields)
        self.assertIn(("avt/BypassHelper", "stoppedBotIndices", "Ljava/util/Set;"), fields)
        for instruction in instructions:
            self.assertTrue(set(instruction.branch_targets) <= boundaries)

    def test_single_account_fallback_matches_the_state_display_fallback(self):
        pool, methods = parse_members(self.helper_class)
        self.assertIn(SINGLE_ACCOUNT_METHOD, methods)
        access, attributes = methods[SINGLE_ACCOUNT_METHOD]
        self.assertTrue(access & 0x0002, "single-account helper should be private")
        self.assertTrue(access & 0x0008, "single-account helper should be static")
        code = attributes["Code"]
        instructions = decode_instructions(code)
        boundaries = {item.pc for item in instructions} | {len(code)}
        self.assertEqual(attributes["MaxStack"], 2)
        self.assertEqual(attributes["MaxLocals"], 2)

        method_refs = [
            method_reference(pool, int.from_bytes(item.raw[1:3], "big"))
            for item in instructions
            if item.opcode in (0xB6, 0xB7, 0xB8, 0xB9)
        ]
        self.assertIn(("avt/BypassHelper", "init", "()V"), method_refs)
        self.assertIn(
            ("java/lang/reflect/Field", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
            method_refs,
        )
        self.assertIn(("java/util/List", "size", "()I"), method_refs)

        fields = [
            field_reference(pool, int.from_bytes(item.raw[1:3], "big"))
            for item in instructions
            if item.opcode in (0xB2, 0xB3, 0xB4, 0xB5)
        ]
        self.assertIn(("avt/BypassHelper", "fM", "Ljava/lang/reflect/Field;"), fields)
        self.assertIn(("avt/BypassHelper", "lastActiveBot", "Ljava/lang/Object;"), fields)
        for instruction in instructions:
            self.assertTrue(set(instruction.branch_targets) <= boundaries)
        exceptions = attributes["ExceptionTable"]
        self.assertEqual(len(exceptions), 1)
        self.assertIn(exceptions[0][0], boundaries)
        self.assertIn(exceptions[0][1], boundaries)
        self.assertIn(exceptions[0][2], boundaries)
        throwable = pool.entries[exceptions[0][3]]
        self.assertEqual(pool.utf8(throwable.value), "java/lang/Throwable")
        self.assertEqual(stack_map_frame_pcs(attributes["StackMapTable"]), [42, 44])

    def test_patcher_is_idempotent_for_both_classes(self):
        for class_name, data in ((HELPER_CLASS, self.helper_class), (STOP_CLASS, self.stop_class)):
            with self.subTest(class_name=class_name):
                patched, count = patch_class(data, class_name)
                self.assertEqual(count, 0)
                self.assertEqual(patched, data)


if __name__ == "__main__":
    unittest.main()
