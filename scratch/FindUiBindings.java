package avt;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public class FindUiBindings {
    public static void main(String[] args) throws Exception {
        File jarFile = new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original");
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                byte[] data = baos.toByteArray();
                String text = new String(data, "ISO-8859-1");
                if (name.endsWith(".fxml") || text.contains("selectedAccount") || text.contains("dailyFishCount") || text.contains("farmReturnAt") || text.contains("currentMission")) {
                    System.out.println("Match entry: " + name);
                }
            }
        }
    }
}
