package avt;

import java.lang.reflect.Field;

public class PrintKabIndexes {
    public static void main(String[] args) throws Exception {
        Class<?> kClass = Class.forName("avt.game.k");
        Field fab = kClass.getDeclaredField("ab");
        fab.setAccessible(true);
        String[] ab = (String[]) fab.get(null);
        int[] arr = {55, 29, 21, 24};
        for (int i : arr) {
            System.out.println("ab[" + i + "] = " + ab[i]);
        }
    }
}
