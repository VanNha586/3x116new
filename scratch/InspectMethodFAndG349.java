package avt;

import java.io.*;

public class InspectMethodFAndG349 {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        java.lang.reflect.Field fg = qClass.getDeclaredField("g");
        fg.setAccessible(true);
        String[] g = (String[]) fg.get(null);
        System.out.println("g[349] = " + g[349]);
        System.out.println("g[42] = " + g[42]);
        System.out.println("g[101] = " + g[101]);
    }
}
