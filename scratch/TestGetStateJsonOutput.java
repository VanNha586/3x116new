package avt;

import java.lang.reflect.*;

public class TestGetStateJsonOutput {
    public static void main(String[] args) throws Exception {
        Class<?> bhClass = Class.forName("avt.BypassHelper");
        Method m = bhClass.getDeclaredMethod("getStateJson", Object[].class);
        m.setAccessible(true);
        String json = (String) m.invoke(null, new Object[]{ new Object[0] });
        System.out.println("getStateJson output:\n" + json);
    }
}
