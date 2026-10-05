import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

public class PacketCalc {
    public static void main(String[] args) throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{ new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original").toURI().toURL() });
        Class<?> kClass = Class.forName("avt.game.k", true, cl);
        // k.V packet: a(23050, 3046246223413995502); guard O==a(21557, 9088880993807526195)
        call(kClass, "a", 23050, 3046246223413995502L, "k.V -> v( ? )");
        call(kClass, "a", 21557, 9088880993807526195L, "k.V guard a(21557,.)");
        // k.Sr packet: a(30908, 7601524680053114248); guard O==a(27685, 4621276158328394039)
        call(kClass, "a", 30908, 7601524680053114248L, "k.Sr -> v( ? )");
        call(kClass, "a", 27685, 4621276158328394039L, "k.Sr guard a(27685,.)");
    }
    static void call(Class<?> cls, String name, int i, long l, String label) {
        try {
            for (Method m : cls.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals(name) && p.length == 2 && p[0] == int.class && p[1] == long.class && m.getReturnType() == int.class) {
                    m.setAccessible(true);
                    Object r = m.invoke(null, i, l);
                    System.out.println(label + " = " + r);
                }
            }
        } catch (Throwable t) { System.out.println(label + " ERR " + t); }
    }
}
