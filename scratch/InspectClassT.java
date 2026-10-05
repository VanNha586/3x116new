package avt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class InspectClassT {
    public static void main(String[] args) throws Exception {
        Class<?> tClass = Class.forName("avt.t");
        System.out.println("Class t fields:");
        for (Field f : tClass.getDeclaredFields()) {
            System.out.println("  " + f.getType().getName() + " " + f.getName());
        }
        System.out.println("Class t methods:");
        for (Method m : tClass.getDeclaredMethods()) {
            System.out.println("  " + m.getReturnType().getName() + " " + m.getName() + "(" + java.util.Arrays.toString(m.getParameterTypes()) + ")");
        }
    }
}
