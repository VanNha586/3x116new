package avt;

public class DumpGKeys {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        java.lang.reflect.Field fg = qClass.getDeclaredField("g");
        fg.setAccessible(true);
        String[] g = (String[]) fg.get(null);
        int[] idxs = {339, 36, 360, 393, 70, 13, 331, 98, 109, 346, 367, 304, 391, 337, 284, 20};
        for (int i : idxs) {
            System.out.println("g[" + i + "] = " + g[i]);
        }
    }
}
