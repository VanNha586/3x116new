package avt;

import java.lang.reflect.Method;

public class FindJsonProducer {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getName().equals("I") && m.getReturnType() == void.class) {
                m.setAccessible(true);
                m.invoke(null, new Object[]{ new Object[0] });
                break;
            }
        }

        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getReturnType() == String.class && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == Object[].class) {
                m.setAccessible(true);
                try {
                    Object res = m.invoke(null, new Object[]{ new Object[0] });
                    if (res != null && res.toString().contains("activated")) {
                        System.out.println("Method " + m.getName() + " returned: " + res);
                    }
                } catch (Throwable t) {}
            }
        }
    }
}
