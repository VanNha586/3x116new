package avt;

import org.json.simple.S;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

public class TestProxySerialization {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getName().equals("I") && m.getReturnType() == void.class) {
                m.setAccessible(true);
                m.invoke(null, new Object[]{ new Object[0] });
                break;
            }
        }

        Field fI = qClass.getDeclaredField("I");
        fI.setAccessible(true);
        Map proxies = (Map) fI.get(null);
        System.out.println("Proxies map: " + proxies);
    }
}
