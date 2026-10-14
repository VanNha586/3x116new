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

    def test_multi_account_persistence_and_restore(self):
        """Verify that fish counts and mission info are stored and restored per account."""
        java_cmd = _find_java()
        javac_cmd = _find_javac()

        test_code = """
package avt;

import java.io.File;
import java.util.Map;

public class TestMultiAccountPersistence {
    public static void main(String[] args) throws Exception {
        // Clear maps
        Map<Integer, Integer> counts = BypassHelper.getFishCountMap();
        Map<Integer, String> missions = BypassHelper.getMissionDetailMap();
        counts.clear();
        missions.clear();

        // Account 0
        counts.put(Integer.valueOf(0), Integer.valueOf(15));
        missions.put(Integer.valueOf(0), "Nhiệm Vụ Thợ Câu - 15 con cá rô");

        // Account 1
        counts.put(Integer.valueOf(1), Integer.valueOf(88));
        missions.put(Integer.valueOf(1), "Nhiệm Vụ Thợ Câu - 88 con cá lóc");

        // Save state
        BypassHelper.persistStatePublic();

        // Clear maps to simulate restarting tool
        counts.clear();
        missions.clear();

        // Restore state
        BypassHelper.restoreStatePublic();

        // Check Account 0
        Integer c0 = counts.get(Integer.valueOf(0));
        String m0 = missions.get(Integer.valueOf(0));
        if (c0 == null || c0.intValue() != 15) {
            System.err.println("FAIL: Account 0 fish count mismatch: " + c0);
            System.exit(1);
        }
        if (m0 == null || !m0.contains("15 con cá rô")) {
            System.err.println("FAIL: Account 0 mission mismatch: " + m0);
            System.exit(2);
        }

        // Check Account 1
        Integer c1 = counts.get(Integer.valueOf(1));
        String m1 = missions.get(Integer.valueOf(1));
        if (c1 == null || c1.intValue() != 88) {
            System.err.println("FAIL: Account 1 fish count mismatch: " + c1);
            System.exit(3);
        }
        if (m1 == null || !m1.contains("88 con cá lóc")) {
            System.err.println("FAIL: Account 1 mission mismatch: " + m1);
            System.exit(4);
        }

        System.out.println("OK: Multi-account persistence verified successfully");
    }
}
"""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            src_file = tmp_path / "TestMultiAccountPersistence.java"
            src_file.write_text(test_code, encoding="utf-8")

            compile_res = subprocess.run(
                [javac_cmd, "--release", "8", "-cp", str(APP_JAR), "-d", str(tmp_path), str(src_file)],
                capture_output=True,
                text=True,
            )
            self.assertEqual(compile_res.returncode, 0, f"Compilation failed:\n{compile_res.stderr}")

            run_res = subprocess.run(
                [java_cmd, "-cp", f"{APP_JAR};{tmp_path}", "avt.TestMultiAccountPersistence"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(run_res.returncode, 0, f"Execution failed:\n{run_res.stderr}\n{run_res.stdout}")
            self.assertIn("OK: Multi-account persistence verified successfully", run_res.stdout)

    def test_fish_debounce_prevents_double_count(self):
        """Verify that rapid successive log lines (within 3s) for the same account do not double count."""
        java_cmd = _find_java()
        javac_cmd = _find_javac()

        test_code = """
package avt;

import java.util.Map;

public class TestDebounceDoubleCount {
    // Dummy bot class with hh field for account index and hl field for stopping flag
    public static class FakeBot {
        public int hh;
        public boolean hl = false;
        public FakeBot(int idx) {
            this.hh = idx;
        }
    }

    public static void main(String[] args) throws Exception {
        Map<Integer, Integer> counts = BypassHelper.getFishCountMap();
        counts.clear();

        FakeBot bot0 = new FakeBot(0);
        FakeBot bot1 = new FakeBot(1);

        // Fire catch log for bot 0
        BypassHelper.onBotLog(bot0, new Object[]{ "Câu thành công con Cá lóc - Trị giá 100 xu" });

        // Rapid duplicate log for bot 0 (should be debounced!)
        BypassHelper.onBotLog(bot0, new Object[]{ "Đã câu được một con cá lóc" });

        // Bot 0 count should be 1, NOT 2
        Integer c0 = counts.get(Integer.valueOf(0));
        if (c0 == null || c0.intValue() != 1) {
            System.err.println("FAIL: Bot 0 was double counted: " + c0);
            System.exit(1);
        }

        // Fire catch log for bot 1 (different account, should not be blocked by bot 0)
        BypassHelper.onBotLog(bot1, new Object[]{ "Câu thành công con Cá rô" });
        Integer c1 = counts.get(Integer.valueOf(1));
        if (c1 == null || c1.intValue() != 1) {
            System.err.println("FAIL: Bot 1 was not counted: " + c1);
            System.exit(2);
        }

        System.out.println("OK: Debounce prevented double count successfully");
    }
}
"""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            src_file = tmp_path / "TestDebounceDoubleCount.java"
            src_file.write_text(test_code, encoding="utf-8")

            compile_res = subprocess.run(
                [javac_cmd, "--release", "8", "-cp", str(APP_JAR), "-d", str(tmp_path), str(src_file)],
                capture_output=True,
                text=True,
            )
            self.assertEqual(compile_res.returncode, 0, f"Compilation failed:\n{compile_res.stderr}")

            run_res = subprocess.run(
                [java_cmd, "-cp", f"{APP_JAR};{tmp_path}", "avt.TestDebounceDoubleCount"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(run_res.returncode, 0, f"Execution failed:\n{run_res.stderr}\n{run_res.stdout}")
            self.assertIn("OK: Debounce prevented double count successfully", run_res.stdout)


if __name__ == "__main__":
    unittest.main()
