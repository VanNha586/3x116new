package avt;

import org.json.simple.S;
import java.lang.reflect.Method;
import java.util.List;

public class TestRunAccount {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getName().equals("I") && m.getReturnType() == void.class) {
                m.setAccessible(true);
                m.invoke(null, new Object[]{ new Object[0] });
                break;
            }
        }

        // Apply BypassHelper with role = "user"
        BypassHelper.apply();
        
        // Find Xy method
        Method mXy = null;
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getName().equals("Xy")) {
                mXy = m;
                break;
            }
        }
        
        System.out.println("Calling Xy with index 0, isRunning...");
        String res = (String) mXy.invoke(null, new Object[]{ new Object[]{ 0, "isRunning", "{}" } });
        System.out.println("Result of Xy: " + res);
    }
}
