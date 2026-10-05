package avt;

import java.io.*;

public class InspectMethodPublicO {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Java\\jdk-22\\bin\\javap.exe", "-c", "-p", "-cp", "f:/game/3x116/app/tool.jar", "avt.Q");
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        boolean inO = false;
        while ((line = r.readLine()) != null) {
            if (line.contains("public static java.lang.String o(")) {
                inO = true;
            }
            if (inO) {
                System.out.println(line);
                if (line.trim().equals("}") || (line.startsWith("  ") && line.contains("static") && !line.contains("o("))) {
                    if (!line.contains("public static java.lang.String o(")) {
                        break;
                    }
                }
            }
        }
        p.waitFor();
    }
}
