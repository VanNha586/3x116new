package avt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class TestPopulateState2 {
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

            Field fG = qClass.getDeclaredField("G");
            fG.setAccessible(true);
            List list = (List) fG.get(null);
            list.clear();
            list.addAll(Arrays.asList("fishTool", "fishermanTool", "farmer", "combinedTool"));

            Field fW = qClass.getDeclaredField("W");
            fW.setAccessible(true);
            fW.set(null, "Không giới hạn (Vĩnh viễn)");

            Field fj = qClass.getDeclaredField("j");
            fj.setAccessible(true);
            Object wObj = fj.get(null);
            if (wObj != null) {
                // Set F in wObj to -1
                Field fF = wObj.getClass().getDeclaredField("F");
                fF.setAccessible(true);
                fF.setInt(wObj, -1);
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
