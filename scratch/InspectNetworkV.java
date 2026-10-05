package avt;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;

public class InspectNetworkV {
    public static void main(String[] args) throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{ new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original").toURI().toURL() });
        Class<?> vClass = Class.forName("avt.network.v", true, cl);
        System.out.println("Fields in avt.network.v:");
        for (Field f : vClass.getDeclaredFields()) {
            System.out.println("  " + f.getType().getName() + " " + f.getName());
        }
    }
}
