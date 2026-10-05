package avt;

import java.lang.reflect.*;

public class PrintFishCountString {
    public static void main(String[] args) throws Exception {
        Class<?> nClass = Class.forName("avt.game.N");
        Field fab = nClass.getDeclaredField("ab");
        fab.setAccessible(true);
        String[] ab = (String[]) fab.get(null);
        for (int i = 0; i < ab.length; i++) {
            if (ab[i] != null && (ab[i].contains("Số lượng") || ab[i].contains("S? l??ng") || ab[i].contains("/200") || ab[i].contains("câu cá") || ab[i].contains("cu"))) {
                System.out.println("N.ab[" + i + "] = " + ab[i]);
            }
        }
    }
}
