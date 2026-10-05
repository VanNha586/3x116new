package avt;

import java.lang.reflect.Method;

public class ListMethodsOfN {
    public static void main(String[] args) throws Exception {
        Class<?> nClass = Class.forName("avt.N");
        for (Method m : nClass.getDeclaredMethods()) {
            System.out.println(m.getReturnType().getName() + " " + m.getName() + "(" + java.util.Arrays.toString(m.getParameterTypes()) + ")");
        }
    }
}
