package avt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class InspectQ {
    public static void main(String[] args) {
        try {
            Class<?> qClass = Class.forName("avt.Q");
            System.out.println("=== FIELDS IN avt.Q ===");
            for (Field f : qClass.getDeclaredFields()) {
                f.setAccessible(true);
                Object val = null;
                if (Modifier.isStatic(f.getModifiers())) {
                    val = f.get(null);
                }
                System.out.println(f.getType().getSimpleName() + " " + f.getName() + " = " + val);
            }

            System.out.println("\n=== METHODS IN avt.Q ===");
            for (Method m : qClass.getDeclaredMethods()) {
                System.out.println(Modifier.toString(m.getModifiers()) + " " + m.getReturnType().getSimpleName() + " " + m.getName() + "(" + java.util.Arrays.toString(m.getParameterTypes()) + ")");
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
