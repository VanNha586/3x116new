package avt;

import java.lang.reflect.Method;

public class TestRuntimeFishSolverReady {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getName().equals("I") && m.getReturnType() == void.class) {
                m.setAccessible(true);
                m.invoke(null, new Object[]{ new Object[0] });
                break;
            }
        }

        Class<?> jClass = Class.forName("avt.game.j");
        Method ms = jClass.getDeclaredMethod("s", Object[].class);
        ms.setAccessible(true);
        boolean isReady = (boolean) ms.invoke(null, new Object[]{ new Object[0] });
        System.out.println("Fish Solver Ready in runtime: " + isReady);
    }
}
