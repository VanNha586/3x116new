package avt;

import java.lang.reflect.*;

public class PrintNAbStrings {
    public static void main(String[] args) throws Exception {
        Class<?> nClass = Class.forName("avt.game.N");
        Field fab = nClass.getDeclaredField("ab");
        fab.setAccessible(true);
        String[] ab = (String[]) fab.get(null);
        for (int i = 0; i < ab.length; i++) {
            System.out.println("N.ab[" + i + "] = " + ab[i]);
        }
    }
}
