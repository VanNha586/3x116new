package avt;

public class DumpGKeysCheck {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        java.lang.reflect.Field fg = qClass.getDeclaredField("g");
        fg.setAccessible(true);
        String[] g = (String[]) fg.get(null);
        System.out.println("g[406] = " + g[406]);
        System.out.println("g[42] = " + g[42]);
        System.out.println("g[339] = " + g[339]);
    }
}
