package avt;

import java.io.*;

public class FindRoleChecksInDebugToDeath {
    public static void main(String[] args) throws Exception {
        File file = new File("f:/game/3x116/demo_source/DebugToDeath.java");
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        String line;
        int lineNo = 0;
        while ((line = r.readLine()) != null) {
            lineNo++;
            if (line.contains("role") || line.contains("user") || line.contains("admin") || line.contains("Chỉ user")) {
                System.out.println(lineNo + ": " + line);
            }
        }
        r.close();
    }
}
