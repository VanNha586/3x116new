package avt;

import org.json.simple.S;
import org.json.simple.p;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BypassHelper {
    private static Field fE;
    private static Field fc;
    private static Field fW;
    private static Field fG;
    private static Field fj;
    private static Field fQ;
    private static Field fD;
    private static Field fw;
    private static Field fM;
    private static Field fs;
    private static Field fa;
    private static Field fJ;
    private static Field fo;
    private static Field fF;
    private static Method mp;
    private static Method mJN;
    private static Method mQI;
    private static boolean initialized = false;

    // Fallback delay: doSr() thực tế chạy ở ~15s sau catch → cần > 15s để luôn cancel được.
    // 30s an toàn cho mọi loại cá, MISS case sẽ tự fire sau 30s.
    private static final long CAST_FALLBACK_DELAY_MS = 30_000L;
    private static final long CAST_THROTTLE_MS      =  5_000L;

    private static final Map<Integer, Object>           activeBotsByIndex = new ConcurrentHashMap<Integer, Object>();
    private static final Map<Integer, Integer>          fishCountMap      = new ConcurrentHashMap<Integer, Integer>();
    private static final Map<Integer, String>           missionDetailMap  = new ConcurrentHashMap<Integer, String>();
    private static volatile Object                      lastActiveBot     = null;

    private static final Map<Object, Long>              lastQuangCauMap = new ConcurrentHashMap<Object, Long>();
    private static final Map<Object, Long>              lastSrMap       = new ConcurrentHashMap<Object, Long>();
    private static final Map<Object, ScheduledFuture<?>> pendingCastMap  = new ConcurrentHashMap<Object, ScheduledFuture<?>>();
    private static final ScheduledExecutorService castScheduler = Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "FishCastScheduler");
            t.setDaemon(true);
            return t;
        }
    });

    public static void registerActiveBot(Object bot) {
        if (bot == null) return;
        lastActiveBot = bot;
        try {
            Field fh = bot.getClass().getDeclaredField("hh");
            fh.setAccessible(true);
            int idx = fh.getInt(bot);
            if (idx >= 0) {
                activeBotsByIndex.put(Integer.valueOf(idx), bot);
            } else {
                activeBotsByIndex.put(Integer.valueOf(0), bot);
            }
        } catch (Throwable t) {
            activeBotsByIndex.put(Integer.valueOf(0), bot);
        }
    }

    public static void onBotLog(Object bot, Object[] args) {
        if (bot == null || isBotStopped(bot)) return;
        registerActiveBot(bot);
        if (args != null && args.length > 0 && args[0] != null) {
            String msg = args[0].toString();
            try {
                Field fh = bot.getClass().getDeclaredField("hh");
                fh.setAccessible(true);
                int idx = fh.getInt(bot);
                if (idx < 0) idx = 0;

                // Cập nhật số cá câu được
                if (msg.contains("Câu thành công") || msg.contains("Đã câu được một con")) {
                    Integer cur = fishCountMap.get(Integer.valueOf(idx));
                    int newCount = (cur != null ? cur.intValue() : 0) + 1;
                    fishCountMap.put(Integer.valueOf(idx), Integer.valueOf(newCount));
                }

                // Cập nhật thông tin chi tiết nhiệm vụ thợ câu
                if (msg.contains("Nhiệm vụ hiện tại của bạn là nhiệm vụ:") || msg.contains("Nhiệm Vụ Thợ Câu")) {
                    missionDetailMap.put(Integer.valueOf(idx), msg.trim());
                }
            } catch (Throwable t) {}
        }
    }

    public static void cancelPendingCast(Object bot) {
        ScheduledFuture<?> pending = pendingCastMap.remove(bot);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    private static boolean isBotStopped(Object bot) {
        if (bot == null) return true;
        try {
            Field stoppingFlag = bot.getClass().getDeclaredField("hl");
            stoppingFlag.setAccessible(true);
            return stoppingFlag.getBoolean(bot);
        } catch (Throwable t) {
            // Fail closed: if the stop state cannot be read, do not queue more work.
            return true;
        }
    }

    // Gửi thật sự packet 41 (quangCau)
    private static void sendQuangCauNow(final Object bot) {
        try {
            if (bot == null || isBotStopped(bot)) {
                cancelPendingCast(bot);
                return;
            }
            cancelPendingCast(bot);
            lastQuangCauMap.put(bot, System.currentTimeMillis());

            Method mo = bot.getClass().getDeclaredMethod("o", Object[].class);
            mo.setAccessible(true);
            mo.invoke(bot, new Object[]{ new Object[]{ "Gửi lệnh quăng câu" } });

            Class<?> vClass = Class.forName("avt.network.v");
            Constructor<?> ctor = vClass.getDeclaredConstructor(int.class);
            ctor.setAccessible(true);
            Object msg = ctor.newInstance(41);

            Method mg = bot.getClass().getDeclaredMethod("g", Object[].class);
            mg.setAccessible(true);
            mg.invoke(bot, new Object[]{ new Object[]{ msg } });
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void doQuangCau(final Object bot) {
        if (bot == null || isBotStopped(bot)) return;
        registerActiveBot(bot);

        // Sau khi câu xong (thành công hoặc trật), bot gọi k.V để quăng cần lượt tiếp.
        // Đợi 1.0s rồi tự động bắt đầu lượt câu mới 45 -> 41.
        castScheduler.schedule(new Runnable() {
            public void run() {
                try {
                    if (!isBotStopped(bot)) doSr(bot);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }, 1000L, TimeUnit.MILLISECONDS);
    }

    public static void doSr(final Object bot) {
        try {
            if (bot == null || isBotStopped(bot)) return;
            registerActiveBot(bot);

            long now = System.currentTimeMillis();
            Long last = lastSrMap.get(bot);
            // Throttle 2000ms chống gọi trùng lặp
            if (last != null && (now - last.longValue() < 2000L)) {
                return;
            }
            lastSrMap.put(bot, now);

            // Hủy timeout cũ (nếu có)
            cancelPendingCast(bot);

            if (isBotStopped(bot)) return;

            // 1. Log "Bắt đầu lượt câu mới"
            Method mo = bot.getClass().getDeclaredMethod("o", Object[].class);
            mo.setAccessible(true);
            mo.invoke(bot, new Object[]{ new Object[]{ "Bắt đầu lượt câu mới" } });
            if (isBotStopped(bot)) return;

            // 2. Chọn mồi câu
            Method mk = bot.getClass().getDeclaredMethod("k", Object[].class);
            mk.setAccessible(true);
            mk.invoke(bot, new Object[]{ new Object[]{ Integer.valueOf(2) } });

            // 3. Gửi batDauLuotCau (45)
            Class<?> vClass = Class.forName("avt.network.v");
            Constructor<?> ctor = vClass.getDeclaredConstructor(int.class);
            ctor.setAccessible(true);
            Object msg45 = ctor.newInstance(45);
            if (isBotStopped(bot)) return;
            Method mg = bot.getClass().getDeclaredMethod("g", Object[].class);
            mg.setAccessible(true);
            mg.invoke(bot, new Object[]{ new Object[]{ msg45 } });
            if (isBotStopped(bot)) return;

            // 4. Delay ngắn 200ms rồi gửi quangCau (41)
            castScheduler.schedule(new Runnable() {
                public void run() {
                    sendQuangCauNow(bot);
                }
            }, 200L, TimeUnit.MILLISECONDS);

            // 5. Watchdog timeout 30s: tự động quăng lại nếu cá không cắn câu
            ScheduledFuture<?> watchdog = castScheduler.schedule(new Runnable() {
                public void run() {
                    try {
                        cancelPendingCast(bot);
                        // The game bot sets `hl` when its Stop handler runs. `w` is
                        // an unrelated field and never reflects the stopped state.
                        Field stoppingFlag = bot.getClass().getDeclaredField("hl");
                        stoppingFlag.setAccessible(true);
                        if (!stoppingFlag.getBoolean(bot)) {
                            doSr(bot);
                        }
                    } catch (Throwable t) {}
                }
            }, 30000L, TimeUnit.MILLISECONDS);
            pendingCastMap.put(bot, watchdog);

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void enqueuePacket(Object session, Object message) {
        try {
            if (message == null) return;

            Class<?> xClass = session.getClass();
            Field fM = xClass.getDeclaredField("M");
            fM.setAccessible(true);
            Object lock = fM.get(session);

            Field fL = xClass.getDeclaredField("L");
            fL.setAccessible(true);
            boolean isL = fL.getBoolean(session);

            Field fr = xClass.getDeclaredField("r");
            fr.setAccessible(true);
            boolean isR = fr.getBoolean(session);

            synchronized (lock) {
                if (isL || isR) {
                    Field fq = xClass.getDeclaredField("q");
                    fq.setAccessible(true);
                    Object queue = fq.get(session);
                    Method mu = queue.getClass().getDeclaredMethod("u", Object[].class);
                    mu.setAccessible(true);
                    mu.invoke(queue, new Object[]{ new Object[]{ message } });
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void handleDisconnected(Object listener) {
        try {
            Class<?> oClass = listener.getClass();
            Field fr = oClass.getDeclaredField("r");
            fr.setAccessible(true);
            Object bot = fr.get(listener);

            // Dọn sạch timer cũ khi ngắt kết nối để không bắn vào session mới
            cancelPendingCast(bot);

            // avt.game.k's Stop handler sets `hl` before closing the session.
            Field stoppingFlag = bot.getClass().getDeclaredField("hl");
            stoppingFlag.setAccessible(true);
            boolean isStopping = stoppingFlag.getBoolean(bot);
            if (isStopping) {
                Method ms = bot.getClass().getDeclaredMethod("S", Object[].class);
                ms.setAccessible(true);
                ms.invoke(bot, new Object[]{ new Object[0] });
                return;
            }

            // Auto reconnect!
            Method mo = bot.getClass().getDeclaredMethod("o", Object[].class);
            mo.setAccessible(true);
            mo.invoke(bot, new Object[]{ new Object[]{ "Mất kết nối server game, đang tự động kết nối lại..." } });

            Method mj = oClass.getDeclaredMethod("j", Object[].class);
            mj.setAccessible(true);
            mj.invoke(listener, new Object[]{ new Object[0] });
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static String getFishSolverJson() {
        S root = new S();
        S solver = new S();
        solver.put("version", 2);
        solver.put("rotationStepDegrees", 15);
        solver.put("rotationFineStepDegrees", 1);
        solver.put("mirrorSwitchMargin", 0.02);
        solver.put("suspiciousRotationStartDegrees", 75);
        solver.put("suspiciousRotationEndDegrees", 105);
        solver.put("suspiciousRotationPenalty", 0.1);
        solver.put("foregroundAlphaThreshold", 0.5);
        solver.put("minForegroundComponentRatio", 0.05);
        solver.put("foregroundTrimRatio", 0.1);
        
        p weights = new p();
        for (int i = 0; i < 8; i++) {
            weights.add(1.0);
        }
        solver.put("featureWeights", weights);
        
        root.put("fishSolver", solver);
        return root.z(new Object[0]);
    }

    private static synchronized void init() {
        if (initialized) return;
        try {
            Class<?> qClass = Class.forName("avt.Q");
            try { fE = qClass.getDeclaredField("E"); fE.setAccessible(true); } catch (Throwable t) {}
            try { fc = qClass.getDeclaredField("c"); fc.setAccessible(true); } catch (Throwable t) {}
            try { fW = qClass.getDeclaredField("W"); fW.setAccessible(true); } catch (Throwable t) {}
            try { fG = qClass.getDeclaredField("G"); fG.setAccessible(true); } catch (Throwable t) {}
            try { fj = qClass.getDeclaredField("j"); fj.setAccessible(true); } catch (Throwable t) {}
            try { fQ = qClass.getDeclaredField("Q"); fQ.setAccessible(true); } catch (Throwable t) {}
            try { fD = qClass.getDeclaredField("D"); fD.setAccessible(true); } catch (Throwable t) {}
            try { fw = qClass.getDeclaredField("w"); fw.setAccessible(true); } catch (Throwable t) {}
            try { fM = qClass.getDeclaredField("m"); fM.setAccessible(true); } catch (Throwable t) {}
            try { fs = qClass.getDeclaredField("s"); fs.setAccessible(true); } catch (Throwable t) {}
            try { fa = qClass.getDeclaredField("a"); fa.setAccessible(true); } catch (Throwable t) {}
            try { fJ = qClass.getDeclaredField("J"); fJ.setAccessible(true); } catch (Throwable t) {}
            try { fo = qClass.getDeclaredField("o"); fo.setAccessible(true); } catch (Throwable t) {}

            try {
                Class<?> wClass = Class.forName("avt.W");
                fF = wClass.getDeclaredField("F");
                fF.setAccessible(true);
            } catch (Throwable t) {}

            for (Method m : qClass.getDeclaredMethods()) {
                if (m.getName().equals("p") && String.class.isAssignableFrom(m.getReturnType())) {
                    mp = m;
                    mp.setAccessible(true);
                }
                if (m.getName().equals("_I") && m.getParameterTypes().length == 1) {
                    mQI = m;
                    mQI.setAccessible(true);
                }
            }

            try {
                Class<?> jClass = Class.forName("avt.game.j");
                for (Method m : jClass.getDeclaredMethods()) {
                    if (m.getName().equals("N") && m.getParameterTypes().length == 1) {
                        mJN = m;
                        mJN.setAccessible(true);
                        break;
                    }
                }
            } catch (Throwable t) {}

            initialized = true;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void apply() {
        init();
        try {
            if (fE != null) fE.setBoolean(null, true);
            if (fc != null) fc.setBoolean(null, true);
            if (fW != null) fW.set(null, "Không giới hạn (Vĩnh viễn)");
            if (fJ != null) fJ.setInt(null, -1);

            List<String> allModes = Arrays.asList("fishTool", "fishermanTool", "farmer", "combinedTool");
            if (fG != null) {
                List list = (List) fG.get(null);
                if (list != null) {
                    list.clear();
                    list.addAll(allModes);
                }
            }

            if (fj != null && fF != null) {
                Object jObj = fj.get(null);
                if (jObj != null) fF.setInt(jObj, -1);
            }
            if (fQ != null && fF != null) {
                Object qObj = fQ.get(null);
                if (qObj != null) fF.setInt(qObj, -1);
            }
            if (fD != null && fF != null) {
                Map map = (Map) fD.get(null);
                if (map != null) {
                    for (Object val : map.values()) {
                        if (val != null) fF.setInt(val, -1);
                    }
                }
            }

            // Apply fish solver config
            String fishSolverJson = getFishSolverJson();
            if (mQI != null) {
                try {
                    mQI.invoke(null, new Object[]{ new Object[]{ fishSolverJson } });
                } catch (Throwable t) {}
            }
            if (mJN != null) {
                try {
                    mJN.invoke(null, new Object[]{ new Object[]{ fishSolverJson } });
                } catch (Throwable t) {}
            }

            if (fs != null) {
                S sessionObj = new S();
                sessionObj.put("status", "ok");
                sessionObj.put("message", "");
                sessionObj.put("activated", true);
                sessionObj.put("canUseTool", true);
                sessionObj.put("accountLimit", -1);
                sessionObj.put("allowedModes", allModes);
                sessionObj.put("timeRemaining", "Không giới hạn (Vĩnh viễn)");
                sessionObj.put("role", "user");
                sessionObj.put("canManage", true);
                sessionObj.put("licenseValid", true);
                sessionObj.put("localDbSecurity", true);
                fs.set(null, sessionObj);
            }

            if (fa != null) {
                S authObj = new S();
                authObj.put("status", "ok");
                authObj.put("message", "");
                authObj.put("activated", true);
                authObj.put("canUseTool", true);
                authObj.put("accountLimit", -1);
                authObj.put("allowedModes", allModes);
                authObj.put("role", "user");
                authObj.put("timeRemaining", "Không giới hạn (Vĩnh viễn)");
                fa.set(null, authObj);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static Object createMockT() {
        try {
            Class<?> tClass = Class.forName("avt.t");
            Constructor<?> ctor = null;
            for (Constructor<?> c : tClass.getDeclaredConstructors()) {
                if (c.getParameterTypes().length == 11) {
                    ctor = c;
                    break;
                }
            }
            ctor.setAccessible(true);

            String key = "bypass_key";
            int accountId = 0;
            int accountLimit = -1;
            String fishSolverJson = getFishSolverJson();
            String role = "user";
            String timeRemaining = "Không giới hạn (Vĩnh viễn)";
            List accounts = new ArrayList();
            S management = new S();
            List allowedModes = Arrays.asList("fishTool", "fishermanTool", "farmer", "combinedTool");
            S updateInfo = new S();
            updateInfo.put("hasUpdate", false);
            updateInfo.put("forceUpdate", false);
            updateInfo.put("currentVersion", "1.1.6");
            updateInfo.put("message", "Avatar 3x Tool - DebugToDeath v1.1.6");

            apply();

            return ctor.newInstance(key, accountId, accountLimit, fishSolverJson, role, timeRemaining, accounts, management, allowedModes, updateInfo, null);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    public static S createMockR() {
        S s = new S();
        s.put("ok", true);
        s.put("status", "ok");
        s.put("code", "ok");
        s.put("message", "Thành công");
        s.put("action", "allow");
        s.put("data", new S());
        return s;
    }

    public static S buildInfoFromBot(Object bot, Object acc) {
        S info = new S();
        if (bot == null) return info;
        try {
            Class<?> kClass = bot.getClass();
            
            // 1. Tên nhân vật (character)
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
            if (charName.isEmpty() && acc != null) {
                try {
                    Field fn = acc.getClass().getDeclaredField("N"); fn.setAccessible(true);
                    Object val = fn.get(acc);
                    if (val != null) charName = val.toString();
                } catch (Throwable t) {}
            }
            info.put("character", charName);

            // 2. Level & levelPercent (lấy từ bot.m.J và bot.m.X của avt.game.N)
            int level = -1;
            int levelPercent = -1;
            try {
                Field fm = kClass.getDeclaredField("m"); fm.setAccessible(true);
                Object nObj = fm.get(bot);
                if (nObj != null) {
                    Class<?> nClass = nObj.getClass();
                    Field fJ = nClass.getDeclaredField("J"); fJ.setAccessible(true);
                    level = fJ.getInt(nObj);
                    Field fX = nClass.getDeclaredField("X"); fX.setAccessible(true);
                    levelPercent = fX.getInt(nObj);
                }
            } catch (Throwable t) {}

            // Fallback đọc từ log bot.z nếu có dòng "Cấp độ chính: 38 +95%"
            try {
                Field fz = kClass.getDeclaredField("z"); fz.setAccessible(true);
                Object zVal = fz.get(bot);
                if (zVal != null) {
                    String log = zVal.toString();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("C[^\n]*?p[^\n]*?(\\d+)\\s*\\+(\\d+)%").matcher(log);
                    while (m.find()) {
                        level = Integer.parseInt(m.group(1));
                        levelPercent = Integer.parseInt(m.group(2));
                    }
                }
            } catch (Throwable t) {}

            if (level > 0) {
                info.put("level", level);
                info.put("levelPercent", levelPercent >= 0 ? levelPercent : 0);
            }

            // 3. Xu, Lượng, Lượng khóa
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

            // 4. Cá câu được & giới hạn (Mục tiêu 550 con: ví dụ 1/550, 2/550, ...)
            int fishCount = 0;
            int fishLimit = 550;
            int botIdx = 0;
            try {
                Field fhh = kClass.getDeclaredField("hh"); fhh.setAccessible(true);
                botIdx = fhh.getInt(bot);
                if (botIdx < 0) botIdx = 0;
            } catch (Throwable t) {}

            Integer trackedCount = fishCountMap.get(Integer.valueOf(botIdx));
            if (trackedCount != null && trackedCount.intValue() > 0) {
                fishCount = trackedCount.intValue();
            } else {
                // Đếm số lần câu thành công từ log bot.z nếu có
                try {
                    Field fz = kClass.getDeclaredField("z"); fz.setAccessible(true);
                    Object zVal = fz.get(bot);
                    if (zVal != null) {
                        String log = zVal.toString();
                        int c = 0;
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("C[^\n]*?u th[^\n]*?nh c[^\n]*?ng|con C[^\n]*? - Tr[^\n]*? gi").matcher(log);
                        while (m.find()) {
                            c++;
                        }
                        if (c > 0) {
                            fishCount = c;
                            fishCountMap.put(Integer.valueOf(botIdx), Integer.valueOf(fishCount));
                        }
                    }
                } catch (Throwable t) {}
            }
            info.put("dailyFishCount", fishCount);
            info.put("dailyFishLimit", fishLimit);

            // 5. Thời gian về farm (farmReturnAt)
            long farmReturnAt = 0L;
            try {
                Field fn = kClass.getDeclaredField("n"); fn.setAccessible(true);
                farmReturnAt = fn.getLong(bot);
            } catch (Throwable t) {}
            info.put("farmReturnAt", farmReturnAt);

            // 6. Nhiệm vụ hiện tại (currentMission)
            String currentMission = "-";
            String savedMission = missionDetailMap.get(Integer.valueOf(botIdx));
            if (savedMission != null && !savedMission.trim().isEmpty() && !savedMission.equals("3.2.2")) {
                currentMission = savedMission.trim();
            } else {
                // Trích xuất từ toàn bộ log bot.z
                try {
                    Field fz = kClass.getDeclaredField("z"); fz.setAccessible(true);
                    Object zVal = fz.get(bot);
                    if (zVal != null) {
                        String log = zVal.toString();
                        int idx = log.indexOf("Nhiệm vụ hiện tại của bạn là");
                        if (idx != -1) {
                            int endIdx = log.indexOf("Câu cá nhiệm vụ", idx);
                            if (endIdx == -1) endIdx = log.indexOf("# Thực hiện:", idx + 30);
                            if (endIdx != -1 && endIdx > idx) {
                                currentMission = log.substring(idx, endIdx).trim();
                            } else {
                                currentMission = log.substring(idx, Math.min(log.length(), idx + 500)).trim();
                            }
                            missionDetailMap.put(Integer.valueOf(botIdx), currentMission);
                        }
                    }
                } catch (Throwable t) {}
            }

            if (currentMission == null || currentMission.equals("-") || currentMission.equals("3.2.2") || currentMission.trim().isEmpty()) {
                currentMission = "Chưa có thông tin nhiệm vụ (sẽ tự động cập nhật khi bot tải nhiệm vụ)";
            }
            info.put("currentMission", currentMission);

        } catch (Throwable t) {
            t.printStackTrace();
        }
        return info;
    }

    public static String getStateJson(Object[] args) {
        apply();
        try {
            S root = new S();
            root.put("status", "ok");
            root.put("message", "");
            root.put("activated", true);
            root.put("canUseTool", true);
            root.put("accountLimit", -1);
            root.put("allowedModes", Arrays.asList("fishTool", "fishermanTool", "farmer", "combinedTool"));
            root.put("timeRemaining", "Không giới hạn (Vĩnh viễn)");
            root.put("role", "user");
            root.put("canManage", true);
            root.put("licenseValid", true);
            root.put("localDbSecurity", true);

            int selectedIndex = 0;
            if (fw != null) {
                try { selectedIndex = fw.getInt(null); } catch (Throwable t) {}
            }
            root.put("selectedIndex", selectedIndex);

            List accountsList = null;
            if (fM != null) {
                try { accountsList = (List) fM.get(null); } catch (Throwable t) {}
            }

            String selectedAccount = "-";
            if (mp != null) {
                try {
                    Object res = mp.invoke(null, new Object[]{ new Object[0] });
                    if (res != null) selectedAccount = res.toString();
                } catch (Throwable t) {}
            }
            if ((selectedAccount == null || selectedAccount.equals("-") || selectedAccount.trim().isEmpty()) 
                    && accountsList != null && selectedIndex >= 0 && selectedIndex < accountsList.size()) {
                try {
                    Object acc = accountsList.get(selectedIndex);
                    Field fnField = acc.getClass().getDeclaredField("N");
                    fnField.setAccessible(true);
                    Object uName = fnField.get(acc);
                    if (uName != null) selectedAccount = uName.toString();
                } catch (Throwable t) {}
            }
            root.put("selectedAccount", selectedAccount);

            Object[] bots = null;
            if (fo != null) {
                try { bots = (Object[]) fo.get(null); } catch (Throwable t) {}
            }
            
            ArrayList<S> jsonAccounts = new ArrayList<S>();
            if (accountsList != null) {
                for (int i = 0; i < accountsList.size(); i++) {
                    Object acc = accountsList.get(i);
                    try {
                        Object bot = (bots != null && i < bots.length && bots[i] != null) ? bots[i] : null;
                        if (bot == null) {
                            bot = activeBotsByIndex.get(Integer.valueOf(i));
                        }
                        if (bot == null && lastActiveBot != null && (accountsList.size() == 1 || i == selectedIndex)) {
                            bot = lastActiveBot;
                        }
                        
                        Method ma = acc.getClass().getDeclaredMethod("a", Object[].class);
                        ma.setAccessible(true);
                        S jsonAcc = (S) ma.invoke(acc, new Object[]{ new Object[]{ Integer.valueOf(i) } });
                        
                        boolean isRunning = false;
                        if (bot != null) {
                            isRunning = true;
                            try {
                                // Read the flag used by avt.Q's Stop path, not the
                                // unrelated `w` field (which remains false).
                                Field stoppingFlag = bot.getClass().getDeclaredField("hl");
                                stoppingFlag.setAccessible(true);
                                boolean isStopping = stoppingFlag.getBoolean(bot);
                                if (isStopping) isRunning = false;
                            } catch (Throwable t) {}
                            
                            // Bổ sung/cập nhật info chi tiết từ bot cho UI
                            S info = buildInfoFromBot(bot, acc);
                            jsonAcc.put("info", info);
                        } else {
                            if (jsonAcc.get("info") == null) {
                                jsonAcc.put("info", new S());
                            }
                        }
                        
                        jsonAcc.put("isRunning", isRunning);
                        jsonAcc.put("running", isRunning);
                        jsonAccounts.add(jsonAcc);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
            root.put("accounts", jsonAccounts);
            root.put("proxies", new ArrayList<Object>());

            S updateObj = new S();
            updateObj.put("hasUpdate", false);
            updateObj.put("currentVersion", "1.1.6");
            updateObj.put("message", "Avatar 3x Tool - DebugToDeath v1.1.6");
            updateObj.put("forceUpdate", false);
            updateObj.put("downloadUrl", "https://raw.githubusercontent.com/nhaxit/avatar3x/main/avatar3x-1.1.6.jar");
            root.put("updateInfo", updateObj);

            return root.z(new Object[0]);
        } catch (Throwable t) {
            t.printStackTrace();
            return "{\"status\":\"ok\",\"activated\":true,\"canUseTool\":true,\"accountLimit\":-1,\"allowedModes\":[\"fishTool\",\"fishermanTool\",\"farmer\",\"combinedTool\"],\"role\":\"user\",\"timeRemaining\":\"Không giới hạn (Vĩnh viễn)\",\"accounts\":[],\"proxies\":[]}";
        }
    }
}
