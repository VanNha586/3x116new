package avt;

import java.lang.reflect.Method;

public class InspectNetworkClasses {
    public static void main(String[] args) throws Exception {
        String[] classes = {"avt.network.b", "avt.network.f", "avt.network.j", "avt.network.K", "avt.network.m", "avt.network.R", "avt.network.T", "avt.network.v", "avt.network.x"};
        for (String cname : classes) {
            Class<?> c = Class.forName(cname);
            System.out.println("=== Class " + cname + " ===");
            for (Method m : c.getDeclaredMethods()) {
                System.out.println("  " + m.getName() + " " + java.util.Arrays.toString(m.getParameterTypes()));
            }
        }
    }
}
