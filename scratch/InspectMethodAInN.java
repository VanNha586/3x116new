package avt;

import java.io.*;

public class InspectMethodAInN {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Java\\jdk-22\\bin\\javap.exe", "-c", "-p", "-cp", "f:/game/3x116/app/tool.jar", "avt.N");
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        boolean inA = false;
        int count = 0;
        while ((line = r.readLine()) != null) {
            if (line.contains("org.json.simple.S a(")) {
                inA = true;
            }
            if (inA) {
                System.out.println(line);
                count++;
                if (count > 50) break;
            }
        }
        p.waitFor();
    }
}
