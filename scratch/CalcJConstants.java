package avt;

import java.lang.reflect.Method;

public class CalcJConstants {
    public static void main(String[] args) throws Exception {
        Class<?> jClass = Class.forName("avt.game.j");
        Method ma = jClass.getDeclaredMethod("a", int.class, long.class);
        ma.setAccessible(true);

        int sz = (int) ma.invoke(null, 5440, 5583533511159440033L);
        int maxRot = (int) ma.invoke(null, 6190, 5395755966860947417L);
        int remRot = (int) ma.invoke(null, 5685, 3660439005908787655L);
        int maxSusp = (int) ma.invoke(null, 6442, 580996880781930207L);

        System.out.println("featureWeights size: " + sz);
        System.out.println("max rotationStepDegrees: " + maxRot);
        System.out.println("rem rotationStepDegrees: " + remRot);
        System.out.println("max suspiciousRotationEnd: " + maxSusp);
    }
}
