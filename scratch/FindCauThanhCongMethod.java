package avt;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class FindCauThanhCongMethod {
    public static void main(String[] args) throws Exception {
        File jarFile = new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original");
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jarFile)) {
            // Check N.ab
            Class<?> nClass = Class.forName("avt.game.N");
            Field fab = nClass.getDeclaredField("ab");
            fab.setAccessible(true);
            String[] ab = (String[]) fab.get(null);
            for (int i = 0; i < ab.length; i++) {
                if (ab[i] != null && (ab[i].contains("thành công") || ab[i].contains("chuẩn bị lượt tiếp theo") || ab[i].contains("chu?n b? l??t ti?p theo") || ab[i].contains("thnh cng"))) {
                    System.out.println("N.ab[" + i + "] = " + ab[i]);
                }
            }
            
            // Check k.ab
            Class<?> kClass = Class.forName("avt.game.k");
            Field kfab = kClass.getDeclaredField("ab");
            kfab.setAccessible(true);
            String[] kab = (String[]) kfab.get(null);
            for (int i = 0; i < kab.length; i++) {
                if (kab[i] != null && (kab[i].contains("thành công") || kab[i].contains("chuẩn bị lượt tiếp theo") || kab[i].contains("chu?n b? l??t ti?p theo") || kab[i].contains("thnh cng"))) {
                    System.out.println("k.ab[" + i + "] = " + kab[i]);
                }
            }
        }
    }
}
