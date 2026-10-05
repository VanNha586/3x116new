package avt;

import java.io.*;

public class InspectMethodBInN {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Java\\jdk-22\\bin\\javap.exe", "-c", "-p", "-cp", "f:/game/3x116/app/tool.jar", "avt.N");
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        boolean inB = false;
        while ((line = r.readLine()) != null) {
            if (line.contains("org.json.simple.S b(")) {
                inB = true;
            }
            if (inB) {
                System.out.println(line);
                if (line.trim().equals("}") || (line.startsWith("  ") && line.contains("org.json.simple.S") && !line.contains("b("))) {
                    if (!line.contains("org.json.simple.S b(")) {
                        break;
                    }
                }
            }
        }
        p.waitFor();
    }
}
