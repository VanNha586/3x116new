package avt;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class PrintPacketLValue {
    public static void main(String[] args) throws Exception {
        Class<?> vClass = Class.forName("avt.network.v");
        Field fL = vClass.getDeclaredField("L");
        fL.setAccessible(true);

        Constructor<?> ctor = vClass.getDeclaredConstructor(int.class);
        ctor.setAccessible(true);

        Object vQuangCau = ctor.newInstance(23050);
        byte lQuangCau = fL.getByte(vQuangCau);
        System.out.println("L for 23050 (quangCau) = " + lQuangCau);

        Object vSr = ctor.newInstance(30908);
        byte lSr = fL.getByte(vSr);
        System.out.println("L for 30908 (batDauLuotCau) = " + lSr);

        Object vEnd = ctor.newInstance(829);
        byte lEnd = fL.getByte(vEnd);
        System.out.println("L for 829 (ketThucLuotCau) = " + lEnd);
    }
}
