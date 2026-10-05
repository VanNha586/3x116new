package avt;

import java.lang.reflect.Field;

public class DumpClassNAb {
    public static void main(String[] args) throws Exception {
        Class<?> nClass = Class.forName("avt.game.N");
        Field fab = nClass.getDeclaredField("ab");
        fab.setAccessible(true);
        String[] ab = (String[]) fab.get(null);
        System.out.println("Strings in N.ab:");
        for (int i = 0; i < ab.length; i++) {
            if (ab[i] != null) {
                System.out.println("  ab[" + i + "] = " + ab[i]);
            }
        }
    }
}
