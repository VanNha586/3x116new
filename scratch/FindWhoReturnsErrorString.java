package avt;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class FindWhoReturnsErrorString {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getReturnType() == String.class && Modifier.isStatic(m.getModifiers())) {
                m.setAccessible(true);
                Class<?>[] params = m.getParameterTypes();
                try {
                    Object res = null;
                    if (params.length == 1 && params[0] == Object[].class) {
                        res = m.invoke(null, new Object[]{ new Object[0] });
                    }
                    if (res != null && res.toString().contains("Chỉ user")) {
                        System.out.println("Method " + m.getName() + "([Ljava/lang/Object;)Ljava/lang/String returned the error!");
                    }
                } catch (Throwable t) {}
            }
        }
    }
}
