package avt;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class InspectReconnectInK {
    public static void main(String[] args) throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{ new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original").toURI().toURL() });
        Class<?> kClass = Class.forName("avt.game.k", true, cl);
        
        System.out.println("Methods in k:");
        for (Method m : kClass.getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("reconnect") || m.getName().toLowerCase().contains("connect") || m.getName().toLowerCase().contains("login") || m.getName().toLowerCase().contains("start")) {
                System.out.println("  " + m.getName() + " " + java.util.Arrays.toString(m.getParameterTypes()));
            }
        }
    }
}
