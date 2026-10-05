package avt;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public class InspectQStateFieldsRuntime {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        System.out.println("=== Q State Runtime Check ===");
        String[] fields = {"o", "d", "I", "m", "w", "D", "j", "Q", "i", "V"};
        for (String fName : fields) {
            try {
                Field f = qClass.getDeclaredField(fName);
                f.setAccessible(true);
                Object val = f.get(null);
                System.out.println("Q." + fName + " = " + val);
            } catch (Throwable t) {
                System.out.println("Q." + fName + " -> error: " + t.getMessage());
            }
        }
    }
}
