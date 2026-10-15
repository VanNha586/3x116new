import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_JAR = ROOT / "app" / "tool.jar"


def _find_java_commands():
    javac_cmd = "javac"
    java_cmd = "java"
    bundled_java = ROOT / "jre" / "bin" / "java.exe"
    if bundled_java.exists():
        java_cmd = str(bundled_java)
    return javac_cmd, java_cmd


class StopBehaviorRegressionTests(unittest.TestCase):
    def test_stop_bot_cancels_cast_and_blocks_reconnect(self):
        javac_cmd, java_cmd = _find_java_commands()
        test_code = """
package avt;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestStopBehavior {
    public static class FakeSession {
        public boolean closed = false;
        public void n(Object[] args) {
            closed = true;
        }
    }

    public static class FakeListener {
        public Object r; // bot
        public ScheduledFuture<?> b; // reconnect future
        public boolean reconnected = false;
        public void j(Object[] args) {
            reconnected = true;
        }
    }

    public static class FakeBot {
        public int hh = 0;
        public boolean hl = false;
        public boolean ho = false;
        public AtomicBoolean hy = new AtomicBoolean(false);
        public FakeSession J = new FakeSession();
        public FakeListener F = new FakeListener();
        public boolean stopMethodCalled = false;

        public FakeBot(int idx) {
            this.hh = idx;
            this.F.r = this;
        }

        public void N(Object[] args) {
            stopMethodCalled = true;
        }

        public void S(Object[] args) {
        }
    }

    public static void main(String[] args) throws Exception {
        FakeBot bot = new FakeBot(0);
        BypassHelper.registerActiveBot(bot);

        // 1. Verify doQuangCau schedules a cast tracked in pendingCastMap
        BypassHelper.doQuangCau(bot);
        Field fPending = BypassHelper.class.getDeclaredField("pendingCastMap");
        fPending.setAccessible(true);
        Map<?, ?> pending = (Map<?, ?>) fPending.get(null);
        if (!pending.containsKey(bot)) {
            System.err.println("FAIL: pendingCastMap does not contain scheduled cast for bot");
            System.exit(1);
        }
        ScheduledFuture<?> castFuture = (ScheduledFuture<?>) pending.get(bot);

        // 2. Stop the bot at index 0
        BypassHelper.stopBotAtIndex(0);

        // Verify cast future was cancelled
        if (!castFuture.isCancelled()) {
            System.err.println("FAIL: Scheduled cast was not cancelled on stopBotAtIndex");
            System.exit(2);
        }

        Field fStopped = BypassHelper.class.getDeclaredField("stoppedBotIndices");
        fStopped.setAccessible(true);
        Set<?> stopped = (Set<?>) fStopped.get(null);
        if (!stopped.contains(Integer.valueOf(0))) {
            System.err.println("FAIL: stoppedBotIndices does not contain index 0");
            System.exit(3);
        }

        // Verify bot.hl and bot.ho are true
        if (!bot.hl || !bot.ho) {
            System.err.println("FAIL: bot flags hl/ho not set to true: hl=" + bot.hl + ", ho=" + bot.ho);
            System.exit(4);
        }

        // Verify bot.hy (AtomicBoolean) is true (prevents Sd reconnect)
        if (!bot.hy.get()) {
            System.err.println("FAIL: bot.hy not set to true");
            System.exit(5);
        }

        // Verify bot.N was called
        if (!bot.stopMethodCalled) {
            System.err.println("FAIL: bot.N was not invoked");
            System.exit(6);
        }

        // 3. Test handleDisconnected with stopping bot
        BypassHelper.handleDisconnected(bot.F);
        if (bot.F.reconnected) {
            System.err.println("FAIL: handleDisconnected invoked reconnect j() on stopped bot");
            System.exit(7);
        }

        System.out.println("OK: Stop behavior verified successfully");
    }
}
"""
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            src_file = tmp_path / "TestStopBehavior.java"
            src_file.write_text(test_code, encoding="utf-8")

            compile_res = subprocess.run(
                [javac_cmd, "--release", "8", "-cp", str(APP_JAR), "-d", str(tmp_path), str(src_file)],
                capture_output=True,
                text=True,
            )
            self.assertEqual(compile_res.returncode, 0, f"Compilation failed:\n{compile_res.stderr}")

            run_res = subprocess.run(
                [java_cmd, "-cp", f"{APP_JAR};{tmp_path}", "avt.TestStopBehavior"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(run_res.returncode, 0, f"Execution failed:\n{run_res.stderr}\n{run_res.stdout}")
            self.assertIn("OK: Stop behavior verified successfully", run_res.stdout)


if __name__ == "__main__":
    unittest.main()
