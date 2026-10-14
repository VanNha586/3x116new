"""Regression tests for account credential persistence in BypassHelper."""
from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_JAR = ROOT / "app" / "tool.jar"
HELPER_SRC = ROOT / "scratch" / "BypassHelper.java"
JRE_JAVA = ROOT / "jre" / "bin" / "java.exe"


def _find_java() -> str:
    if JRE_JAVA.exists():
        return str(JRE_JAVA)
    java_exe = Path("C:/Program Files/Java/jdk-22/bin/java.exe")
    if java_exe.exists():
        return str(java_exe)
    return "java"


def _find_javac() -> str:
    javac_exe = Path("C:/Program Files/Java/jdk-22/bin/javac.exe")
    if javac_exe.exists():
        return str(javac_exe)
    return "javac"


class AccountPersistenceRegressionTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(APP_JAR.exists(), f"{APP_JAR} must exist")
        self.assertTrue(HELPER_SRC.exists(), f"{HELPER_SRC} must exist")

    def test_jar_has_account_persistence_symbols(self):
        """Ensure avt/BypassHelper.class in tool.jar contains account persistence methods."""
        with zipfile.ZipFile(APP_JAR, "r") as archive:
            self.assertIn("avt/BypassHelper.class", archive.namelist())
            class_bytes = archive.read("avt/BypassHelper.class")

        self.assertIn(b"saveClientAccounts", class_bytes)
        self.assertIn(b"syncAccountFilesOnStartup", class_bytes)
        self.assertIn(b"hasAccountCredentials", class_bytes)
        self.assertIn(b"client-accounts.json", class_bytes)

    def test_source_contains_user_role_and_persistence_logic(self):
        """Verify BypassHelper.java sets role to user and hooks account saving."""
        content = HELPER_SRC.read_text(encoding="utf-8")
        self.assertIn('fW.set(null, "user")', content)
        self.assertIn("saveClientAccounts()", content)
        self.assertIn("syncAccountFilesOnStartup()", content)
        self.assertIn("AccountSaverShutdownHook", content)

    def test_java_account_persistence_cycle(self):
        """Run Java persistence cycle test to ensure credentials save to disk and reload cleanly."""
        java_cmd = _find_java()
        javac_cmd = _find_javac()

        test_code = """
package avt;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class TestPersistenceCycleRuntime {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        Class<?> helperClass = Class.forName("avt.BypassHelper");

        // 1. Initialize
        Method mApply = helperClass.getDeclaredMethod("apply");
        mApply.invoke(null);

        // Verify role W is "user"
        Field fW = qClass.getDeclaredField("W");
        fW.setAccessible(true);
        String role = (String) fW.get(null);
        if (!"user".equals(role)) {
            throw new RuntimeException("Expected role 'user', got: " + role);
        }
        System.out.println("Role check passed: W = " + role);

        // 2. Load accounts via v5
        Method mv5 = qClass.getDeclaredMethod("v5", Object[].class);
        mv5.setAccessible(true);
        mv5.invoke(null, new Object[]{ new Object[0] });

        // 3. Set test username & password on account 0
        Field fm = qClass.getDeclaredField("m");
        fm.setAccessible(true);
        List accounts = (List) fm.get(null);
        if (accounts.isEmpty()) {
            throw new RuntimeException("accounts list is empty after v5");
        }
        Object acc0 = accounts.get(0);
        Field fn = acc0.getClass().getDeclaredField("N"); fn.setAccessible(true);
        Field fp = acc0.getClass().getDeclaredField("p"); fp.setAccessible(true);
        fn.set(acc0, "test_persistent_user");
        fp.set(acc0, "test_persistent_pass");

        // 4. Save accounts
        Method mSave = helperClass.getDeclaredMethod("saveClientAccounts");
        mSave.invoke(null);

        // 5. Verify both files on disk have the credentials
        String[] paths = new String[]{ "data/client-accounts.json", "app/data/client-accounts.json" };
        for (String p : paths) {
            File f = new File(p);
            if (!f.exists()) throw new RuntimeException("File not found: " + p);
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (!content.contains("test_persistent_user")) {
                throw new RuntimeException("File " + p + " does not contain test_persistent_user");
            }
            System.out.println("Verified file " + p + " contains test_persistent_user");
        }

        // 6. Clear m in memory to simulate fresh restart
        accounts.clear();

        // 7. Re-run v5 to simulate tool reopening
        mv5.invoke(null, new Object[]{ new Object[0] });
        accounts = (List) fm.get(null);
        if (accounts.isEmpty()) throw new RuntimeException("Fresh v5 returned empty accounts!");
        Object reloadedAcc = accounts.get(0);
        String reloadedUser = (String) fn.get(reloadedAcc);
        String reloadedPass = (String) fp.get(reloadedAcc);

        if (!"test_persistent_user".equals(reloadedUser)) {
            throw new RuntimeException("Expected reloaded user 'test_persistent_user', got: " + reloadedUser);
        }
        if (!"test_persistent_pass".equals(reloadedPass)) {
            throw new RuntimeException("Expected reloaded pass 'test_persistent_pass', got: " + reloadedPass);
        }
        System.out.println("SUCCESS! Reloaded username: " + reloadedUser + ", password: " + reloadedPass);

        // 8. Test getStateJson
        Method mState = helperClass.getDeclaredMethod("getStateJson", Object[].class);
        String stateJson = (String) mState.invoke(null, new Object[]{ new Object[0] });
        if (!stateJson.contains("test_persistent_user")) {
            throw new RuntimeException("getStateJson does not contain test_persistent_user");
        }
        System.out.println("SUCCESS! getStateJson contains username: true");
    }
}
"""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            src_file = tmp_path / "TestPersistenceCycleRuntime.java"
            src_file.write_text(test_code, encoding="utf-8")

            compile_res = subprocess.run(
                [javac_cmd, "--release", "8", "-cp", f"{APP_JAR};{ROOT}/jre/lib/*;{ROOT}/jre/lib/ext/*", "-d", str(tmp_path), str(src_file)],
                capture_output=True,
                text=True,
                cwd=str(ROOT),
            )
            self.assertEqual(compile_res.returncode, 0, f"Compile failed:\n{compile_res.stderr}")

            run_res = subprocess.run(
                [java_cmd, "-cp", f"{APP_JAR};{tmp_path}", "avt.TestPersistenceCycleRuntime"],
                capture_output=True,
                text=True,
                cwd=str(ROOT),
                timeout=30,
            )
            self.assertEqual(run_res.returncode, 0, f"Execution failed:\n{run_res.stdout}\n{run_res.stderr}")
            self.assertIn("Role check passed: W = user", run_res.stdout)
            self.assertIn("SUCCESS! Reloaded username: test_persistent_user", run_res.stdout)
            self.assertIn("SUCCESS! getStateJson contains username: true", run_res.stdout)

        # Restore clean template
        bak_file = ROOT / "data" / "client-accounts.json.bak"
        if bak_file.exists():
            clean_bytes = bak_file.read_bytes()
            (ROOT / "data" / "client-accounts.json").write_bytes(clean_bytes)
            (ROOT / "app" / "data" / "client-accounts.json").write_bytes(clean_bytes)

    def test_java_account_persistence_from_app_cwd(self):
        """Simulate running tool directly from app directory (like Chay-Tool.bat)."""
        java_cmd = _find_java()
        javac_cmd = _find_javac()

        test_code = """
package avt;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class TestPersistenceCycleFromApp {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        Class<?> helperClass = Class.forName("avt.BypassHelper");

        Method mApply = helperClass.getDeclaredMethod("apply");
        mApply.invoke(null);

        Method mv5 = qClass.getDeclaredMethod("v5", Object[].class);
        mv5.setAccessible(true);
        mv5.invoke(null, new Object[]{ new Object[0] });

        Field fm = qClass.getDeclaredField("m");
        fm.setAccessible(true);
        List accounts = (List) fm.get(null);
        if (accounts.isEmpty()) {
            throw new RuntimeException("accounts list is empty after v5");
        }
        Object acc0 = accounts.get(0);
        Field fn = acc0.getClass().getDeclaredField("N"); fn.setAccessible(true);
        Field fp = acc0.getClass().getDeclaredField("p"); fp.setAccessible(true);
        fn.set(acc0, "bat_user_test");
        fp.set(acc0, "bat_pass_test");

        Method mSave = helperClass.getDeclaredMethod("saveClientAccounts");
        mSave.invoke(null);

        // Check data/client-accounts.json relative to app dir
        File fApp = new File("data/client-accounts.json");
        if (!fApp.exists()) throw new RuntimeException("data/client-accounts.json missing in app dir");
        String contentApp = new String(Files.readAllBytes(fApp.toPath()), StandardCharsets.UTF_8);
        if (!contentApp.contains("bat_user_test")) {
            throw new RuntimeException("app data/client-accounts.json missing test user");
        }

        // Check parent data/client-accounts.json
        File fParent = new File("../data/client-accounts.json");
        if (!fParent.exists()) throw new RuntimeException("../data/client-accounts.json missing");
        String contentParent = new String(Files.readAllBytes(fParent.toPath()), StandardCharsets.UTF_8);
        if (!contentParent.contains("bat_user_test")) {
            throw new RuntimeException("parent data/client-accounts.json missing test user");
        }

        // Verify NO app/app directory was accidentally created
        if (new File("app").exists()) {
            throw new RuntimeException("Accidental nested 'app' folder was created inside app directory!");
        }

        System.out.println("SUCCESS_APP_CWD");
    }
}
"""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            src_file = tmp_path / "TestPersistenceCycleFromApp.java"
            src_file.write_text(test_code, encoding="utf-8")

            compile_res = subprocess.run(
                [javac_cmd, "--release", "8", "-cp", f"{APP_JAR};{ROOT}/jre/lib/*;{ROOT}/jre/lib/ext/*", "-d", str(tmp_path), str(src_file)],
                capture_output=True,
                text=True,
                cwd=str(ROOT / "app"),
            )
            self.assertEqual(compile_res.returncode, 0, f"Compile failed:\n{compile_res.stderr}")

            run_res = subprocess.run(
                [java_cmd, "-cp", f"tool.jar;{tmp_path}", "avt.TestPersistenceCycleFromApp"],
                capture_output=True,
                text=True,
                cwd=str(ROOT / "app"),
                timeout=30,
            )
            self.assertEqual(run_res.returncode, 0, f"Execution failed:\n{run_res.stdout}\n{run_res.stderr}")
            self.assertIn("SUCCESS_APP_CWD", run_res.stdout)

        # Restore clean template
        bak_file = ROOT / "data" / "client-accounts.json.bak"
        if bak_file.exists():
            clean_bytes = bak_file.read_bytes()
            (ROOT / "data" / "client-accounts.json").write_bytes(clean_bytes)
            (ROOT / "app" / "data" / "client-accounts.json").write_bytes(clean_bytes)


if __name__ == "__main__":
    unittest.main()
