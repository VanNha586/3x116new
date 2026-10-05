import sys
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tests"))

from patch_log_selection import (  # noqa: E402
    ACCOUNT_METHOD,
    CONTROLLER,
    RENDER_METHOD,
    ROW_INSERTIONS,
    patch_class as patch_log_class,
)
from patch_stop_flag import ConstantPool, Reader, decode_instructions  # noqa: E402
from test_stop_button_regression import parse_members, stack_map_frame_pcs  # noqa: E402

JAR_PATH = ROOT / "app" / "tool.jar"
UI_SOURCE_PATH = ROOT / "demo_source" / "DebugToDeath.java"


def invoked_methods(code, pool):
    result = []
    for instruction in decode_instructions(code):
        if instruction.opcode not in (0xB6, 0xB7, 0xB8, 0xB9):
            continue
        cp_index = int.from_bytes(instruction.raw[1:3], "big")
        entry = pool.entries[cp_index]
        if entry is None or entry.tag not in (10, 11):
            continue
        class_index, name_and_type_index = entry.value
        class_entry = pool.entries[class_index]
        name_and_type = pool.entries[name_and_type_index]
        if class_entry is None or name_and_type is None:
            continue
        owner = pool.utf8(class_entry.value)
        name = pool.utf8(name_and_type.value[0])
        descriptor = pool.utf8(name_and_type.value[1])
        result.append((instruction.pc, instruction.opcode, owner, name, descriptor))
    return result


def assert_code_offsets_valid(test_case, code, exception_table):
    instructions = decode_instructions(code)
    boundaries = {instruction.pc for instruction in instructions} | {len(code)}
    targets = {target for instruction in instructions for target in instruction.branch_targets}
    test_case.assertLessEqual(targets, boundaries)
    for start_pc, end_pc, handler_pc, _ in exception_table:
        test_case.assertIn(start_pc, boundaries)
        test_case.assertIn(end_pc, boundaries)
        test_case.assertIn(handler_pc, boundaries)


class AccountLogSelectionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with zipfile.ZipFile(JAR_PATH) as archive:
            cls.controller_class = archive.read(CONTROLLER)
            cls.jar_names = archive.namelist()
        cls.source = UI_SOURCE_PATH.read_text(encoding="utf-8")
        cls.pool, _, cls.methods = parse_members(cls.controller_class)

    def test_row_clicks_pin_the_account_log_index(self):
        for method_key in ROW_INSERTIONS:
            with self.subTest(method=method_key):
                code = self.methods[method_key][1]["Code"]
                refs = invoked_methods(code, self.pool)
                self.assertTrue(
                    any(owner == "java/util/Map" and name == "put"
                        and descriptor == "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
                        for _, _, owner, name, descriptor in refs),
                    "row click must save its account index under the reserved log-view key",
                )

        self.assertIn("this.M.put(Integer.valueOf(-1), Integer.valueOf(n2));", self.source)
        self.assertIn("this.M.put(Integer.valueOf(-1), Integer.valueOf(n2.q));", self.source)

    def test_periodic_refresh_uses_pinned_index_for_log_and_fallback_account(self):
        for method_key in (RENDER_METHOD, ACCOUNT_METHOD):
            with self.subTest(method=method_key):
                code = self.methods[method_key][1]["Code"]
                refs = invoked_methods(code, self.pool)
                self.assertTrue(
                    any(owner == "java/util/Map" and name == "getOrDefault"
                        and descriptor == "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
                        for _, _, owner, name, descriptor in refs),
                    "renderer must retain the manually selected log row across refreshes",
                )
                attributes = self.methods[method_key][1]
                assert_code_offsets_valid(self, code, attributes["ExceptionTable"])
                boundaries = {instruction.pc for instruction in decode_instructions(code)} | {len(code)}
                if "StackMapTable" in attributes:
                    self.assertLessEqual(set(stack_map_frame_pcs(attributes["StackMapTable"])), boundaries)

        self.assertGreaterEqual(self.source.count('this.M.get(Integer.valueOf(-1))'), 2)

    def test_patched_controller_is_idempotent_and_jar_is_valid(self):
        patched, count = patch_log_class(self.controller_class)
        self.assertEqual(count, 0)
        self.assertEqual(patched, self.controller_class)
        with zipfile.ZipFile(JAR_PATH) as archive:
            self.assertIsNone(archive.testzip())
            self.assertIn(CONTROLLER, self.jar_names)


if __name__ == "__main__":
    unittest.main()
