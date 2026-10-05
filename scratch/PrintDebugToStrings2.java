package avt;

import java.lang.reflect.*;

public class PrintDebugToStrings2 {
    public static void main(String[] args) throws Exception {
        Class<?> dClass = Class.forName("avt.DebugToDeath");
        Field fe = dClass.getDeclaredField("e");
        fe.setAccessible(true);
        String[] e = (String[]) fe.get(null);
        int[] indices = {213, 227, 349, 364, 328, 155, 16, 42, 18, 24, 54, 297};
        for (int idx : indices) {
            if (idx >= 0 && idx < e.length) {
                System.out.println("e[" + idx + "] = " + e[idx]);
            }
        }
    }
}
