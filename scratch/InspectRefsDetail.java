package avt;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class InspectRefsDetail {
    public static void main(String[] args) throws Exception {
        List<String> list = Arrays.asList(
            "avt/game/k.class",
            "avt/game/N.class",
            "avt/game/O.class",
            "avt/a.class",
            "avt/e.class",
            "avt/K.class",
            "avt/q.class",
            "avt/x.class",
            "avt/y.class"
        );
        for (String rel : list) {
            System.out.println("\n=== Inspecting: " + rel + " ===");
            ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Java\\jdk-22\\bin\\javap.exe", "-c", "-p", "-cp", "f:/game/3x116/scratch/jar_unpacked", rel.replace(".class", ""));
            Process p = pb.start();
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains("avt/Q")) {
                    System.out.println("  " + line.trim());
                }
            }
            p.waitFor();
        }
    }
}
