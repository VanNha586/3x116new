package avt;

import java.lang.reflect.Method;

public class TestCallF {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getName().equals("I") && m.getReturnType() == void.class) {
                m.setAccessible(true);
                m.invoke(null, new Object[]{ new Object[0] });
                break;
            }
        }

        BypassHelper.apply();

        Method mf = null;
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getName().equals("f") && m.getParameterTypes().length == 2 && m.getParameterTypes()[0] == int.class && m.getParameterTypes()[1] == String.class) {
                mf = m;
                break;
            }
        }
        mf.setAccessible(true);
        String accountsJson = BypassHelper.getStateJson(new Object[0]);
        System.out.println("Calling f(0, json)...");
        Object res = mf.invoke(null, 0, accountsJson);
        System.out.println("Result of f(0): " + res);
    }
}
