package avt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class TestPopulateState {
    public static void main(String[] args) {
        try {
            Class<?> qClass = Class.forName("avt.Q");
            for (Method m : qClass.getDeclaredMethods()) {
                if (m.getName().equals("I") && m.getReturnType() == void.class) {
                    m.setAccessible(true);
                    m.invoke(null, new Object[]{ new Object[0] });
                    break;
                }
            }

            // Set E = true, c = false
            qClass.getDeclaredField("E").setBoolean(null, true);
            qClass.getDeclaredField("c").setBoolean(null, false);

            // Let's inspect what fields are used in Z()
            // a is org.json.simple.S
            // G is List (allowedModes)
            // W is String (timeRemaining)
            // s is org.json.simple.S
            // Let's inspect them!
            Field fG = qClass.getDeclaredField("G");
            fG.setAccessible(true);
            // Add all modes: "fishTool", "fishermanTool", "farmer", "combinedTool", etc.
            List list = (List) fG.get(null);
            list.addAll(Arrays.asList("fishTool", "fishermanTool", "farmer", "combinedTool"));

            Field fW = qClass.getDeclaredField("W");
            fW.setAccessible(true);
            fW.set(null, "Không giới hạn (Vĩnh viễn)");

            // In avt.W, let's see what avt.W is
            Field fj = qClass.getDeclaredField("j");
            fj.setAccessible(true);
            Object wObj = fj.get(null);
            System.out.println("wObj = " + wObj);
            if (wObj != null) {
                for (Field wf : wObj.getClass().getDeclaredFields()) {
                    wf.setAccessible(true);
                    System.out.println("  W field: " + wf.getName() + " (" + wf.getType().getName() + ") = " + wf.get(wObj));
                }
            }

            Method fMethod = qClass.getDeclaredMethod("F", Object[].class);
            fMethod.setAccessible(true);
            Object res = fMethod.invoke(null, new Object[]{ new Object[0] });
            System.out.println("\n==> F() Result: " + res);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
