package avt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class TestAuthFlow {
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

            // Apply bypass patch state:
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
                Field fF = wObj.getClass().getDeclaredField("F");
                fF.setAccessible(true);
                fF.setInt(wObj, -1);
            }

            // Test if any periodic task or network call in Q or elsewhere tries to revert it:
            System.out.println("Calling F():");
            Method fMethod = qClass.getDeclaredMethod("F", Object[].class);
            fMethod.setAccessible(true);
            System.out.println("F() => " + fMethod.invoke(null, new Object[]{ new Object[0] }));

            // Test Q.S (login method) - what if user clicks login or anything?
            Method sMethod = qClass.getDeclaredMethod("S", Object[].class);
            sMethod.setAccessible(true);
            System.out.println("S(\"test\", \"\") => " + sMethod.invoke(null, new Object[]{ new Object[]{ "test", "" } }));

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
