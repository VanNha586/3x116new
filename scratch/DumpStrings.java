import java.lang.reflect.Field;

public class DumpStrings {
    public static void main(String[] args) throws Exception {
        Class<?> cls = Class.forName("avt.DebugToDeath");
        Field f = cls.getDeclaredField("e");
        f.setAccessible(true);
        String[] arr = (String[]) f.get(null);
        for (int i = 0; i < arr.length; i++) {
            System.out.println(i + ": " + arr[i]);
        }
    }
}
