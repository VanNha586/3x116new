package avt;

import java.lang.reflect.Field;

public class FindDisconnectInK {
    public static void main(String[] args) throws Exception {
        Class<?> kClass = Class.forName("avt.game.k");
        Field fab = kClass.getDeclaredField("ab");
        fab.setAccessible(true);
        String[] ab = (String[]) fab.get(null);
        for (int i = 0; i < ab.length; i++) {
            if (ab[i] != null && (ab[i].contains("Mất kết nối") || ab[i].contains("kết nối") || ab[i].contains("Đăng nhập lại") || ab[i].contains("thoát") || ab[i].contains("Dừng"))) {
                System.out.println("ab[" + i + "] = " + ab[i]);
            }
        }
    }
}
