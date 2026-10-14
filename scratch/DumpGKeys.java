package avt;

public class DumpGKeys {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        java.lang.reflect.Field fg = qClass.getDeclaredField("g");
        fg.setAccessible(true);
        String[] g = (String[]) fg.get(null);
        int[] idxs = {290, 31, 269, 254, 318, 11};
        for (int i : idxs) {
            System.out.println("g[" + i + "] = " + g[i]);
        }
    }
}
