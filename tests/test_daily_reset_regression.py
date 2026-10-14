"""Regression tests for daily reset of fish count in BypassHelper."""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_JAR = ROOT / "app" / "tool.jar"
HELPER_SRC = ROOT / "scratch" / "BypassHelper.java"


def _find_java() -> str:
    java_exe = Path("C:/Program Files/Java/jdk-22/bin/java.exe")
    if java_exe.exists():
        return str(java_exe)
    return "java"


def _find_javac() -> str:
    javac_exe = Path("C:/Program Files/Java/jdk-22/bin/javac.exe")
    if javac_exe.exists():
        return str(javac_exe)
    return "javac"


class DailyResetRegressionTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(APP_JAR.exists(), f"{APP_JAR} must exist")
        self.assertTrue(HELPER_SRC.exists(), f"{HELPER_SRC} must exist")

    def test_jar_has_daily_reset_symbols(self):
        """Ensure avt/BypassHelper.class in tool.jar contains daily reset methods and fields."""
        with zipfile.ZipFile(APP_JAR, "r") as archive:
            self.assertIn("avt/BypassHelper.class", archive.namelist())
            class_bytes = archive.read("avt/BypassHelper.class")

        self.assertIn(b"checkDailyReset", class_bytes)
        self.assertIn(b"getTodayDate", class_bytes)
        self.assertIn(b"lastStateDate", class_bytes)
        self.assertIn(b"stateDate", class_bytes)
        self.assertIn(b"Asia/Ho_Chi_Minh", class_bytes)

    def test_source_contains_daily_reset_logic(self):
        """Verify BypassHelper.java has proper daily reset implementation."""
        content = HELPER_SRC.read_text(encoding="utf-8")
        self.assertIn("checkDailyReset", content)
        self.assertIn("getTodayDate", content)
        self.assertIn("stateDate", content)
        self.assertIn("Asia/Ho_Chi_Minh", content)
        self.assertIn("public class BypassHelper implements Runnable", content)
        self.assertIn("checkDailyReset()", content)

    def test_source_contains_safe_persistence_logic(self):
        """Verify BypassHelper.java uses REPLACE_EXISTING move to safely overwrite on Windows."""
        content = HELPER_SRC.read_text(encoding="utf-8")
        self.assertIn("REPLACE_EXISTING", content)
        self.assertIn("StandardCopyOption", content)
        self.assertIn(".tmp", content)

    def test_patch_bypass_state_check_passes(self):
        """Verify patch_bypass_state.py --check reports valid daily-reset patch."""
        script = ROOT / "tools" / "patch_bypass_state.py"
        result = subprocess.run(
            [sys.executable, str(script), "--check"],
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("daily-reset", result.stdout.lower())

    def test_runtime_daily_reset_behavior_via_java(self):
        """Run a test in the JVM using app/tool.jar to test checkDailyReset and date transition."""
        java_cmd = _find_java()
        javac_cmd = _find_javac()

        test_code = """
package avt;

import java.util.Map;

public class TestDailyResetRuntime {
    public static void main(String[] args) throws Exception {
        // 1. Verify getTodayDate() is not null and has format YYYY-MM-DD
        String today = BypassHelper.getTodayDate();
        if (today == null || !today.matches("\\\\d{4}-\\\\d{2}-\\\\d{2}")) {
            System.err.println("FAIL: getTodayDate invalid: " + today);
            System.exit(1);
        }

        // 2. Put count into fishCountMap
        Map<Integer, Integer> map = BypassHelper.getFishCountMap();
        map.put(Integer.valueOf(0), Integer.valueOf(42));
        map.put(Integer.valueOf(1), Integer.valueOf(100));

        // 3. Set lastStateDate to today -> checkDailyReset should keep the counts
        BypassHelper.setLastStateDateForTest(today);
        BypassHelper.checkDailyReset();
        if (map.get(Integer.valueOf(0)) == null || map.get(Integer.valueOf(0)).intValue() != 42) {
            System.err.println("FAIL: Same day reset cleared counts unexpectedly!");
            System.exit(2);
        }

        // 4. Simulate a new day by setting lastStateDate to yesterday
        BypassHelper.setLastStateDateForTest("2020-01-01");
        BypassHelper.checkDailyReset();

        // 5. Verify fishCountMap was reset
        Integer c0 = map.get(Integer.valueOf(0));
        if (c0 != null && c0.intValue() != 0) {
            System.err.println("FAIL: New day did not reset fish count: " + c0);
            System.exit(3);
        }

        // 6. Verify lastStateDate was updated to today
        if (!today.equals(BypassHelper.getLastStateDate())) {
            System.err.println("FAIL: lastStateDate was not updated to today: " + BypassHelper.getLastStateDate());
            System.exit(4);
        }

        System.out.println("OK: Runtime daily reset verified successfully");
    }
}
"""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            src_file = tmp_path / "TestDailyResetRuntime.java"
            src_file.write_text(test_code, encoding="utf-8")

            # Compile against tool.jar
            compile_res = subprocess.run(
                [javac_cmd, "--release", "8", "-cp", str(APP_JAR), "-d", str(tmp_path), str(src_file)],
                capture_output=True,
                text=True,
            )
            self.assertEqual(compile_res.returncode, 0, f"Compilation failed:\n{compile_res.stderr}")

            # Run test
            run_res = subprocess.run(
                [java_cmd, "-cp", f"{APP_JAR};{tmp_path}", "avt.TestDailyResetRuntime"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(run_res.returncode, 0, f"Execution failed:\n{run_res.stderr}\n{run_res.stdout}")
            self.assertIn("OK: Runtime daily reset verified successfully", run_res.stdout)


if __name__ == "__main__":
    unittest.main()
