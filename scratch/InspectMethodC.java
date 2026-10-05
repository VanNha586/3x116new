package avt;

import java.io.*;

public class InspectMethodC {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Java\\jdk-22\\bin\\javap.exe", "-c", "-p", "-cp", "f:/game/3x116/app/tool.jar", "avt.Q");
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        boolean inC = false;
        while ((line = r.readLine()) != null) {
            if (line.contains("static java.util.List C(")) {
                inC = true;
            }
            if (inC) {
                System.out.println(line);
                if (line.trim().equals("}") || (line.startsWith("  ") && line.contains("static") && !line.contains("C("))) {
                    if (!line.contains("static java.util.List C(")) {
                        break;
                    }
                }
            }
        }
        p.waitFor();
    }
}
