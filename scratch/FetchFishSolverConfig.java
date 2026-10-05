package avt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class FetchFishSolverConfig {
    public static void main(String[] args) {
        String[] urls = {
            "https://chia2thegioi.github.io/avatar3x/api-tool.json",
            "https://raw.githubusercontent.com/nhaxit/avatar3x/main/api-tool.json",
            "https://raw.githubusercontent.com/chia2thegioi/avatar3x/main/api-tool.json"
        };
        for (String urlStr : urls) {
            try {
                System.out.println("Fetching " + urlStr + "...");
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                System.out.println("Response from " + urlStr + ":");
                System.out.println(sb.toString());
            } catch (Throwable t) {
                System.out.println("Error fetching " + urlStr + ": " + t.getMessage());
            }
        }
    }
}
