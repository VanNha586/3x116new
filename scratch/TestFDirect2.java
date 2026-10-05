package avt;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class TestFDirect2 {
    public static void main(String[] args) {
        try {
            Class<?> qClass = Class.forName("avt.Q");
            Method initMethod = null;
            Method fMethod = null;

            for (Method m : qClass.getDeclaredMethods()) {
                if (Modifier.isPublic(m.getModifiers()) && m.getName().equals("I") && m.getReturnType() == void.class) {
                    initMethod = m;
                }
                if (Modifier.isPublic(m.getModifiers()) && m.getName().equals("F") && m.getReturnType() == String.class) {
                    fMethod = m;
                }
            }

            initMethod.invoke(null, new Object[]{ new Object[0] });
            String json = (String) fMethod.invoke(null, new Object[]{ new Object[0] });
            System.out.println("JSON STATE FROM PATCHED TOOL:\n" + json);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
