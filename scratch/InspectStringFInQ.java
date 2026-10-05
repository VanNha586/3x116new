package avt;

import java.io.*;

public class InspectStringFInQ {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Java\\jdk-22\\bin\\javap.exe", "-c", "-p", "-cp", "f:/game/3x116/backup_demo_20260817_115814/tool.jar.original", "avt.Q");
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        boolean inF = false;
        while ((line = r.readLine()) != null) {
            if (line.contains("static java.lang.String f(")) {
                inF = true;
            }
            if (inF) {
                System.out.println(line);
                if (line.trim().equals("}") || (line.startsWith("  ") && line.contains("static") && !line.contains("f("))) {
                    if (!line.contains("static java.lang.String f(")) break;
                }
            }
        }
        p.waitFor();
    }
}
