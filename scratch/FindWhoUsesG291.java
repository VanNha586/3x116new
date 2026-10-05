package avt;

import java.io.*;

public class FindWhoUsesG291 {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Java\\jdk-22\\bin\\javap.exe", "-c", "-p", "-cp", "f:/game/3x116/app/tool.jar", "avt.Q");
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        String currentMethod = "";
        while ((line = r.readLine()) != null) {
            if (line.startsWith("  ") && line.contains("(")) {
                currentMethod = line;
            }
            if (line.contains("291") || line.contains("130") || line.contains("328")) {
                System.out.println(currentMethod.trim() + " => " + line.trim());
            }
        }
        p.waitFor();
    }
}
