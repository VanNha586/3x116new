package avt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class TestFieldsDetail {
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
            Field fE = qClass.getDeclaredField("E");
            fE.setAccessible(true);
            fE.setBoolean(null, true);

            Field fc = qClass.getDeclaredField("c");
            fc.setAccessible(true);
            fc.setBoolean(null, false);

            // Let's check other fields: accountLimit, allowedModes, timeRemaining, etc.
            // Let's print all fields and their types
            for (Field f : qClass.getDeclaredFields()) {
                f.setAccessible(true);
                if (Modifier.isStatic(f.getModifiers())) {
                    System.out.println(f.getName() + " (" + f.getType().getName() + ") = " + f.get(null));
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
