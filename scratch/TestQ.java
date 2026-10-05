package avt;

import java.lang.reflect.Method;

public class TestQ {
    public static void main(String[] args) {
        try {
            Class<?> qClass = Class.forName("avt.Q");
            for (Method m : qClass.getDeclaredMethods()) {
                if (m.getName().equals("I") && m.getReturnType() == void.class) {
                    m.setAccessible(true);
                    m.invoke(null, new Object[]{ new Object[0] });
                    System.out.println("Invoked Q.I(void)");
                    break;
                }
            }
            Method fMethod = qClass.getDeclaredMethod("F", Object[].class);
            fMethod.setAccessible(true);
            Object res = fMethod.invoke(null, new Object[]{ new Object[0] });
            System.out.println("Result of avt.Q.F: " + res);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
