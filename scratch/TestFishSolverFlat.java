package avt;

import org.json.simple.S;
import org.json.simple.p;
import java.lang.reflect.Method;

public class TestFishSolverFlat {
    public static String getFishSolverJson() {
        S root = new S();
        S solver = new S();
        solver.put("version", 1);
        solver.put("rotationStepDegrees", 15);
        solver.put("rotationFineStepDegrees", 1);
        solver.put("minForegroundComponentRatio", 0.05);
        solver.put("suspiciousRotationStartDegrees", 75);
        solver.put("suspiciousRotationEndDegrees", 105);
        solver.put("suspiciousRotationPenalty", 0.1);
        solver.put("foregroundAlphaThreshold", 0.5);
        solver.put("foregroundTrimRatio", 0.1);
        solver.put("mirrorSwitchMargin", 0.02);
        
        p weights = new p();
        for (int i = 0; i < 8; i++) {
            weights.add(1.0);
        }
        solver.put("featureWeights", weights);
        
        root.put("fishSolver", solver);
        return root.z(new Object[0]);
    }

    public static void main(String[] args) throws Exception {
        String json = getFishSolverJson();
        System.out.println("Generated JSON: " + json);

        Class<?> jClass = Class.forName("avt.game.j");
        Method mN = jClass.getDeclaredMethod("N", Object[].class);
        mN.setAccessible(true);
        mN.invoke(null, new Object[]{ new Object[]{ json } });

        Method ms = jClass.getDeclaredMethod("s", Object[].class);
        ms.setAccessible(true);
        boolean isReady = (boolean) ms.invoke(null, new Object[]{ new Object[0] });
        System.out.println("SUCCESS! avt.game.j.s (isReady): " + isReady);
    }
}
