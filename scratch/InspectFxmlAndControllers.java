package avt;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public class InspectFxmlAndControllers {
    public static void main(String[] args) throws Exception {
        File jarFile = new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original");
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".fxml")) {
                    System.out.println("=== FXML: " + name + " ===");
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                    System.out.println(new String(baos.toByteArray(), "UTF-8"));
                }
            }
        }
    }
}
