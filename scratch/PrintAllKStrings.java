package avt;

import java.io.*;
import java.lang.reflect.*;

public class PrintAllKStrings {
    public static void main(String[] args) throws Exception {
        Class<?> kClass = Class.forName("avt.game.k");
        Field fab = kClass.getDeclaredField("ab");
        fab.setAccessible(true);
        String[] ab = (String[]) fab.get(null);
        for (int i = 0; i < ab.length; i++) {
            System.out.println(i + ": " + ab[i]);
        }
    }
}
