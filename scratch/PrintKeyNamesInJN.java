package avt;

import java.lang.reflect.Field;

public class PrintKeyNamesInJN {
    public static void main(String[] args) throws Exception {
        Class<?> jClass = Class.forName("avt.game.j");
        Field fa = jClass.getDeclaredField("a");
        fa.setAccessible(true);
        String[] a = (String[]) fa.get(null);
        System.out.println("a[16] = " + a[16]);
        System.out.println("a[25] = " + a[25]);
        System.out.println("a[23] = " + a[23]);
        System.out.println("a[22] = " + a[22]);
        System.out.println("a[14] = " + a[14]);
        System.out.println("a[28] = " + a[28]);
        System.out.println("a[12] = " + a[12]);
        System.out.println("a[10] = " + a[10]);
        System.out.println("a[21] = " + a[21]);
        System.out.println("a[8] = " + a[8]);
        System.out.println("a[13] = " + a[13]);
    }
}
