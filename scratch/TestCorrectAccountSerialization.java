package avt;

import org.json.simple.S;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class TestCorrectAccountSerialization {
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

        ArrayList<S> jsonAccounts = new ArrayList<S>();
        if (accounts != null) {
            for (int i = 0; i < accounts.size(); i++) {
                Object acc = accounts.get(i);
                Method ma = acc.getClass().getDeclaredMethod("a", Object[].class);
                ma.setAccessible(true);
                S jsonAcc = (S) ma.invoke(acc, new Object[]{ new Object[]{ Integer.valueOf(i) } });
                jsonAccounts.add(jsonAcc);
            }
        }

        S root = new S();
        root.put("status", "ok");
        root.put("activated", true);
        root.put("canUseTool", true);
        root.put("accountLimit", -1);
        root.put("accounts", jsonAccounts);

        System.out.println("VALID JSON ROOT: " + root.z(new Object[0]));
    }
}
