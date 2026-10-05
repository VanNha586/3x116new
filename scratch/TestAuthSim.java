package avt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TestAuthSim {
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
            
            // Set fields in Q
            for (Field f : qClass.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == boolean.class) {
                    System.out.println("Boolean field: " + f.getName() + " = " + f.getBoolean(null));
                }
            }

            // Let's invoke Z(true)
            Method zMethod = null;
            for (Method m : qClass.getDeclaredMethods()) {
                if (m.getName().equals("Z") && m.getReturnType() == String.class) {
                    zMethod = m;
                    break;
                }
            }
            if (zMethod != null) {
                zMethod.setAccessible(true);
                Object res = zMethod.invoke(null, new Object[]{ new Object[]{ Boolean.TRUE } });
                System.out.println("Result of Z(true): " + res);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
