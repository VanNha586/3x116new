package avt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class InspectClassJ {
    public static void main(String[] args) throws Exception {
        Class<?> jClass = Class.forName("avt.game.j");
        System.out.println("Fields in avt.game.j:");
        for (Field f : jClass.getDeclaredFields()) {
            f.setAccessible(true);
            System.out.println("  " + f.getType().getName() + " " + f.getName());
        }

        System.out.println("\nMethods in avt.game.j:");
        for (Method m : jClass.getDeclaredMethods()) {
            System.out.println("  " + m.getReturnType().getName() + " " + m.getName() + "(" + java.util.Arrays.toString(m.getParameterTypes()) + ")");
        }

        Field fa = jClass.getDeclaredField("a");
        fa.setAccessible(true);
        String[] a = (String[]) fa.get(null);
        System.out.println("\nStrings in avt.game.j.a:");
        for (int i = 0; i < a.length; i++) {
            System.out.println("  a[" + i + "] = " + a[i]);
        }
    }
}
