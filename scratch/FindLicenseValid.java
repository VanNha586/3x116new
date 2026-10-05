package avt;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class FindLicenseValid {
    public static void main(String[] args) throws Exception {
        File dir = new File("f:/game/3x116/scratch/jar_unpacked");
        List<File> classFiles = new ArrayList<>();
        findClasses(dir, classFiles);
        
        for (File f : classFiles) {
            byte[] bytes = Files.readAllBytes(f.toPath());
            String str = new String(bytes, "ISO-8859-1");
            if (str.contains("licenseValid") || str.contains("localDbSecurity") || str.contains("canManage")) {
                System.out.println("Match: " + f.getAbsolutePath().replace("\\", "/").replace("f:/game/3x116/scratch/jar_unpacked/", ""));
            }
        }
    }

    private static void findClasses(File dir, List<File> res) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) findClasses(f, res);
            else if (f.getName().endsWith(".class")) res.add(f);
        }
    }
}
