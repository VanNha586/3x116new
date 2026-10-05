package avt;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class FindAllF {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getName().equals("F")) {
                System.out.println(Modifier.toString(m.getModifiers()) + " " + m.getReturnType().getName() + " F(" + java.util.Arrays.toString(m.getParameterTypes()) + ")");
            }
        }
    }
}
