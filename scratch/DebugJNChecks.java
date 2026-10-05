package avt;

import org.json.simple.S;
import org.json.simple.p;
import org.json.simple.parser.J;
import java.lang.reflect.Method;

public class DebugJNChecks {
    public static void main(String[] args) throws Exception {
        String json = TestFishSolverFlat.getFishSolverJson();
        Class<?> jClass = Class.forName("avt.game.j");
        
        Method mb = jClass.getDeclaredMethod("b", Object[].class);
        mb.setAccessible(true);
        Method mf = jClass.getDeclaredMethod("f", Object[].class);
        mf.setAccessible(true);
        Method ma = jClass.getDeclaredMethod("a", int.class, long.class);
        ma.setAccessible(true);

        J parser = new J();
        S root = (S) parser.g(new Object[]{ json });
        S solver = (S) root.get("fishSolver");

        int version = (int) mb.invoke(null, new Object[]{ new Object[]{ solver.get("version"), 0 } });
        int rotationStepDegrees = (int) mb.invoke(null, new Object[]{ new Object[]{ solver.get("rotationStepDegrees"), 0 } });
        int rotationFineStepDegrees = (int) mb.invoke(null, new Object[]{ new Object[]{ solver.get("rotationFineStepDegrees"), 0 } });
        double minForegroundComponentRatio = (double) mf.invoke(null, new Object[]{ new Object[]{ solver.get("minForegroundComponentRatio"), -1.0 } });
        int suspiciousRotationStartDegrees = (int) mb.invoke(null, new Object[]{ new Object[]{ solver.get("suspiciousRotationStartDegrees"), -1 } });
        int suspiciousRotationEndDegrees = (int) mb.invoke(null, new Object[]{ new Object[]{ solver.get("suspiciousRotationEndDegrees"), -1 } });
        double suspiciousRotationPenalty = (double) mf.invoke(null, new Object[]{ new Object[]{ solver.get("suspiciousRotationPenalty"), -1.0 } });
        double foregroundAlphaThreshold = (double) mf.invoke(null, new Object[]{ new Object[]{ solver.get("foregroundAlphaThreshold"), -1.0 } });
        double foregroundTrimRatio = (double) mf.invoke(null, new Object[]{ new Object[]{ solver.get("foregroundTrimRatio"), -1.0 } });
        double mirrorSwitchMargin = (double) mf.invoke(null, new Object[]{ new Object[]{ solver.get("mirrorSwitchMargin"), -1.0 } });

        System.out.println("version = " + version);
        System.out.println("rotationStepDegrees = " + rotationStepDegrees);
        System.out.println("rotationFineStepDegrees = " + rotationFineStepDegrees);
        System.out.println("minForegroundComponentRatio = " + minForegroundComponentRatio);
        System.out.println("suspiciousRotationStartDegrees = " + suspiciousRotationStartDegrees);
        System.out.println("suspiciousRotationEndDegrees = " + suspiciousRotationEndDegrees);
        System.out.println("suspiciousRotationPenalty = " + suspiciousRotationPenalty);
        System.out.println("foregroundAlphaThreshold = " + foregroundAlphaThreshold);
        System.out.println("foregroundTrimRatio = " + foregroundTrimRatio);
        System.out.println("mirrorSwitchMargin = " + mirrorSwitchMargin);

        // Check conditions
        int c1 = (int) ma.invoke(null, 5685, 3660439005908787655L); // 360
        int c2 = (int) ma.invoke(null, 6190, 5395755966860947417L); // 45
        int c3 = (int) ma.invoke(null, 6442, 580996880781930207L);  // 359
        int c4 = (int) ma.invoke(null, 5440, 5583533511159440033L); // 8

        System.out.println("rotationStepDegrees >= 1: " + (rotationStepDegrees >= 1));
        System.out.println("rotationStepDegrees <= c2 (" + c2 + "): " + (rotationStepDegrees <= c2));
        System.out.println("c1 (" + c1 + ") % rotationStepDegrees == 0: " + (c1 % rotationStepDegrees == 0));
        System.out.println("rotationFineStepDegrees >= 1: " + (rotationFineStepDegrees >= 1));
        System.out.println("rotationFineStepDegrees < rotationStepDegrees: " + (rotationFineStepDegrees < rotationStepDegrees));
        System.out.println("minForegroundComponentRatio >= 0.0: " + (minForegroundComponentRatio >= 0.0));
        System.out.println("minForegroundComponentRatio <= 0.5: " + (minForegroundComponentRatio <= 0.5));
        System.out.println("suspiciousRotationStartDegrees >= 0: " + (suspiciousRotationStartDegrees >= 0));
        System.out.println("suspiciousRotationStartDegrees < suspiciousRotationEndDegrees: " + (suspiciousRotationStartDegrees < suspiciousRotationEndDegrees));
        System.out.println("suspiciousRotationEndDegrees <= c3 (" + c3 + "): " + (suspiciousRotationEndDegrees <= c3));
        System.out.println("suspiciousRotationPenalty >= 0.0: " + (suspiciousRotationPenalty >= 0.0));
        System.out.println("suspiciousRotationPenalty <= 0.5: " + (suspiciousRotationPenalty <= 0.5));
        System.out.println("foregroundAlphaThreshold >= 0.0: " + (foregroundAlphaThreshold >= 0.0));
        System.out.println("foregroundAlphaThreshold <= 1.0: " + (foregroundAlphaThreshold <= 1.0));
        System.out.println("foregroundTrimRatio > 0.0: " + (foregroundTrimRatio > 0.0));
        System.out.println("foregroundTrimRatio <= 1.0: " + (foregroundTrimRatio <= 1.0));
        System.out.println("mirrorSwitchMargin >= 0.0: " + (mirrorSwitchMargin >= 0.0));
        System.out.println("mirrorSwitchMargin <= 0.25: " + (mirrorSwitchMargin <= 0.25));
    }
}
