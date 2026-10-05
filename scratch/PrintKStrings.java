package avt;

import java.io.*;
import java.lang.reflect.*;

public class PrintKStrings {
    public static void main(String[] args) throws Exception {
        Class<?> kClass = Class.forName("avt.game.k");
        Field fab = kClass.getDeclaredField("ab");
        fab.setAccessible(true);
        String[] ab = (String[]) fab.get(null);
        for (int i = 0; i < ab.length; i++) {
            if (ab[i] != null && (ab[i].contains("Nhân vật") || ab[i].contains("Cấp độ") || ab[i].contains("Xu:") || ab[i].contains("Lượng:") || ab[i].contains("farm"))) {
                System.out.println("ab[" + i + "] = " + ab[i]);
            }
        }
    }
}
