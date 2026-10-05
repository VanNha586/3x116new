package avt;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class VerifyAllClassesInJar {
    public static void main(String[] args) throws Exception {
        File jar = new File("f:/game/3x116/app/tool.jar");
        URLClassLoader cl = new URLClassLoader(new URL[]{ jar.toURI().toURL() }, VerifyAllClassesInJar.class.getClassLoader());
        
        int verified = 0;
        int failed = 0;
        try (ZipFile zf = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze.getName().endsWith(".class")) {
                    String className = ze.getName().replace('/', '.').substring(0, ze.getName().length() - 6);
                    try {
                        Class<?> c = Class.forName(className, true, cl);
                        c.getDeclaredMethods();
                        verified++;
                    } catch (Throwable t) {
                        System.err.println("FAILED: " + className + " -> " + t);
                        failed++;
                    }
                }
            }
        }
        System.out.println("Verification complete: " + verified + " classes verified, " + failed + " failures.");
    }
}
