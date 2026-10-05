package avt;

import java.io.*;
import java.lang.reflect.*;
import org.json.simple.S;

public class TestBuildInfoFromBot {
    public static S buildInfo(Object bot) {
        S info = new S();
        if (bot == null) return info;
        try {
            Class<?> kClass = bot.getClass();
            
            // Character name
            String charName = "";
            try {
                Field fh3 = kClass.getDeclaredField("h3"); fh3.setAccessible(true);
                Object val = fh3.get(bot);
                if (val != null && !val.toString().trim().isEmpty()) charName = val.toString().trim();
            } catch (Throwable t) {}
            if (charName.isEmpty()) {
                try {
                    Field fhP = kClass.getDeclaredField("hP"); fhP.setAccessible(true);
                    Object val = fhP.get(bot);
                    if (val != null && !val.toString().trim().isEmpty()) charName = val.toString().trim();
                } catch (Throwable t) {}
            }
            info.put("character", charName);

            // Level & percent
            int level = -1;
            int levelPercent = -1;
            try {
                Field fh = kClass.getDeclaredField("h"); fh.setAccessible(true);
                level = fh.getInt(bot);
            } catch (Throwable t) {}
            try {
                Field fM = kClass.getDeclaredField("M"); fM.setAccessible(true);
                levelPercent = fM.getInt(bot);
            } catch (Throwable t) {}
            info.put("level", level);
            info.put("levelPercent", levelPercent);

            // Xu, Luong, LuongKhoa
            int xu = 0;
            int luong = 0;
            int luongKhoa = 0;
            try {
                Field fa = kClass.getDeclaredField("a"); fa.setAccessible(true);
                xu = fa.getInt(bot);
            } catch (Throwable t) {}
            try {
                Field fhW = kClass.getDeclaredField("hW"); fhW.setAccessible(true);
                luong = fhW.getInt(bot);
            } catch (Throwable t) {}
            try {
                Field fhK = kClass.getDeclaredField("hK"); fhK.setAccessible(true);
                luongKhoa = fhK.getInt(bot);
            } catch (Throwable t) {}
            info.put("xu", String.format("%,d", (long) xu));
            info.put("luong", String.format("%,d", (long) luong));
            info.put("luongKhoa", String.format("%,d", (long) luongKhoa));

            // Fish count
            int fishCount = 0;
            int fishLimit = 200;
            try {
                Field fb = kClass.getDeclaredField("b"); fb.setAccessible(true);
                fishCount = fb.getInt(bot);
            } catch (Throwable t) {}
            try {
                Field fC = kClass.getDeclaredField("C"); fC.setAccessible(true);
                fishLimit = fC.getInt(bot);
            } catch (Throwable t) {}
            info.put("dailyFishCount", fishCount);
            info.put("dailyFishLimit", fishLimit);

            // Farm return at
            long farmReturnAt = 0L;
            try {
                Field fn = kClass.getDeclaredField("n"); fn.setAccessible(true);
                farmReturnAt = fn.getLong(bot);
            } catch (Throwable t) {}
            info.put("farmReturnAt", farmReturnAt);

            // Current mission
            String currentMission = "-";
            try {
                Field fA = kClass.getDeclaredField("A"); fA.setAccessible(true);
                Object val = fA.get(bot);
                if (val != null && !val.toString().trim().isEmpty()) currentMission = val.toString().trim();
            } catch (Throwable t) {}
            if (currentMission.equals("-")) {
                try {
                    Field fI = kClass.getDeclaredField("I"); fI.setAccessible(true);
                    Object val = fI.get(bot);
                    if (val != null && !val.toString().trim().isEmpty()) currentMission = val.toString().trim();
                } catch (Throwable t) {}
            }
            info.put("currentMission", currentMission);

        } catch (Throwable t) {
            t.printStackTrace();
        }
        return info;
    }

    public static void main(String[] args) {
        System.out.println("Test build info class OK");
    }
}
