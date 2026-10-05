package avt;

import org.json.simple.S;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestRunAccount2 {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getName().equals("I") && m.getReturnType() == void.class) {
                m.setAccessible(true);
                m.invoke(null, new Object[]{ new Object[0] });
                break;
            }
        }

        BypassHelper.apply();

        // Let's also set s and a fields in Q
        Field fs = qClass.getDeclaredField("s");
        fs.setAccessible(true);
        S sessionObj = new S();
        sessionObj.put("status", "ok");
        sessionObj.put("message", "");
        sessionObj.put("activated", true);
        sessionObj.put("canUseTool", true);
        sessionObj.put("accountLimit", -1);
        sessionObj.put("allowedModes", Arrays.asList("fishTool", "fishermanTool", "farmer", "combinedTool"));
        sessionObj.put("timeRemaining", "Không giới hạn (Vĩnh viễn)");
        sessionObj.put("role", "user");
        sessionObj.put("canManage", true);
        sessionObj.put("licenseValid", true);
        sessionObj.put("localDbSecurity", true);
        fs.set(null, sessionObj);

        Field fa = qClass.getDeclaredField("a");
        fa.setAccessible(true);
        fa.set(null, sessionObj);

        Method mXy = null;
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getName().equals("Xy")) {
                mXy = m;
                break;
            }
        }

        String stateJson = BypassHelper.getStateJson(new Object[0]);
        System.out.println("Calling Xy with index 0, isRunning...");
        String res = (String) mXy.invoke(null, new Object[]{ new Object[]{ 0, "isRunning", stateJson } });
        System.out.println("Result of Xy: " + res);
    }
}
