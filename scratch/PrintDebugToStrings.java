package avt;

import java.io.*;
import java.lang.reflect.*;

public class PrintDebugToStrings {
    public static void main(String[] args) throws Exception {
        Class<?> dClass = Class.forName("avt.DebugToDeath");
        Field fe = dClass.getDeclaredField("e");
        fe.setAccessible(true);
        String[] e = (String[]) fe.get(null);
        int[] indices = {165, 367, 223, 149, 178, 385, 123, 138, 388, 346, 88, 181};
        for (int idx : indices) {
            if (idx >= 0 && idx < e.length) {
                System.out.println("e[" + idx + "] = " + e[idx]);
            }
        }
    }
}
