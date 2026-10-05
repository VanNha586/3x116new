package avt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class DumpClassL {
    public static void main(String[] args) throws Exception {
        Class<?> lClass = Class.forName("avt.game.l");
        Field fa = lClass.getDeclaredField("a");
        fa.setAccessible(true);
        String[] a = (String[]) fa.get(null);
        System.out.println("Strings in avt.game.l.a:");
        for (int i = 0; i < a.length; i++) {
            System.out.println("  a[" + i + "] = " + a[i]);
        }

        System.out.println("\nFields in avt.game.l:");
        for (Field f : lClass.getDeclaredFields()) {
            System.out.println("  " + f.getType().getName() + " " + f.getName());
        }

        System.out.println("\nMethods in avt.game.l:");
        for (Method m : lClass.getDeclaredMethods()) {
            System.out.println("  " + m.getReturnType().getName() + " " + m.getName() + "(" + java.util.Arrays.toString(m.getParameterTypes()) + ")");
        }
    }
}
