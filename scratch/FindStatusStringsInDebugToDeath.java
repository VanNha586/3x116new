package avt;

import java.lang.reflect.Field;

public class FindStatusStringsInDebugToDeath {
    public static void main(String[] args) throws Exception {
        Class<?> dClass = Class.forName("avt.DebugToDeath");
        Field fe = dClass.getDeclaredField("e");
        fe.setAccessible(true);
        String[] e = (String[]) fe.get(null);
        for (int i = 0; i < e.length; i++) {
            if (e[i] != null && (e[i].contains("Đã dừng") || e[i].contains("Đang chạy") || e[i].contains("Da dung") || e[i].contains("Dang chay"))) {
                System.out.println("e[" + i + "] = " + e[i]);
            }
        }
    }
}
