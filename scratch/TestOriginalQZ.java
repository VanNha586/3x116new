package avt;

import java.lang.reflect.*;

public class TestOriginalQZ {
    public static void main(String[] args) throws Exception {
        Class<?> bhClass = Class.forName("avt.BypassHelper");
        Method mApply = bhClass.getDeclaredMethod("apply");
        mApply.setAccessible(true);
        mApply.invoke(null);

        Class<?> qClass = Class.forName("avt.Q");
        Method mz = qClass.getDeclaredMethod("Z", Object[].class);
        mz.setAccessible(true);
        String json = (String) mz.invoke(null, new Object[]{ new Object[]{ Boolean.TRUE } });
        System.out.println("Q.Z output:\n" + json);
    }
}
