package avt;

import java.lang.reflect.Method;

public class TestPatchedJar {
    public static void main(String[] args) {
        try {
            Class<?> qClass = Class.forName("avt.Q");
            for (Method m : qClass.getDeclaredMethods()) {
                if (m.getName().equals("I") && m.getReturnType() == void.class) {
                    m.setAccessible(true);
                    m.invoke(null, new Object[]{ new Object[0] });
                    System.out.println("Invoked patched Q.I(void)");
                    break;
                }
            }

            Method fMethod = qClass.getDeclaredMethod("F", Object[].class);
            fMethod.setAccessible(true);
            Object res = fMethod.invoke(null, new Object[]{ new Object[0] });
            System.out.println("\n==> Patched Q.F() Result: \n" + res);

            Method sMethod = qClass.getDeclaredMethod("S", Object[].class);
            sMethod.setAccessible(true);
            Object sRes = sMethod.invoke(null, new Object[]{ new Object[]{ "anything", "" } });
            System.out.println("\n==> Patched Q.S() Result: \n" + sRes);

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
