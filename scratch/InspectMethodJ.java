package avt;

import java.io.*;

public class InspectMethodJ {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Java\\jdk-22\\bin\\javap.exe", "-c", "-p", "-cp", "f:/game/3x116/app/tool.jar", "avt.Q");
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        boolean inJ = false;
        int count = 0;
        while ((line = r.readLine()) != null) {
            if (line.contains("private static java.lang.String j(")) {
                inJ = true;
            }
            if (inJ) {
                System.out.println(line);
                count++;
                if (count > 250) break;
            }
        }
        p.waitFor();
    }
}
