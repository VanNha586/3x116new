package avt;

import java.io.*;

public class FindProxyMethod {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Java\\jdk-22\\bin\\javap.exe", "-c", "-p", "-cp", "f:/game/3x116/app/tool.jar", "avt.Q");
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        String cur = "";
        while ((line = r.readLine()) != null) {
            if (line.startsWith("  ") && line.contains("(")) {
                cur = line;
            }
            if (line.contains("Field I:Ljava/util/Map;") || line.contains("Field i:Ljava/util/Map;")) {
                System.out.println(cur.trim() + " => " + line.trim());
            }
        }
        p.waitFor();
    }
}
