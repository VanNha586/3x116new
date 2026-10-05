package avt;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public class InspectWhereBotIsStored {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        System.out.println("=== Q Fields ===");
        for (Field f : qClass.getDeclaredFields()) {
            f.setAccessible(true);
            System.out.println(f.getName() + " : " + f.getType().getName() + " (static: " + Modifier.isStatic(f.getModifiers()) + ")");
        }
        
        Class<?> wClass = Class.forName("avt.W");
        System.out.println("=== W Fields ===");
        for (Field f : wClass.getDeclaredFields()) {
            f.setAccessible(true);
            System.out.println(f.getName() + " : " + f.getType().getName() + " (static: " + Modifier.isStatic(f.getModifiers()) + ")");
        }
    }
}
