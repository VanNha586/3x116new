package avt;

import java.io.*;

public class InspectStringSInQ {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Java\\jdk-22\\bin\\javap.exe", "-c", "-p", "-cp", "f:/game/3x116/backup_demo_20260817_115814/tool.jar.original", "avt.Q");
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        boolean inS = false;
        int count = 0;
        while ((line = r.readLine()) != null) {
            if (line.contains("static java.lang.String s(") || line.contains("private static java.lang.String s(")) {
                inS = true;
            }
            if (inS) {
                System.out.println(line);
                count++;
                if (count > 250) break;
            }
        }
        p.waitFor();
    }
}
