package avt;

import java.io.*;

public class PrintFullMethodSInQ2 {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Java\\jdk-22\\bin\\javap.exe", "-c", "-p", "-cp", "f:/game/3x116/backup_demo_20260817_115814/tool.jar.original", "avt.Q");
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        int remaining = 0;
        while ((line = r.readLine()) != null) {
            if (line.contains("private static java.lang.String s(java.lang.Object[]);")) {
                remaining = 150;
            }
            if (remaining > 0) {
                System.out.println(line);
                remaining--;
            }
        }
        p.waitFor();
    }
}
