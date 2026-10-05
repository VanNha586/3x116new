package avt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TestBypassComplete {
    public static void main(String[] args) {
        try {
            Class<?> qClass = Class.forName("avt.Q");
            for (Method m : qClass.getDeclaredMethods()) {
                if (m.getName().equals("I") && m.getReturnType() == void.class) {
                    m.setAccessible(true);
                    m.invoke(null, new Object[]{ new Object[0] });
                    break;
                }
            }

            // Apply bypass
            BypassHelper.apply();

            // Invoke F(Object[])
            for (Method m : qClass.getDeclaredMethods()) {
                if (m.getName().equals("F") && m.getReturnType() == String.class) {
                    m.setAccessible(true);
                    String json = (String) m.invoke(null, new Object[]{ new Object[0] });
                    System.out.println("FULL JSON STATE FROM Q.F():\n" + json);
                    break;
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
