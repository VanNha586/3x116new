package avt;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class FindAllFishingPacketSenders {
    public static void main(String[] args) throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{ new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original").toURI().toURL() });
        Class<?> kClass = Class.forName("avt.game.k", true, cl);
        Class<?> lClass = Class.forName("avt.game.l", true, cl);

        System.out.println("k methods:");
        for (java.lang.reflect.Method m : kClass.getDeclaredMethods()) {
            if (m.getName().startsWith("lambda") || m.getName().equals("m") || m.getName().equals("V") || m.getName().equals("Sr") || m.getName().equals("z")) {
                System.out.println("  " + m.getName() + " " + java.util.Arrays.toString(m.getParameterTypes()));
            }
        }
    }
}
