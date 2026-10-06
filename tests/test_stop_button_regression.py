import sys
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

from patch_stop_flag import (  # noqa: E402
    ConstantPool,
    GUARDED_METHODS,
    Reader,
    STOP_HELPER,
    TARGET_METHODS,
    decode_instructions,
    patch_class,
)

JAR_PATH = ROOT / "app" / "tool.jar"
LEGACY_JAR_PATH = ROOT / "scratch" / "tool_patched.jar"
SOURCE_PATH = ROOT / "scratch" / "BypassHelper.java"
UI_SOURCE_PATH = ROOT / "demo_source" / "DebugToDeath.java"


def parse_members(class_bytes):
    reader = Reader(class_bytes)
    reader.take(8)
    pool = ConstantPool.parse(class_bytes, reader)
    reader.take(6)  # access_flags, this_class, super_class
    reader.take(reader.u2() * 2)

    fields = []
    for _ in range(reader.u2()):
        access = reader.u2()
        name = pool.utf8(reader.u2())
        descriptor = pool.utf8(reader.u2())
        attribute_count = reader.u2()
        for _ in range(attribute_count):
            reader.take(2)
            reader.take(reader.u4())
        fields.append((access, name, descriptor))

    methods = {}
    for _ in range(reader.u2()):
        access = reader.u2()
        name = pool.utf8(reader.u2())
        descriptor = pool.utf8(reader.u2())
        attributes = {}
        for _ in range(reader.u2()):
            attribute_name = pool.utf8(reader.u2())
            attribute_info = reader.take(reader.u4())
            if attribute_name == "Code":
                code_reader = Reader(attribute_info)
                attributes["MaxStack"] = code_reader.u2()
                code_reader.take(2)  # max_locals
                attributes[attribute_name] = code_reader.take(code_reader.u4())
                exceptions = []
                for _ in range(code_reader.u2()):
                    exceptions.append((code_reader.u2(), code_reader.u2(),
                                      code_reader.u2(), code_reader.u2()))
                attributes["ExceptionTable"] = exceptions
                for _ in range(code_reader.u2()):
                    nested_name = pool.utf8(code_reader.u2())
                    nested_info = code_reader.take(code_reader.u4())
                    if nested_name == "StackMapTable":
                        attributes[nested_name] = nested_info
        methods[(name, descriptor)] = (access, attributes)
    return pool, fields, methods


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


def bytecode_field_refs(code, pool):
    refs = []
    for instruction in decode_instructions(code):
        if instruction.opcode in (0xB2, 0xB3, 0xB4, 0xB5):
            cp_index = int.from_bytes(instruction.raw[1:3], "big")
            ref = field_reference(pool, cp_index)
            if ref is not None:
                refs.append((instruction.opcode, ref))
    return refs


def string_loads(code, pool):
    values = []
    for instruction in decode_instructions(code):
        if instruction.opcode == 0x12:
            cp_index = instruction.raw[1]
        elif instruction.opcode == 0x13:
            cp_index = int.from_bytes(instruction.raw[1:3], "big")
        else:
            continue
        value = pool.string(cp_index)
        if value is not None:
            values.append(value)
    return values


def stack_map_frame_pcs(info):
    reader = Reader(info)
    frame_count = reader.u2()
    previous_pc = -1
    pcs = []

    def skip_verification_type():
        tag = reader.u1()
        if tag in (7, 8):
            reader.take(2)
        elif tag not in range(0, 7):
            raise AssertionError(f"invalid verification_type_info tag {tag}")

    for _ in range(frame_count):
        frame_type = reader.u1()
        if frame_type <= 63:
            offset_delta = frame_type
        elif frame_type <= 127:
            offset_delta = frame_type - 64
            skip_verification_type()
        elif frame_type == 247:
            offset_delta = reader.u2()
            skip_verification_type()
        elif 248 <= frame_type <= 251:
            offset_delta = reader.u2()
        elif 252 <= frame_type <= 254:
            offset_delta = reader.u2()
            for _ in range(frame_type - 251):
                skip_verification_type()
        elif frame_type == 255:
            offset_delta = reader.u2()
            for _ in range(reader.u2()):
                skip_verification_type()
            for _ in range(reader.u2()):
                skip_verification_type()
        else:
            raise AssertionError(f"reserved stack-map frame type {frame_type}")
        previous_pc += offset_delta + 1
        pcs.append(previous_pc)
    if reader.pos != len(info):
        raise AssertionError("unexpected trailing StackMapTable data")
    return pcs


def make_stop_references_stale(data, class_name):
    reader = Reader(data)
    reader.take(8)
    pool = ConstantPool.parse(data, reader)
    body_start = reader.pos
    old_index = pool.add_string("w")
    if old_index > 0xFF:
        raise AssertionError("test fixture needs a low-index `w` String constant")

    body_reader = Reader(data[body_start:])
    prefix_start = body_reader.pos
    body_reader.take(6)
    body_reader.take(body_reader.u2() * 2)
    for _ in range(body_reader.u2()):
        body_reader.take(6)
        for _ in range(body_reader.u2()):
            body_reader.take(2)
            body_reader.take(body_reader.u4())
    fields_end = body_reader.pos
    output = bytearray(body_reader.data[prefix_start:fields_end])
    method_count = body_reader.u2()
    output.extend(method_count.to_bytes(2, "big"))
    targets = TARGET_METHODS[class_name]
    changed = 0

    for _ in range(method_count):
        access, name_index, descriptor_index = body_reader.u2(), body_reader.u2(), body_reader.u2()
        method_key = (pool.utf8(name_index), pool.utf8(descriptor_index))
        output.extend(access.to_bytes(2, "big"))
        output.extend(name_index.to_bytes(2, "big"))
        output.extend(descriptor_index.to_bytes(2, "big"))
        attribute_count = body_reader.u2()
        output.extend(attribute_count.to_bytes(2, "big"))
        for _ in range(attribute_count):
            attribute_name = body_reader.u2()
            attribute_info = body_reader.take(body_reader.u4())
            if method_key in targets and pool.utf8(attribute_name) == "Code":
                code_reader = Reader(attribute_info)
                code_reader.take(4)
                code_length = code_reader.u4()
                code = bytearray(code_reader.take(code_length))
                for instruction in decode_instructions(code):
                    if instruction.opcode == 0x12:
                        cp_index = instruction.raw[1]
                    elif instruction.opcode == 0x13:
                        cp_index = int.from_bytes(instruction.raw[1:3], "big")
                    else:
                        continue
                    if pool.string(cp_index) != "hl":
                        continue
                    if instruction.opcode == 0x12:
                        code[instruction.pc + 1] = old_index
                    else:
                        code[instruction.pc + 1 : instruction.pc + 3] = old_index.to_bytes(2, "big")
                    changed += 1
                attribute_info = (attribute_info[:8] + bytes(code)
                                  + attribute_info[8 + code_length :])
            output.extend(attribute_name.to_bytes(2, "big"))
            output.extend(len(attribute_info).to_bytes(4, "big"))
            output.extend(attribute_info)

    output.extend(body_reader.take(len(body_reader.data) - body_reader.pos))
    if changed != len(targets):
        raise AssertionError(f"expected to stale {len(targets)} references, changed {changed}")
    return data[:8] + pool.to_bytes() + bytes(output)


def row_handler_source(source, handler_number):
    marker = f"private void lambda$accountRowNode${handler_number}("
    start = source.index(marker)
    end = source.index("\n    }", start) + len("\n    }")
    return source[start:end]


class StopButtonRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with zipfile.ZipFile(JAR_PATH) as jar:
            cls.classes = {
                name: jar.read(name)
                for name in (
                    "avt/BypassHelper.class",
                    "avt/BypassHelper$5.class",  # watchdog Runnable (was $4 before shutdown hook was added)
                    "avt/game/k.class",
                    "avt/Q.class",
                )
            }
        cls.helper_source = SOURCE_PATH.read_text(encoding="utf-8")
        cls.ui_source = UI_SOURCE_PATH.read_text(encoding="utf-8")

    def test_settings_run_stop_and_delete_buttons_are_bound(self):
        expected_bindings = (
            "button6.setOnAction(arg_0 -> this.lambda$accountRowNode$5",
            "button5.setOnAction(arg_0 -> this.lambda$accountRowNode$6",
            "button4.setOnAction(arg_0 -> this.lambda$accountRowNode$7",
            "button3.setOnAction(arg_0 -> this.lambda$accountRowNode$8",
        )
        for binding in expected_bindings:
            with self.subTest(binding=binding):
                self.assertIn(binding, self.ui_source)

        # The row actions dispatch in order to Settings, Run, Stop, and Delete.
        for handler, action in ((5, "Y"), (6, "a"), (7, "n"), (8, "U")):
            with self.subTest(handler=handler):
                self.assertIn(f"this.{action}(new Object[]{{n2.q}})",
                              row_handler_source(self.ui_source, handler))

    def test_stop_handler_sets_the_same_flag_checked_by_runtime_helper(self):
        game_pool, game_fields, game_methods = parse_members(self.classes["avt/game/k.class"])
        self.assertIn((65, "hl", "Z"), game_fields)

        q_pool, _, q_methods = parse_members(self.classes["avt/Q.class"])
        stop_code = q_methods[("H", "([Ljava/lang/Object;)V")][1]["Code"]
        stop_writes = {
            ref
            for opcode, ref in bytecode_field_refs(stop_code, q_pool)
            if opcode == 0xB5
        }
        self.assertIn(("avt/game/k", "hl", "Z"), stop_writes)

        # The game bot's stop routine marks logging stopped and its log method
        # checks that marker before appending a line to the UI.
        stop_routine = game_methods[("N", "([Ljava/lang/Object;)V")][1]["Code"]
        stop_refs = bytecode_field_refs(stop_routine, game_pool)
        self.assertIn((0xB4, ("avt/game/k", "hl", "Z")), stop_refs)
        self.assertIn((0xB5, ("avt/game/k", "ho", "Z")), stop_refs)
        log_method = game_methods[("o", "([Ljava/lang/Object;)V")][1]["Code"]
        log_refs = bytecode_field_refs(log_method, game_pool)
        self.assertIn((0xB4, ("avt/game/k", "ho", "Z")), log_refs)

        # The bundle now checks `hl` in the watchdog, disconnect path, and UI state.
        for class_name, method_keys in TARGET_METHODS.items():
            pool, _, methods = parse_members(self.classes[class_name])
            for method_key in method_keys:
                code = methods[method_key][1]["Code"]
                with self.subTest(class_name=class_name, method=method_key):
                    self.assertIn("hl", string_loads(code, pool))
                    self.assertNotIn("w", string_loads(code, pool))

    def test_selected_account_field_lookup_is_not_changed(self):
        pool, _, methods = parse_members(self.classes["avt/BypassHelper.class"])
        init_code = methods[("init", "()V")][1]["Code"]
        self.assertIn("w", string_loads(init_code, pool))

    def test_source_and_bundled_class_patch_are_idempotent(self):
        self.assertGreaterEqual(
            self.helper_source.count('getDeclaredField("hl")'),
            3,
            "watchdog, disconnect handling, and displayed running state must all use `hl`",
        )
        self.assertIn("private static boolean isBotStopped(Object bot)", self.helper_source)
        self.assertIn("if (bot == null || isBotStopped(bot)) return;", self.helper_source)
        for class_name, class_bytes in self.classes.items():
            if class_name not in TARGET_METHODS and class_name not in GUARDED_METHODS:
                continue
            with self.subTest(class_name=class_name):
                patched, count = patch_class(class_bytes, class_name)
                self.assertEqual(count, 0)
                self.assertEqual(patched, class_bytes)

    def test_pending_casts_and_late_logs_are_guarded_after_stop(self):
        class_name = "avt/BypassHelper.class"
        pool, _, methods = parse_members(self.classes[class_name])
        helper_ref = pool.find_methodref("avt/BypassHelper", *STOP_HELPER)
        self.assertIsNotNone(helper_ref)
        expected_prefix = b"\x2a\xb8" + int(helper_ref).to_bytes(2, "big") + b"\x99\x00\x04\xb1"

        for method_key in GUARDED_METHODS[class_name]:
            with self.subTest(method=method_key):
                code_attributes = methods[method_key][1]
                code = code_attributes["Code"]
                self.assertTrue(code.startswith(expected_prefix))
                instructions = decode_instructions(code)
                boundaries = {item.pc for item in instructions} | {len(code)}
                for instruction in instructions:
                    self.assertTrue(set(instruction.branch_targets) <= boundaries)
                for start_pc, end_pc, handler_pc, _ in code_attributes["ExceptionTable"]:
                    self.assertIn(start_pc, boundaries)
                    self.assertIn(end_pc, boundaries)
                    self.assertIn(handler_pc, boundaries)
                frame_pcs = stack_map_frame_pcs(code_attributes["StackMapTable"])
                self.assertTrue(set(frame_pcs) <= boundaries)
                self.assertIn(8, frame_pcs)

        access, helper_attributes = methods[STOP_HELPER]
        helper_code = helper_attributes["Code"]
        self.assertTrue(access & 0x0008, "the stop-state helper must be static")
        self.assertIn("hl", string_loads(helper_code, pool))
        # MaxStack is 2 from javac, or 3 from the bytecode-synthesized helper in patch_stop_flag.
        self.assertGreaterEqual(helper_attributes["MaxStack"], 2)
        exceptions = helper_attributes["ExceptionTable"]
        self.assertEqual(len(exceptions), 1)
        catch_type = exceptions[0][3]
        throwable = pool.entries[catch_type]
        self.assertEqual(pool.utf8(throwable.value), "java/lang/Throwable")
        # StackMapTable format differs between synthesized and javac-compiled; only check present + non-empty.
        self.assertIn("StackMapTable", helper_attributes)
        self.assertGreater(len(helper_attributes["StackMapTable"]), 2)

    def test_patcher_corrects_stale_field_loads_without_touching_other_methods(self):
        # BypassHelper$5 is the watchdog Runnable compiled from Java source.
        # patch_stop_flag only patches classes whose stop-field references use the
        # "w" sentinel string. The javac-compiled watchdog never used "w", so we
        # skip it here.
        SKIP_FOR_STALE = {"avt/BypassHelper$5.class"}
        for class_name in TARGET_METHODS:
            if class_name in SKIP_FOR_STALE:
                continue
            with self.subTest(class_name=class_name):
                stale = make_stop_references_stale(self.classes[class_name], class_name)
                corrected, patched_count = patch_class(stale, class_name)
                self.assertGreaterEqual(patched_count, len(TARGET_METHODS[class_name]))
                corrected_pool, _, corrected_methods = parse_members(corrected)
                for method_key in TARGET_METHODS[class_name]:
                    code = corrected_methods[method_key][1]["Code"]
                    self.assertIn("hl", string_loads(code, corrected_pool))
                    self.assertNotIn("w", string_loads(code, corrected_pool))

    def test_patcher_upgrades_legacy_helper_bundle_and_is_idempotent(self):
        # The legacy JAR was built before the shutdown-hook was added to BypassHelper,
        # so it has $4 (watchdog) instead of $5. Skip class names that don't exist in
        # the legacy bundle.
        with zipfile.ZipFile(LEGACY_JAR_PATH) as jar:
            legacy_names = set(jar.namelist())
            for class_name in TARGET_METHODS:
                if class_name not in legacy_names:
                    continue  # e.g. avt/BypassHelper$5.class absent in legacy
                with self.subTest(class_name=class_name):
                    legacy = jar.read(class_name)
                    patched, count = patch_class(legacy, class_name)
                    self.assertGreater(count, 0)
                    repeated, repeated_count = patch_class(patched, class_name)
                    self.assertEqual(repeated_count, 0)
                    self.assertEqual(repeated, patched)
                    pool, _, methods = parse_members(patched)
                    for method_key in TARGET_METHODS[class_name]:
                        code = methods[method_key][1]["Code"]
                        self.assertIn("hl", string_loads(code, pool))
                        self.assertNotIn("w", string_loads(code, pool))
                    if class_name in GUARDED_METHODS:
                        helper_ref = pool.find_methodref("avt/BypassHelper", *STOP_HELPER)
                        prefix = b"\x2a\xb8" + int(helper_ref).to_bytes(2, "big") + b"\x99\x00\x04\xb1"
                        for method_key in GUARDED_METHODS[class_name]:
                            self.assertTrue(methods[method_key][1]["Code"].startswith(prefix))
                        self.assertIn(STOP_HELPER, methods)


if __name__ == "__main__":
    unittest.main()
