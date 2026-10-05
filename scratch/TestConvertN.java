package avt;

import org.json.simple.S;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class TestConvertN {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getName().equals("I") && m.getReturnType() == void.class) {
                m.setAccessible(true);
                m.invoke(null, new Object[]{ new Object[0] });
                break;
            }
        }

        Field fM = qClass.getDeclaredField("m");
        fM.setAccessible(true);
        List accounts = (List) fM.get(null);
        System.out.println("Accounts count: " + accounts.size());
        for (Object acc : accounts) {
            Method mb = acc.getClass().getDeclaredMethod("b", Object[].class);
            mb.setAccessible(true);
            S jsonAcc = (S) mb.invoke(acc, new Object[]{ new Object[0] });
            System.out.println("jsonAcc => " + jsonAcc);
        }
    }
}
