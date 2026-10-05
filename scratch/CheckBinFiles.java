package avt;

import java.io.File;
import java.nio.file.Files;

public class CheckBinFiles {
    public static void main(String[] args) throws Exception {
        File f1 = new File("f:/game/3x116/data/1.bin");
        File f2 = new File("f:/game/3x116/data/2.bin");
        if (f1.exists()) {
            byte[] b1 = Files.readAllBytes(f1.toPath());
            System.out.println("1.bin size: " + b1.length);
            String s1 = new String(b1, "UTF-8");
            if (s1.contains("licenseValid") || s1.contains("activated")) {
                System.out.println("1.bin contains license info!");
            }
        }
        if (f2.exists()) {
            byte[] b2 = Files.readAllBytes(f2.toPath());
            System.out.println("2.bin size: " + b2.length);
            String s2 = new String(b2, "UTF-8");
            if (s2.contains("licenseValid") || s2.contains("activated")) {
                System.out.println("2.bin contains license info!");
            }
        }
    }
}
