package avt;

public class DumpAllG {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        java.lang.reflect.Field fg = qClass.getDeclaredField("g");
        fg.setAccessible(true);
        String[] g = (String[]) fg.get(null);
        System.out.println("Total g strings: " + g.length);
        for (int i = 0; i < g.length; i++) {
            if (g[i] != null && (g[i].contains("localDb") || g[i].contains("Security") || g[i].contains("role") || g[i].contains("canManage") || g[i].contains("licenseValid"))) {
                System.out.println("g[" + i + "] = " + g[i]);
            }
        }
    }
}
