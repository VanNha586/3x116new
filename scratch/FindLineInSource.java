package avt;

import java.io.BufferedReader;
import java.io.FileReader;

public class FindLineInSource {
    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader("f:/game/3x116/demo_source/DebugToDeath.java"));
        String line;
        int lineNo = 1;
        while ((line = br.readLine()) != null) {
            if (line.contains("398") || line.contains("144")) {
                System.out.println(lineNo + ": " + line);
            }
            lineNo++;
        }
    }
}
