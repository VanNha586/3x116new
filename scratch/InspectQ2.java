package avt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class InspectQ2 {
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
            System.out.println("=== FIELDS IN avt.Q (AFTER INIT) ===");
            for (Field f : qClass.getDeclaredFields()) {
                f.setAccessible(true);
                if (Modifier.isStatic(f.getModifiers())) {
                    Object val = f.get(null);
                    System.out.println(f.getType().getName() + " " + f.getName() + " = " + val);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
