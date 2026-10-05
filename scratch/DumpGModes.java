package avt;

public class DumpGModes {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        java.lang.reflect.Field fg = qClass.getDeclaredField("g");
        fg.setAccessible(true);
        String[] g = (String[]) fg.get(null);
        int[] arr = {202, 374, 252, 29, 317, 382, 153, 222, 396, 186, 24, 262, 402, 198, 35, 148, 381, 324, 188};
        for (int i : arr) {
            System.out.println("g[" + i + "] = " + g[i]);
        }
    }
}
