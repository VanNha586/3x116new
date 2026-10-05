package avt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class InspectFishingLoopInK {
    public static void main(String[] args) throws Exception {
        Class<?> kClass = Class.forName("avt.game.k");
        Field fab = kClass.getDeclaredField("ab");
        fab.setAccessible(true);
        String[] ab = (String[]) fab.get(null);
        System.out.println("Strings in k.ab related to fishing:");
        for (int i = 0; i < ab.length; i++) {
            if (ab[i] != null && (ab[i].contains("quăng") || ab[i].contains("quang") || ab[i].contains("câu") || ab[i].contains("cau") || ab[i].contains("lượt"))) {
                System.out.println("ab[" + i + "] = " + ab[i]);
            }
        }
    }
}
