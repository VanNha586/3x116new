package avt;

import org.json.simple.S;
import org.json.simple.p;
import java.lang.reflect.Method;
import java.util.Arrays;

public class TestFishSolverConfig {
    public static String getFishSolverJson() {
        S root = new S();
        S solver = new S();
        solver.put("version", "STRUCTURAL_CACHELESS_V4");
        
        S data = new S();
        data.put("rotationStepDegrees", 15);
        data.put("rotationFineStepDegrees", 1);
        data.put("minForegroundComponentRatio", 0.05);
        data.put("suspiciousRotationStartDegrees", 75);
        data.put("suspiciousRotationEndDegrees", 105);
        data.put("suspiciousRotationPenalty", 0.1);
        data.put("foregroundAlphaThreshold", 0.5);
        data.put("foregroundTrimRatio", 0.1);
        data.put("mirrorSwitchMargin", 0.02);
        
        p weights = new p();
        for (int i = 0; i < 8; i++) {
            weights.add(1.0);
        }
        data.put("featureWeights", weights);
        
        solver.put("data", data);
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
        System.out.println("avt.game.j.s (isReady): " + isReady);
    }
}
