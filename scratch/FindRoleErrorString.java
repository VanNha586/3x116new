package avt;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class FindRoleErrorString {
    public static void main(String[] args) throws Exception {
        File dir = new File("f:/game/3x116/scratch/jar_unpacked");
        List<String> classNames = new ArrayList<>();
        findClassNames(dir, "", classNames);
        
        URLClassLoader cl = new URLClassLoader(new URL[]{ new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original").toURI().toURL() });

        for (String cname : classNames) {
            try {
                Class<?> c = Class.forName(cname, true, cl);
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == String[].class) {
                        f.setAccessible(true);
                        String[] arr = (String[]) f.get(null);
                        if (arr != null) {
                            for (int i = 0; i < arr.length; i++) {
                                if (arr[i] != null && (arr[i].contains("Chỉ user") || arr[i].contains("tài khoản game") || arr[i].contains("user"))) {
                                    System.out.println("Found in " + cname + "." + f.getName() + "[" + i + "] = " + arr[i]);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {}
        }
    }

    private static void findClassNames(File dir, String pkg, List<String> res) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) {
                findClassNames(f, pkg.isEmpty() ? f.getName() : pkg + "." + f.getName(), res);
            } else if (f.getName().endsWith(".class")) {
                String name = f.getName().replace(".class", "");
                res.add(pkg.isEmpty() ? name : pkg + "." + name);
            }
        }
    }
}
