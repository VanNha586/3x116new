import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

public class SleepCalc {
    public static void main(String[] args) throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{ new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original").toURI().toURL() });
        Class<?> nClass = Class.forName("avt.game.N", true, cl);
        System.out.println("=== N methods (static, int+long or int+J) ===");
        for (Method m : nClass.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[0] == int.class && (p[1] == long.class || p[1] == int.class)) {
                System.out.println("  " + m.getName() + " " + Arrays.toString(p) + " -> " + m.getReturnType());
            }
        }
        Class<?> kClass = Class.forName("avt.game.k", true, cl);
        System.out.println("=== k methods (int+long) ===");
        for (Method m : kClass.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[0] == int.class && (p[1] == long.class || p[1] == int.class)) {
                System.out.println("  " + m.getName() + " " + Arrays.toString(p) + " -> " + m.getReturnType());
            }
        }

        Object[][] cases = {
            { "N.b(12341, 785715963075315692L)", "avt.game.N", "b", 12341, 785715963075315692L },
            { "N.b(11506, 1256379947032992574L)", "avt.game.N", "b", 11506, 1256379947032992574L },
        };
        for (Object[] c : cases) {
            try {
                Class<?> cls = Class.forName((String)c[1], true, cl);
                for (Method m : cls.getDeclaredMethods()) {
                    if (m.getName().equals(c[2])) {
                        m.setAccessible(true);
                        Class<?>[] p = m.getParameterTypes();
                        if (p.length == 2 && p[0] == int.class && p[1] == long.class) {
                            Object r = m.invoke(null, (Integer)c[3], (Long)c[4]);
                            System.out.println(c[0] + " = " + r);
                        } else if (p.length == 2 && p[0] == int.class && p[1] == int.class) {
                            Object r = m.invoke(null, (Integer)c[3], Integer.valueOf(((Long)c[4]).intValue()));
                            System.out.println(c[0] + " (int,int) = " + r);
                        }
                    }
                }
            } catch (Throwable t) {
                System.out.println(c[0] + " ERROR: " + t);
            }
        }
    }
}
