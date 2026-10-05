import sys
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

from patch_stop_flag import (  # noqa: E402
    ConstantPool,
    CpEntry,
    Reader,
    TARGET_METHODS,
    decode_instructions,
    patch_class,
)

JAR_PATH = ROOT / "app" / "tool.jar"
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
                code_reader.take(4)  # max_stack, max_locals
                attributes[attribute_name] = code_reader.take(code_reader.u4())
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


def replace_string_constant(data, old_value, new_value):
    reader = Reader(data)
    reader.take(8)
    pool = ConstantPool.parse(data, reader)
    matches = [
        entry.value
        for index, entry in enumerate(pool.entries)
        if entry is not None and entry.tag == 8 and pool.string(index) == old_value
    ]
    if len(matches) != 1:
        raise AssertionError(f"expected one String constant {old_value!r}, found {len(matches)}")
    utf8_index = int(matches[0])
    encoded = new_value.encode("utf-8")
    pool.entries[utf8_index] = CpEntry(
        1, b"\x01" + len(encoded).to_bytes(2, "big") + encoded, new_value
    )
    return data[:8] + pool.to_bytes() + data[reader.pos :]


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
                    "avt/BypassHelper$4.class",
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
        _, game_fields, _ = parse_members(self.classes["avt/game/k.class"])
        self.assertIn((65, "hl", "Z"), game_fields)

        q_pool, _, q_methods = parse_members(self.classes["avt/Q.class"])
        stop_code = q_methods[("H", "([Ljava/lang/Object;)V")][1]["Code"]
        stop_writes = {
            ref
            for opcode, ref in bytecode_field_refs(stop_code, q_pool)
            if opcode == 0xB5
        }
        self.assertIn(("avt/game/k", "hl", "Z"), stop_writes)

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
        for class_name, class_bytes in self.classes.items():
            if class_name not in TARGET_METHODS:
                continue
            with self.subTest(class_name=class_name):
                patched, count = patch_class(class_bytes, class_name)
                self.assertEqual(count, 0)
                self.assertEqual(patched, class_bytes)

    def test_patcher_corrects_stale_field_loads_without_touching_other_methods(self):
        for class_name in TARGET_METHODS:
            with self.subTest(class_name=class_name):
                stale = replace_string_constant(self.classes[class_name], "hl", "w")
                corrected, patched_count = patch_class(stale, class_name)
                expected_count = len(TARGET_METHODS[class_name])
                self.assertEqual(patched_count, expected_count)
                corrected_pool, _, corrected_methods = parse_members(corrected)
                for method_key in TARGET_METHODS[class_name]:
                    code = corrected_methods[method_key][1]["Code"]
                    self.assertIn("hl", string_loads(code, corrected_pool))
                    self.assertNotIn("w", string_loads(code, corrected_pool))


if __name__ == "__main__":
    unittest.main()
