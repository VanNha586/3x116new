package avt;

import java.lang.reflect.Field;

public class DumpAllMStrings {
    public static void main(String[] args) throws Exception {
        Class<?> mClass = Class.forName("avt.M");
        Field fa = mClass.getDeclaredField("a");
        fa.setAccessible(true);
        String[] a = (String[]) fa.get(null);
        for (int i = 0; i < a.length; i++) {
            System.out.println("M.a[" + i + "] = " + a[i]);
        }
    }
}
