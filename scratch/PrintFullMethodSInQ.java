package avt;

import java.io.*;

public class PrintFullMethodSInQ {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Java\\jdk-22\\bin\\javap.exe", "-c", "-p", "-cp", "f:/game/3x116/backup_demo_20260817_115814/tool.jar.original", "avt.Q");
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        boolean inS = false;
        while ((line = r.readLine()) != null) {
            if (line.contains("private static java.lang.String s(java.lang.Object[]);")) {
                inS = true;
            }
            if (inS) {
                System.out.println(line);
                if (line.trim().equals("}") || (line.startsWith("  ") && line.contains("static") && !line.contains("s("))) {
                    if (!line.contains("private static java.lang.String s(java.lang.Object[]);")) break;
                }
            }
        }
        p.waitFor();
    }
}
