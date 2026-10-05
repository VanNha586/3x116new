import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;

public class DumpKStrings {
    public static void main(String[] args) throws Exception {
        String jar = args.length > 0 ? args[0] : "f:/game/3x116/backup_demo_20260817_115814/tool.jar.original";
        URLClassLoader cl = new URLClassLoader(new URL[]{ new File(jar).toURI().toURL() });
        Class<?> kClass = Class.forName("avt.game.k", true, cl);
        Field f = kClass.getDeclaredField("ab");
        f.setAccessible(true);
        String[] arr = (String[]) f.get(null);
        for (int i = 0; i < arr.length; i++) {
            System.out.println(i + ": " + arr[i]);
        }
    }
}
