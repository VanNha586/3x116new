package avt;

import org.json.simple.S;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TestPopulateS {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getName().equals("I") && m.getReturnType() == void.class) {
                m.setAccessible(true);
                m.invoke(null, new Object[]{ new Object[0] });
                break;
            }
        }

        // Let's create S object
        S sessionObj = new S();
        sessionObj.put("status", "ok");
        sessionObj.put("message", "");
        sessionObj.put("activated", true);
        sessionObj.put("canUseTool", true);
        sessionObj.put("accountLimit", -1);
        sessionObj.put("allowedModes", Arrays.asList("fishTool", "fishermanTool", "farmer", "combinedTool"));
        sessionObj.put("timeRemaining", "Không giới hạn (Vĩnh viễn)");
        sessionObj.put("role", "VIP Pro");
        sessionObj.put("canManage", true);
        sessionObj.put("licenseValid", true);
        sessionObj.put("localDbSecurity", true);

        S updateObj = new S();
        updateObj.put("hasUpdate", false);
        updateObj.put("currentVersion", "1.1.6");
        updateObj.put("message", "Avatar 3x Tool - DebugToDeath v1.1.6");
        updateObj.put("forceUpdate", false);
        sessionObj.put("updateInfo", updateObj);

        Field fs = qClass.getDeclaredField("s");
        fs.setAccessible(true);
        fs.set(null, sessionObj);

        Field fW = qClass.getDeclaredField("W");
        fW.setAccessible(true);
        fW.set(null, "Không giới hạn (Vĩnh viễn)");

        Field fG = qClass.getDeclaredField("G");
        fG.setAccessible(true);
        List list = (List) fG.get(null);
        list.clear();
        list.addAll(Arrays.asList("fishTool", "fishermanTool", "farmer", "combinedTool"));

        Field fQ = qClass.getDeclaredField("Q");
        fQ.setAccessible(true);
        Object qObj = fQ.get(null);
        if (qObj != null) {
            Field fF = qObj.getClass().getDeclaredField("F");
            fF.setAccessible(true);
            fF.setInt(qObj, -1);
        }

        Method fMethod = qClass.getDeclaredMethod("F", Object[].class);
        fMethod.setAccessible(true);
        String res = (String) fMethod.invoke(null, new Object[]{ new Object[0] });
        System.out.println("FULL JSON OUTPUT:\n" + res);
    }
}
