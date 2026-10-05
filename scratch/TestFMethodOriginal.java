package avt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class TestFMethodOriginal {
    public static void main(String[] args) throws Exception {
        Class<?> qClass = Class.forName("avt.Q");
        for (Method m : qClass.getDeclaredMethods()) {
            if (m.getName().equals("I") && m.getReturnType() == void.class) {
                m.setAccessible(true);
                m.invoke(null, new Object[]{ new Object[0] });
                break;
            }
        }

        // Set E = true, c = false
        Field fE = qClass.getDeclaredField("E");
        fE.setAccessible(true);
        fE.setBoolean(null, true);

        Field fc = qClass.getDeclaredField("c");
        fc.setAccessible(true);
        fc.setBoolean(null, false);

        Field fW = qClass.getDeclaredField("W");
        fW.setAccessible(true);
        fW.set(null, "Không giới hạn (Vĩnh viễn)");

        Field fG = qClass.getDeclaredField("G");
        fG.setAccessible(true);
        List list = (List) fG.get(null);
        list.clear();
        list.addAll(Arrays.asList("fishTool", "fishermanTool", "farmer", "combinedTool"));

        Field fj = qClass.getDeclaredField("j");
        fj.setAccessible(true);
        Object jObj = fj.get(null);
        if (jObj != null) {
            Field fF = jObj.getClass().getDeclaredField("F");
            fF.setAccessible(true);
            fF.setInt(jObj, -1);
        }

        Field fQ = qClass.getDeclaredField("Q");
        fQ.setAccessible(true);
        Object qObj = fQ.get(null);
        if (qObj != null) {
            Field fF = qObj.getClass().getDeclaredField("F");
            fF.setAccessible(true);
            fF.setInt(qObj, -1);
        }

        Method fMethod = qClass.getDeclaredMethod("F", Object[].class);
        fMethod.setAccessible(true);
        String res = (String) fMethod.invoke(null, new Object[]{ new Object[0] });
        System.out.println("Result with E=true, c=false, W=unlimited, j.F=-1, Q.F=-1:");
        System.out.println(res);
    }
}
