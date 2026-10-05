package avt;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class InspectExactVAndSr {
    public static void main(String[] args) throws Exception {
        Class<?> kClass = Class.forName("avt.game.k");
        Field fab = kClass.getDeclaredField("ab");
        fab.setAccessible(true);
        String[] ab = (String[]) fab.get(null);
        System.out.println("ab[31] = " + ab[31]);
        System.out.println("ab[57] = " + ab[57]);
        System.out.println("ab[58] = " + ab[58]);
        System.out.println("ab[36] = " + ab[36]);

        for (Method m : kClass.getDeclaredMethods()) {
            if (m.getName().equals("a") && m.getParameterTypes().length == 2 && m.getParameterTypes()[0] == int.class) {
                m.setAccessible(true);
                int quangCauCmd = (Integer) m.invoke(null, 23050, 3046246223413995502L);
                System.out.println("Computed quangCauCmd = " + quangCauCmd);
                int srCmd = (Integer) m.invoke(null, 30908, 7601524680053114248L);
                System.out.println("Computed srCmd = " + srCmd);
            }
        }
    }
}
