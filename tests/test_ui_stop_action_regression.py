import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_JAR = ROOT / "app" / "tool.jar"
sys.path.insert(0, str(ROOT / "tools"))

import patch_stop_fallback as psfb  # noqa: E402
import patch_stop_flag as psf  # noqa: E402


def _find_java_commands():
    javac_cmd = "javac"
    java_cmd = "java"
    bundled_java = ROOT / "jre" / "bin" / "java.exe"
    if bundled_java.exists():
        java_cmd = str(bundled_java)
    return javac_cmd, java_cmd


class UIStopActionRegressionTests(unittest.TestCase):
    def test_ui_stop_and_stop_all_bytecode_calls_are_present(self):
        with zipfile.ZipFile(APP_JAR) as jar:
            data = jar.read("avt/Q.class")

        reader = psf.Reader(data)
        reader.take(8)
        pool = psf.ConstantPool.parse(data, reader)
        body = psf.Reader(data[reader.pos:])
        body.take(6)
        body.take(body.u2() * 2)
        for _ in range(body.u2()):
            body.take(6)
            for _ in range(body.u2()):
                body.take(2)
                body.take(body.u4())

        found_o = False
        found_q = False
        methods_count = body.u2()
        for _ in range(methods_count):
            body.u2()
            name = pool.utf8(body.u2())
            desc = pool.utf8(body.u2())
            for _ in range(body.u2()):
                aname = pool.utf8(body.u2())
                alen = body.u4()
                ainfo = body.take(alen)
                if aname == "Code":
                    code = psfb._code_from_attribute(ainfo)
                    if name == "O" and desc == "([Ljava/lang/Object;)Ljava/lang/String;":
                        found_o = psfb._has_static_call(code, pool, psfb.FALLBACK_METHOD)
                    elif name == "q" and desc == "([Ljava/lang/Object;)Ljava/lang/String;":
                        found_q = psfb._has_static_call(code, pool, psfb.STOP_ALL_METHOD)

        self.assertTrue(found_o, "avt.Q.O must call BypassHelper.stopBotAtIndex")
        self.assertTrue(found_q, "avt.Q.q must call BypassHelper.stopAllBots")

    def test_invoking_stop_action_marks_stopped_indices_and_kills_bot(self):
        javac_cmd, java_cmd = _find_java_commands()
        test_code = """
package test;

import avt.BypassHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

public class TestUIStopInvocation {
    public static void main(String[] args) throws Exception {
        Class<?> q = Class.forName("avt.Q");
        Method oMethod = null;
        Method qMethod = null;
        for (Method m : q.getDeclaredMethods()) {
            if (m.getName().equals("O") && m.getReturnType() == String.class
                    && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == Object[].class) {
                oMethod = m;
            }
            if (m.getName().equals("q") && m.getReturnType() == String.class
                    && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == Object[].class) {
                qMethod = m;
            }
        }
        if (oMethod == null) throw new RuntimeException("Could not find O method returning String");
        if (qMethod == null) throw new RuntimeException("Could not find q method returning String");
        oMethod.setAccessible(true);
        qMethod.setAccessible(true);

        // 1. Invoking avt.Q.O([0]) directly executes stopBotAtIndex(0)
        oMethod.invoke(null, new Object[]{ new Object[]{ Integer.valueOf(0) } });

        Field fStopped = BypassHelper.class.getDeclaredField("stoppedBotIndices");
        fStopped.setAccessible(true);
        Set<?> stopped = (Set<?>) fStopped.get(null);
        if (!stopped.contains(Integer.valueOf(0))) {
            System.err.println("FAIL: stoppedBotIndices does not contain index 0 after Q.O([0])");
            System.exit(1);
        }

        // 2. Invoking avt.Q.q() executes stopAllBots()
        qMethod.invoke(null, new Object[]{ new Object[0] });

        System.out.println("OK: UI stop actions invoked stopBotAtIndex and stopAllBots successfully");
    }
}
"""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            src_file = tmp_path / "TestUIStopInvocation.java"
            src_file.write_text(test_code, encoding="utf-8")

            compile_res = subprocess.run(
                [javac_cmd, "--release", "8", "-cp", str(APP_JAR), "-d", str(tmp_path), str(src_file)],
                capture_output=True,
                text=True,
            )
            self.assertEqual(compile_res.returncode, 0, f"Compilation failed:\n{compile_res.stderr}")

            run_res = subprocess.run(
                [java_cmd, "-cp", f"{APP_JAR};{tmp_path}", "test.TestUIStopInvocation"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(run_res.returncode, 0, f"Execution failed:\n{run_res.stderr}\n{run_res.stdout}")
            self.assertIn("OK: UI stop actions invoked stopBotAtIndex and stopAllBots successfully", run_res.stdout)


if __name__ == "__main__":
    unittest.main()
