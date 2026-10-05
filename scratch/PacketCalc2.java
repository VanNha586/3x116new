import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class PacketCalc2 {
    public static void main(String[] args) throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{ new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original").toURI().toURL() });
        Class<?> kClass = Class.forName("avt.game.k", true, cl);
        calcInt(kClass, 829, 978663027020545563L, "k.m ketThucLuotCau packet");
        calcInt(kClass, 21049, 8881316959138606860L, "k.k chonMoi packet");
        calcInt(kClass, 166, 6424088657641061819L, "k.Pw packet");
        // guard values
        calcInt(kClass, 21557, 9088880993807526195L, "k.V guard O==?");
        calcInt(kClass, 27685, 4621276158328394039L, "k.Sr/k.m/k.k guard O==?");
    }
    static void calcInt(Class<?> cls, int i, long l, String label) {
        for (Method m : cls.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getName().equals("a") && p.length==2 && p[0]==int.class && p[1]==long.class && m.getReturnType()==int.class) {
                try { m.setAccessible(true); System.out.println(label + " = " + m.invoke(null, i, l)); }
                catch (Throwable t) { System.out.println(label + " ERR " + t); }
            }
        }
    }
}
