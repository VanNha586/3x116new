package avt;

import java.lang.reflect.Field;

public class DumpMStrings {
    public static void main(String[] args) throws Exception {
        Class<?> mClass = Class.forName("avt.M");
        Field fa = mClass.getDeclaredField("a");
        fa.setAccessible(true);
        String[] a = (String[]) fa.get(null);
        System.out.println("Total strings in M.a: " + a.length);
        for (int i = 0; i < a.length; i++) {
            if (a[i] != null && (a[i].contains("user") || a[i].contains("tài khoản") || a[i].contains("Chỉ") || a[i].contains("Lỗi") || a[i].contains("role") || a[i].contains("quyền"))) {
                System.out.println("M.a[" + i + "] = " + a[i]);
            }
        }
    }
}
