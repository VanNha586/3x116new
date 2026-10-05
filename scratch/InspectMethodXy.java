package avt;

import java.io.*;

public class InspectMethodXy {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Java\\jdk-22\\bin\\javap.exe", "-c", "-p", "-cp", "f:/game/3x116/backup_demo_20260817_115814/tool.jar.original", "avt.Q");
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        boolean inXy = false;
        int count = 0;
        while ((line = r.readLine()) != null) {
            if (line.contains("Xy(") || line.contains("xy(")) {
                inXy = true;
            }
            if (inXy) {
                System.out.println(line);
                count++;
                if (count > 200) break;
            }
        }
        p.waitFor();
    }
}
