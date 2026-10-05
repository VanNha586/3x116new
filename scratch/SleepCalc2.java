import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class SleepCalc2 {
    public static void main(String[] args) throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{ new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original").toURI().toURL() });
        Class<?> lClass = Class.forName("avt.game.l", true, cl);
        Class<?> nClass = Class.forName("avt.game.N", true, cl);
        Class<?> kClass = Class.forName("avt.game.k", true, cl);
        calc(lClass, "l.b(11929, 6508491092042608246)");
        calc(nClass, "N.b(12341, 785715963075315692)");
        calc(nClass, "N.b(11506, 1256379947032992574)");
        // k.Sd sleeps: k.b(1514, 6569224572090758733)
        calc(kClass, "k.b(1514, 6569224572090758733)");
    }
    static void calc(Class<?> cls, String label) {
        String core = label.substring(label.indexOf('(') + 1, label.indexOf(')'));
        String[] parts = core.split(",");
        String mn = label.substring(0, label.indexOf('.')).split("\\.")[0];
        int i = Integer.parseInt(parts[0].trim());
        long l = Long.parseLong(parts[1].trim());
        for (Method m : cls.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getName().equals(label.substring(label.indexOf('.') + 1, label.indexOf('('))) && p.length==2 && p[0]==int.class && p[1]==long.class && m.getReturnType()==long.class) {
                try { m.setAccessible(true); System.out.println(label + " = " + m.invoke(null, i, l) + " ms"); }
                catch (Throwable t) { System.out.println(label + " ERR " + t); }
            }
        }
    }
}
