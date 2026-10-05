package avt;

import java.lang.reflect.Constructor;

public class InspectConstructorsOfT {
    public static void main(String[] args) throws Exception {
        Class<?> tClass = Class.forName("avt.t");
        for (Constructor<?> c : tClass.getDeclaredConstructors()) {
            System.out.println(c.toString());
        }
    }
}
