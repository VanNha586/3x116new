package avt;

import java.lang.reflect.*;

public class PrintNAbStrings {
    public static void main(String[] args) throws Exception {
        Class<?> nClass = Class.forName("avt.game.N");
        Field fab = nClass.getDeclaredField("ab");
        fab.setAccessible(true);
        String[] ab = (String[]) fab.get(null);
        for (int i = 0; i < ab.length; i++) {
            if (ab[i] != null && (ab[i].contains("Cấp độ") || ab[i].contains("C?p") || ab[i].contains("chính") || ab[i].contains("đường trắng") || ab[i].contains("Trị giá"))) {
                System.out.println("N.ab[" + i + "] = " + ab[i]);
            }
        }
    }
}
