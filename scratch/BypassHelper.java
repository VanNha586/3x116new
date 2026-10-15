package avt;

import org.json.simple.S;
import org.json.simple.p;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BypassHelper implements Runnable {
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
    private static Field fO;
    private static Field fl;
    private static Field fF;
    private static Method mp;
    private static Method mJN;
    private static Method mQI;
    private static Method mSaveClientAccounts;
    private static Method mv5;
    private static boolean initialized = false;
    private static volatile boolean shutdownHookRegistered = false;

    // Đường dẫn file lưu trữ trạng thái giữa các lần mở tool
    // Tool chạy từ thư mục app/ nên dùng đường dẫn tương đối data/bot-state.json
    private static final String STATE_FILE = "data/bot-state.json";

    // Fallback delay: doSr() thực tế chạy ở ~15s sau catch → cần > 15s để luôn cancel được.
    // 30s an toàn cho mọi loại cá, MISS case sẽ tự fire sau 30s.
    private static final long CAST_FALLBACK_DELAY_MS = 30_000L;
    private static final long CAST_THROTTLE_MS      =  5_000L;

    private static final Map<Integer, Object>           activeBotsByIndex = new ConcurrentHashMap<Integer, Object>();
    private static final Map<Integer, Integer>          fishCountMap      = new ConcurrentHashMap<Integer, Integer>();
    private static final Map<Integer, String>           missionDetailMap  = new ConcurrentHashMap<Integer, String>();
    private static final Map<Integer, Map<String, Object>> accountInfoMap = new ConcurrentHashMap<Integer, Map<String, Object>>();
    private static final Map<Integer, Long>             lastFishIncrementTime = new ConcurrentHashMap<Integer, Long>();
    private static volatile Object                      lastActiveBot     = null;

    // Tập hợp các bot đã được đánh dấu dừng để chặn log sau khi stop
    private static final Set<Integer> stoppedBotIndices = ConcurrentHashMap.newKeySet();

    // Ngày hiện tại được ghi nhận để kiểm tra reset số cá theo ngày (Asia/Ho_Chi_Minh)
    private static volatile String                      lastStateDate     = null;
    private static volatile boolean                     stateRestored     = false;
    private static final java.time.ZoneId               ZONE_VN           = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

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

    public static String getTodayDate() {
        try {
            return java.time.LocalDate.now(ZONE_VN).toString();
        } catch (Throwable t) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            return sdf.format(new java.util.Date());
        }
    }

    public static synchronized void checkDailyReset() {
        if (!stateRestored) {
            restoreState();
        }
        String today = getTodayDate();
        if (lastStateDate != null && !today.equals(lastStateDate)) {
            fishCountMap.clear();
            for (Integer idx : activeBotsByIndex.keySet()) {
                fishCountMap.put(idx, Integer.valueOf(0));
            }
            lastStateDate = today;
            persistState();
        } else if (lastStateDate == null) {
            lastStateDate = today;
        }
    }

    // Helper methods cho test & quản trị
    public static Map<Integer, Integer> getFishCountMap() {
        return fishCountMap;
    }

    public static void setLastStateDateForTest(String date) {
        lastStateDate = date;
    }

    public static String getLastStateDate() {
        return lastStateDate;
    }

    public static Map<Integer, String> getMissionDetailMap() {
        return missionDetailMap;
    }

    public static Map<Integer, Map<String, Object>> getAccountInfoMap() {
        return accountInfoMap;
    }

    public static void restoreStatePublic() {
        restoreState();
    }

    public static void persistStatePublic() {
        persistState();
    }

    // Thực thi tác vụ định kỳ mỗi phút kiểm tra chuyển ngày (Asia/Ho_Chi_Minh)
    @Override
    public void run() {
        try {
            checkDailyReset();
        } catch (Throwable ignored) {}
    }

    // Tự động lưu state khi JVM thoát và lên lịch kiểm tra reset ngày mỗi phút
    static {
        Runtime.getRuntime().addShutdownHook(new Thread("BypassHelper-StateWriter") {
            public void run() {
                persistState();
            }
        });
        restoreState();
        castScheduler.scheduleAtFixedRate(new BypassHelper(), 1, 1, TimeUnit.MINUTES);
    }

    public static void registerActiveBot(Object bot) {
        if (bot == null) return;
        lastActiveBot = bot;
        try {
            Field fh = bot.getClass().getDeclaredField("hh");
            fh.setAccessible(true);
            int idx = fh.getInt(bot);
            if (idx >= 0) {
                activeBotsByIndex.put(Integer.valueOf(idx), bot);
                // Bot mới đăng ký cho index này → xóa trạng thái dừng cũ
                stoppedBotIndices.remove(Integer.valueOf(idx));
            } else {
                activeBotsByIndex.put(Integer.valueOf(0), bot);
                stoppedBotIndices.remove(Integer.valueOf(0));
            }
        } catch (Throwable t) {
            activeBotsByIndex.put(Integer.valueOf(0), bot);
            stoppedBotIndices.remove(Integer.valueOf(0));
        }
    }

    public static void onBotLog(Object bot, Object[] args) {
        if (bot == null || isBotStopped(bot)) return;
        checkDailyReset();
        // Kiểm tra thêm stoppedBotIndices để chặn log đã được enqueue từ trước lệnh stop
        try {
            Field fhCheck = bot.getClass().getDeclaredField("hh");
            fhCheck.setAccessible(true);
            int idxCheck = fhCheck.getInt(bot);
            if (idxCheck < 0) idxCheck = 0;
            if (stoppedBotIndices.contains(Integer.valueOf(idxCheck))) return;
        } catch (Throwable ignored) {}

        registerActiveBot(bot);
        if (args != null && args.length > 0 && args[0] != null) {
            String msg = args[0].toString();
            try {
                Field fh = bot.getClass().getDeclaredField("hh");
                fh.setAccessible(true);
                int idx = fh.getInt(bot);
                if (idx < 0) idx = 0;

                // Cập nhật số cá câu được (chống double count / x2 bằng cooldown 3s)
                if (msg.contains("Câu thành công") || msg.contains("Đã câu được một con")) {
                    long now = System.currentTimeMillis();
                    Long lastTime = lastFishIncrementTime.get(Integer.valueOf(idx));
                    if (lastTime == null || now - lastTime.longValue() >= 3000L) {
                        lastFishIncrementTime.put(Integer.valueOf(idx), Long.valueOf(now));
                        Integer cur = fishCountMap.get(Integer.valueOf(idx));
                        int newCount = (cur != null ? cur.intValue() : 0) + 1;
                        fishCountMap.put(Integer.valueOf(idx), Integer.valueOf(newCount));
                        // Lưu ngay khi có cá mới để không mất khi tool tắt đột ngột
                        persistState();
                    }
                }

                // Cập nhật thông tin chi tiết nhiệm vụ thợ câu
                if (msg.contains("Nhiệm vụ hiện tại của bạn là nhiệm vụ:") || msg.contains("Nhiệm Vụ Thợ Câu")) {
                    missionDetailMap.put(Integer.valueOf(idx), msg.trim());
                    persistState();
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

    /**
     * The state renderer uses lastActiveBot for a single-account install when
     * the indexed cache cannot identify the bot. Match that fallback on Stop,
     * but never borrow the last bot when multiple accounts are present.
     */
    private static Object getSingleAccountFallbackBot(int index) {
        if (index != 0) return null;
        try {
            init();
            if (fM == null) return null;
            List accounts = (List) fM.get(null);
            if (accounts == null || accounts.size() != 1) return null;
            return lastActiveBot;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * avt.Q.H can miss the live instance in its primary slot and indexed cache.
     * Fall back to lastActiveBot only when the UI has exactly one account.
     */
    public static void stopBotAtIndex(int index) {
        // Đánh dấu index này đã dừng ngay lập tức để chặn log đang enqueue
        stoppedBotIndices.add(Integer.valueOf(index));

        Object bot = activeBotsByIndex.get(Integer.valueOf(index));
        if (isBotStopped(bot)) {
            Object fallbackBot = getSingleAccountFallbackBot(index);
            if (!isBotStopped(fallbackBot)) bot = fallbackBot;
        }
        if (isBotStopped(bot)) return;

        cancelPendingCast(bot);
        try {
            // Dùng reflection để tránh lỗi ambiguous overload từ obfuscation
            Field hlField = bot.getClass().getDeclaredField("hl");
            hlField.setAccessible(true);
            hlField.setBoolean(bot, true);

            try {
                Field hoField = bot.getClass().getDeclaredField("ho");
                hoField.setAccessible(true);
                hoField.setBoolean(bot, true);
            } catch (Throwable ignored) {}

            // Khóa cờ hy để Sd() không thể reconnect
            try {
                Field hyField = bot.getClass().getDeclaredField("hy");
                hyField.setAccessible(true);
                Object hy = hyField.get(bot);
                if (hy instanceof AtomicBoolean) {
                    ((AtomicBoolean) hy).set(true);
                }
            } catch (Throwable ignored) {}

            // Hủy reconnect scheduler trong listener F (avt.game.O)
            try {
                Field fField = bot.getClass().getDeclaredField("F");
                fField.setAccessible(true);
                Object listener = fField.get(bot);
                if (listener != null) {
                    try {
                        Field bField = listener.getClass().getDeclaredField("b");
                        bField.setAccessible(true);
                        Object fut = bField.get(listener);
                        if (fut instanceof ScheduledFuture) {
                            ((ScheduledFuture<?>) fut).cancel(true);
                        }
                    } catch (Throwable ignored) {}
                    try {
                        Field hField = listener.getClass().getDeclaredField("H");
                        hField.setAccessible(true);
                        Object exec = hField.get(listener);
                        if (exec instanceof ScheduledExecutorService) {
                            ((ScheduledExecutorService) exec).shutdownNow();
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            // Dừng executor hf trong bot k
            try {
                Field hfField = bot.getClass().getDeclaredField("hf");
                hfField.setAccessible(true);
                Object hf = hfField.get(bot);
                if (hf instanceof ScheduledExecutorService) {
                    ((ScheduledExecutorService) hf).shutdownNow();
                }
            } catch (Throwable ignored) {}

            Method stopMethod = null;
            for (Method m : bot.getClass().getDeclaredMethods()) {
                if ("N".equals(m.getName()) && m.getParameterTypes().length == 1
                        && m.getParameterTypes()[0] == Object[].class) {
                    stopMethod = m;
                    break;
                }
            }
            if (stopMethod != null) {
                stopMethod.setAccessible(true);
                stopMethod.invoke(bot, new Object[]{ new Object[0] });
            }

            // Đóng socket kết nối
            try {
                Field jField = bot.getClass().getDeclaredField("J");
                jField.setAccessible(true);
                Object session = jField.get(bot);
                if (session != null) {
                    Method mn = session.getClass().getDeclaredMethod("n", Object[].class);
                    mn.setAccessible(true);
                    mn.invoke(session, new Object[]{ new Object[0] });
                }
            } catch (Throwable ignored) {}

            // Ngắt thread thực thi bot
            try {
                Field hxField = bot.getClass().getDeclaredField("hX");
                hxField.setAccessible(true);
                Object thread = hxField.get(bot);
                if (thread instanceof Thread) {
                    ((Thread) thread).interrupt();
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            // Best-effort
        }

        activeBotsByIndex.remove(Integer.valueOf(index));
        if (bot != null && bot == lastActiveBot) {
            lastActiveBot = null;
        }

        // Xóa bot khỏi mảng tĩnh avt.Q.o
        try {
            if (fo != null) {
                Object[] bots = (Object[]) fo.get(null);
                if (bots != null && index >= 0 && index < bots.length) {
                    bots[index] = null;
                }
            }
        } catch (Throwable ignored) {}

        // Đặt acc.b = false trong danh sách tài khoản UI (avt.Q.m)
        try {
            if (fM != null) {
                List accountsList = (List) fM.get(null);
                if (accountsList != null && index >= 0 && index < accountsList.size()) {
                    Object acc = accountsList.get(index);
                    Field bField = acc.getClass().getDeclaredField("b");
                    bField.setAccessible(true);
                    bField.setBoolean(acc, false);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Dừng tất cả bot một cách triệt để
     */
    public static void stopAllBots() {
        init();
        try {
            if (fM != null) {
                List accountsList = (List) fM.get(null);
                if (accountsList != null) {
                    for (int i = 0; i < accountsList.size(); i++) {
                        stopBotAtIndex(i);
                    }
                    return;
                }
            }
        } catch (Throwable ignored) {}
        for (int i = 0; i < 20; i++) {
            stopBotAtIndex(i);
        }
    }

    /**
     * Lưu fishCountMap, missionDetailMap và stateDate vào file JSON để khôi phục khi mở lại tool.
     * Ghi theo cơ chế atomic (ghi file tạm rồi rename) để tránh corrupt khi tắt đột ngột.
     */
    static void persistState() {
        try {
            String today = (lastStateDate != null) ? lastStateDate : getTodayDate();
            S root = new S();
            root.put("stateDate", today);

            S counts = new S();
            for (Map.Entry<Integer, Integer> e : fishCountMap.entrySet()) {
                counts.put(e.getKey().toString(), e.getValue());
            }
            root.put("fishCounts", counts);

            S missions = new S();
            for (Map.Entry<Integer, String> e : missionDetailMap.entrySet()) {
                missions.put(e.getKey().toString(), e.getValue());
            }
            root.put("missions", missions);

            S accStats = new S();
            for (Map.Entry<Integer, Map<String, Object>> e : accountInfoMap.entrySet()) {
                S s = new S();
                for (Map.Entry<String, Object> se : e.getValue().entrySet()) {
                    s.put(se.getKey(), se.getValue());
                }
                accStats.put(e.getKey().toString(), s);
            }
            root.put("accountStats", accStats);

            String json = root.z(new Object[0]);
            // Ghi vào cả data/ và app/data/ tự động nhận biết thư mục chạy
            writeStateToFile(new File("data/bot-state.json"), json);
            if (isRunningInAppDir()) {
                try {
                    File parent = new File(".").getCanonicalFile().getParentFile();
                    if (parent != null) {
                        writeStateToFile(new File(parent, "data/bot-state.json"), json);
                    }
                } catch (Throwable ignored) {}
            } else {
                writeStateToFile(new File("app/data/bot-state.json"), json);
            }
        } catch (Throwable t) {
            // Không throw - lưu state là best-effort
        }
    }

    private static void writeStateToFile(File stateFile, String json) {
        try {
            File parent = stateFile.getParentFile();
            if (parent != null) parent.mkdirs();
            File tmp = new File(stateFile.getPath() + ".tmp");
            try (OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                w.write(json);
            }
            try {
                Files.move(tmp.toPath(), stateFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable t1) {
                Files.move(tmp.toPath(), stateFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Khôi phục fishCountMap, missionDetailMap và accountInfoMap từ file JSON khi khởi động.
     * Nếu ngày lưu khác ngày hiện tại, reset fishCountMap về 0.
     * Hỗ trợ đa tài khoản theo index slot (0, 1, 2, ...).
     */
    @SuppressWarnings("unchecked")
    static synchronized void restoreState() {
        stateRestored = true;
        try {
            String today = getTodayDate();
            lastStateDate = today;

            File fileToRead = null;
            File[] candidates = new File[]{
                new File("data/bot-state.json"),
                new File("data/bot-state.json.tmp"),
                new File("app/data/bot-state.json"),
                new File("app/data/bot-state.json.tmp"),
                new File("../data/bot-state.json"),
                new File("../app/data/bot-state.json")
            };
            long maxMod = -1;
            for (File c : candidates) {
                if (c.exists() && c.length() > 10 && c.lastModified() > maxMod) {
                    maxMod = c.lastModified();
                    fileToRead = c;
                }
            }
            if (fileToRead == null) return;
            byte[] bytes = Files.readAllBytes(fileToRead.toPath());
            String json = new String(bytes, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) return;

            // Gọi phương thức parser.g(Object[]) của thư viện json-simple đã obfuscate
            Object root = null;
            org.json.simple.parser.J parser = new org.json.simple.parser.J();
            try {
                root = parser.g(new Object[]{ json });
            } catch (Throwable t1) {
                for (Method m : parser.getClass().getDeclaredMethods()) {
                    if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == Object[].class
                            && Object.class.isAssignableFrom(m.getReturnType())) {
                        try {
                            m.setAccessible(true);
                            root = m.invoke(parser, new Object[]{ new Object[]{ json } });
                            if (root instanceof S) break;
                        } catch (Throwable ignored) {}
                    }
                }
            }
            if (!(root instanceof S)) return;
            S rootMap = (S) root;

            Object dateObj = rootMap.get("stateDate");
            String savedDate = (dateObj != null) ? dateObj.toString().trim() : null;
            boolean isSameDay = today.equals(savedDate);

            if (isSameDay) {
                Object countsObj = rootMap.get("fishCounts");
                if (countsObj instanceof S) {
                    S counts = (S) countsObj;
                    for (Object key : counts.keySet()) {
                        try {
                            int idx = Integer.parseInt(key.toString());
                            Object val = counts.get(key);
                            if (val instanceof Number) {
                                fishCountMap.put(Integer.valueOf(idx), ((Number) val).intValue());
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } else {
                fishCountMap.clear();
                persistState();
            }

            Object missionsObj = rootMap.get("missions");
            if (missionsObj instanceof S) {
                S missions = (S) missionsObj;
                for (Object key : missions.keySet()) {
                    try {
                        int idx = Integer.parseInt(key.toString());
                        Object val = missions.get(key);
                        if (val != null) {
                            missionDetailMap.put(Integer.valueOf(idx), val.toString());
                        }
                    } catch (Throwable ignored) {}
                }
            }

            Object statsObj = rootMap.get("accountStats");
            if (statsObj instanceof S) {
                S statsMap = (S) statsObj;
                for (Object key : statsMap.keySet()) {
                    try {
                        int idx = Integer.parseInt(key.toString());
                        Object val = statsMap.get(key);
                        if (val instanceof S) {
                            S s = (S) val;
                            Map<String, Object> map = new ConcurrentHashMap<String, Object>();
                            for (Object k : s.keySet()) {
                                Object item = s.get(k);
                                if (item != null) map.put(k.toString(), item);
                            }
                            accountInfoMap.put(Integer.valueOf(idx), map);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            // Không throw - restore là best-effort
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

            if (isBotStopped(bot)) return;

            Method mg = bot.getClass().getDeclaredMethod("g", Object[].class);
            mg.setAccessible(true);
            mg.invoke(bot, new Object[]{ new Object[]{ msg } });
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void doQuangCau(final Object bot) {
        if (bot == null || isBotStopped(bot)) {
            cancelPendingCast(bot);
            return;
        }
        registerActiveBot(bot);
        cancelPendingCast(bot);

        // Sau khi câu xong (thành công hoặc trật), bot gọi k.V để quăng cần lượt tiếp.
        // Đợi 1.0s rồi tự động bắt đầu lượt câu mới 45 -> 41.
        // LƯU LẠI ScheduledFuture để cancelPendingCast có thể hủy ngay lập tức khi ấn Dừng!
        ScheduledFuture<?> future = castScheduler.schedule(new Runnable() {
            public void run() {
                try {
                    if (!isBotStopped(bot)) {
                        doSr(bot);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }, 1000L, TimeUnit.MILLISECONDS);
        pendingCastMap.put(bot, future);
    }

    public static void doSr(final Object bot) {
        try {
            if (bot == null || isBotStopped(bot)) {
                cancelPendingCast(bot);
                return;
            }
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
            ScheduledFuture<?> castFuture = castScheduler.schedule(new Runnable() {
                public void run() {
                    if (!isBotStopped(bot)) {
                        sendQuangCauNow(bot);
                    }
                }
            }, 200L, TimeUnit.MILLISECONDS);
            pendingCastMap.put(bot, castFuture);

            // 5. Watchdog timeout 30s: tự động quăng lại nếu cá không cắn câu
            ScheduledFuture<?> watchdog = castScheduler.schedule(new Runnable() {
                public void run() {
                    try {
                        cancelPendingCast(bot);
                        Field stoppingFlag = bot.getClass().getDeclaredField("hl");
                        stoppingFlag.setAccessible(true);
                        if (!stoppingFlag.getBoolean(bot) && !isBotStopped(bot)) {
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

            int idx = -1;
            if (bot != null) {
                try {
                    Field fh = bot.getClass().getDeclaredField("hh");
                    fh.setAccessible(true);
                    idx = fh.getInt(bot);
                } catch (Throwable ignored) {}
            }

            boolean isStopping = false;
            try {
                if (bot != null) {
                    Field stoppingFlag = bot.getClass().getDeclaredField("hl");
                    stoppingFlag.setAccessible(true);
                    isStopping = stoppingFlag.getBoolean(bot);
                }
            } catch (Throwable ignored) {}

            boolean isStopped = isStopping || isBotStopped(bot);
            if (!isStopped && idx >= 0 && stoppedBotIndices.contains(Integer.valueOf(idx))) {
                isStopped = true;
            }

            if (isStopped) {
                if (idx >= 0) {
                    stoppedBotIndices.add(Integer.valueOf(idx));
                    activeBotsByIndex.remove(Integer.valueOf(idx));
                }
                if (bot != null && bot == lastActiveBot) {
                    lastActiveBot = null;
                }

                // Khóa cờ hy để Sd() không thể reconnect
                try {
                    if (bot != null) {
                        Field hyField = bot.getClass().getDeclaredField("hy");
                        hyField.setAccessible(true);
                        Object hy = hyField.get(bot);
                        if (hy instanceof AtomicBoolean) {
                            ((AtomicBoolean) hy).set(true);
                        }
                    }
                } catch (Throwable ignored) {}

                // Hủy triệt để hẹn giờ tự động kết nối lại
                try {
                    Field bField = oClass.getDeclaredField("b");
                    bField.setAccessible(true);
                    Object fut = bField.get(listener);
                    if (fut instanceof ScheduledFuture) {
                        ((ScheduledFuture<?>) fut).cancel(true);
                    }
                } catch (Throwable ignored) {}
                try {
                    Field hField = oClass.getDeclaredField("H");
                    hField.setAccessible(true);
                    Object exec = hField.get(listener);
                    if (exec instanceof ScheduledExecutorService) {
                        ((ScheduledExecutorService) exec).shutdownNow();
                    }
                } catch (Throwable ignored) {}

                // Dừng executor hf trong bot k
                try {
                    if (bot != null) {
                        Field hfField = bot.getClass().getDeclaredField("hf");
                        hfField.setAccessible(true);
                        Object hf = hfField.get(bot);
                        if (hf instanceof ScheduledExecutorService) {
                            ((ScheduledExecutorService) hf).shutdownNow();
                        }
                    }
                } catch (Throwable ignored) {}

                if (bot != null) {
                    Method ms = bot.getClass().getDeclaredMethod("S", Object[].class);
                    ms.setAccessible(true);
                    ms.invoke(bot, new Object[]{ new Object[0] });
                }
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
            try { fO = qClass.getDeclaredField("O"); fO.setAccessible(true); } catch (Throwable t) {}
            try { fl = qClass.getDeclaredField("l"); fl.setAccessible(true); } catch (Throwable t) {}

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
                if (m.getName().equals("Y") && m.getReturnType() == void.class && m.getParameterTypes().length == 1) {
                    mSaveClientAccounts = m;
                    mSaveClientAccounts.setAccessible(true);
                }
            }

            try {
                mv5 = qClass.getDeclaredMethod("v5", Object[].class);
                mv5.setAccessible(true);
            } catch (Throwable t) {}

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

            syncAccountFilesOnStartup();

            if (!shutdownHookRegistered) {
                shutdownHookRegistered = true;
                try {
                    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                        public void run() {
                            saveClientAccounts();
                            persistState();
                        }
                    }, "AccountSaverShutdownHook"));
                } catch (Throwable t) {}
            }

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
            if (fW != null) fW.set(null, "user");
            if (fO != null) fO.set(null, "Không giới hạn (Vĩnh viễn)");
            if (fl != null) fl.set(null, "Vĩnh viễn");
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

            saveClientAccounts();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static boolean hasAccountCredentials(List accountsList) {
        if (accountsList == null || accountsList.isEmpty()) return false;
        for (Object acc : accountsList) {
            if (acc == null) continue;
            try {
                Field fn = acc.getClass().getDeclaredField("N");
                fn.setAccessible(true);
                Object u = fn.get(acc);
                if (u != null && !u.toString().trim().isEmpty()) {
                    return true;
                }
            } catch (Throwable t) {}
            try {
                Field fp = acc.getClass().getDeclaredField("p");
                fp.setAccessible(true);
                Object p = fp.get(acc);
                if (p != null && !p.toString().trim().isEmpty()) {
                    return true;
                }
            } catch (Throwable t) {}
        }
        return false;
    }

    private static boolean fileHasAccountCredentials(File file) {
        if (file == null || !file.isFile() || file.length() < 10) return false;
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (content.contains("\"username\":\"") && !content.contains("\"username\":\"\"")) {
                return true;
            }
            if (content.contains("\"password\":\"") && !content.contains("\"password\":\"\"")) {
                return true;
            }
        } catch (Throwable t) {}
        return false;
    }

    private static boolean isRunningInAppDir() {
        try {
            File curDir = new File(".").getCanonicalFile();
            return "app".equalsIgnoreCase(curDir.getName());
        } catch (Throwable t) {
            String path = new File(".").getAbsolutePath().replace('\\', '/');
            return path.endsWith("/app/.") || path.endsWith("/app");
        }
    }

    private static List<File> getTargetAccountFiles() {
        List<File> list = new ArrayList<File>();
        list.add(new File("data/client-accounts.json"));
        if (isRunningInAppDir()) {
            try {
                File parent = new File(".").getCanonicalFile().getParentFile();
                if (parent != null) {
                    list.add(new File(parent, "data/client-accounts.json"));
                }
            } catch (Throwable ignored) {}
        } else {
            list.add(new File("app/data/client-accounts.json"));
        }
        return list;
    }

    public static synchronized void syncAccountFilesOnStartup() {
        try {
            List<File> targetFiles = getTargetAccountFiles();

            File bestFile = null;
            String bestContent = null;
            boolean bestHasCreds = false;

            for (File f : targetFiles) {
                try {
                    if (f.isFile() && f.length() > 10) {
                        String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                        if (content.startsWith("[") && content.endsWith("]")) {
                            boolean hasCreds = content.contains("\"username\":\"") && !content.contains("\"username\":\"\"");
                            if (bestFile == null || (!bestHasCreds && hasCreds) || (hasCreds && content.length() > (bestContent != null ? bestContent.length() : 0))) {
                                bestFile = f;
                                bestContent = content;
                                bestHasCreds = hasCreds;
                                if (hasCreds) break;
                            }
                        }
                    }
                } catch (Throwable t) {}
            }

            if (bestContent != null && bestFile != null) {
                byte[] bytes = bestContent.getBytes(StandardCharsets.UTF_8);
                for (File f : targetFiles) {
                    try {
                        File parent = f.getParentFile();
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }
                        if (parent != null && parent.exists()) {
                            boolean targetHasCreds = fileHasAccountCredentials(f);
                            if (!targetHasCreds && bestHasCreds) {
                                Files.write(f.toPath(), bytes);
                            } else if (!f.exists() || f.length() < 10) {
                                Files.write(f.toPath(), bytes);
                            }
                        }
                    } catch (Throwable t) {}
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static synchronized void saveClientAccounts() {
        try {
            if (fM == null) return;
            List accountsList = (List) fM.get(null);
            if (accountsList == null || accountsList.isEmpty()) {
                return;
            }

            List<File> targetFiles = getTargetAccountFiles();
            boolean hasCreds = hasAccountCredentials(accountsList);

            // Safety guard: If memory list has NO credentials, but disk files ALREADY have credentials,
            // DO NOT OVERWRITE! Instead, reload them from disk into memory!
            if (!hasCreds) {
                boolean anyDiskHasCreds = false;
                for (File f : targetFiles) {
                    if (fileHasAccountCredentials(f)) {
                        anyDiskHasCreds = true;
                        break;
                    }
                }
                if (anyDiskHasCreds) {
                    if (mv5 != null) {
                        try {
                            mv5.invoke(null, new Object[]{ new Object[0] });
                        } catch (Throwable t) {}
                    }
                    return;
                }
            }

            p jsonArray = new p();
            for (int i = 0; i < accountsList.size(); i++) {
                Object acc = accountsList.get(i);
                if (acc == null) continue;
                try {
                    try {
                        Field fd = acc.getClass().getDeclaredField("d");
                        fd.setAccessible(true);
                        fd.setInt(acc, i + 1);
                    } catch (Throwable t) {}

                    Method ma = acc.getClass().getDeclaredMethod("a", Object[].class);
                    ma.setAccessible(true);
                    S jsonAcc = (S) ma.invoke(acc, new Object[]{ new Object[]{ Integer.valueOf(i + 1) } });
                    if (jsonAcc != null) {
                        jsonArray.add(jsonAcc);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }

            if (jsonArray.isEmpty()) return;

            String jsonStr = jsonArray.z(new Object[0]);
            byte[] bytes = jsonStr.getBytes(StandardCharsets.UTF_8);

            for (File file : targetFiles) {
                try {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    if (parent != null && parent.exists()) {
                        Files.write(file.toPath(), bytes);
                    }
                } catch (Throwable t) {}
            }

            if (mSaveClientAccounts != null) {
                try {
                    mSaveClientAccounts.invoke(null, new Object[]{ new Object[]{ Boolean.FALSE } });
                } catch (Throwable t) {}
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

            if (acc != null) {
                try {
                    for (Field f : acc.getClass().getDeclaredFields()) {
                        if (f.getName().equalsIgnoreCase("dailyFishLimit") || f.getName().toLowerCase().contains("limit")) {
                            f.setAccessible(true);
                            Object v = f.get(acc);
                            if (v instanceof Number && ((Number) v).intValue() > 0) {
                                fishLimit = ((Number) v).intValue();
                                break;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            checkDailyReset();

            Integer trackedCount = fishCountMap.get(Integer.valueOf(botIdx));
            if (trackedCount != null) {
                fishCount = Math.max(0, trackedCount.intValue());
            } else {
                // Đếm số lần câu thành công từ log bot.z nếu có (khi bot chưa được tracking)
                try {
                    Field fz = kClass.getDeclaredField("z"); fz.setAccessible(true);
                    Object zVal = fz.get(bot);
                    if (zVal != null) {
                        String log = zVal.toString();
                        int c = 0;
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("C[^\n]*?u th[^\n]*?nh c[^\n]*?ng").matcher(log);
                        while (m.find()) {
                            c++;
                        }
                        fishCount = c;
                        fishCountMap.put(Integer.valueOf(botIdx), Integer.valueOf(fishCount));
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

            // Lưu thông tin nhân vật, xu, lượng, level vào accountInfoMap để khôi phục khi mở lại tool
            Map<String, Object> stats = accountInfoMap.get(Integer.valueOf(botIdx));
            if (stats == null) {
                stats = new ConcurrentHashMap<String, Object>();
                accountInfoMap.put(Integer.valueOf(botIdx), stats);
            }
            if (charName != null && !charName.trim().isEmpty()) stats.put("character", charName.trim());
            if (level > 0) {
                stats.put("level", Integer.valueOf(level));
                stats.put("levelPercent", Integer.valueOf(levelPercent >= 0 ? levelPercent : 0));
            }
            if (info.get("xu") != null) stats.put("xu", info.get("xu"));
            if (info.get("luong") != null) stats.put("luong", info.get("luong"));
            if (info.get("luongKhoa") != null) stats.put("luongKhoa", info.get("luongKhoa"));
            persistState();

        } catch (Throwable t) {
            t.printStackTrace();
        }
        return info;
    }

    public static String getStateJson(Object[] args) {
        checkDailyReset();
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
            if (selectedAccount == null || selectedAccount.equals("-") || selectedAccount.trim().isEmpty()) {
                Map<String, Object> stats = accountInfoMap.get(Integer.valueOf(selectedIndex));
                if (stats != null && stats.get("character") != null) {
                    selectedAccount = stats.get("character").toString();
                }
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
                        boolean isStoppedByIndex = stoppedBotIndices.contains(Integer.valueOf(i));
                        Object bot = null;
                        if (!isStoppedByIndex) {
                            bot = (bots != null && i < bots.length && bots[i] != null) ? bots[i] : null;
                            if (bot == null) {
                                bot = activeBotsByIndex.get(Integer.valueOf(i));
                            }
                            if (bot == null && lastActiveBot != null && (accountsList.size() == 1 || i == selectedIndex)) {
                                bot = lastActiveBot;
                            }
                            if (bot != null && isBotStopped(bot)) {
                                bot = null;
                            }
                        } else {
                            try {
                                Field bField = acc.getClass().getDeclaredField("b");
                                bField.setAccessible(true);
                                bField.setBoolean(acc, false);
                            } catch (Throwable ignored) {}
                        }
                        
                        Method ma = acc.getClass().getDeclaredMethod("a", Object[].class);
                        ma.setAccessible(true);
                        S jsonAcc = (S) ma.invoke(acc, new Object[]{ new Object[]{ Integer.valueOf(i) } });
                        
                        boolean isRunning = false;
                        if (bot != null) {
                            isRunning = true;
                            try {
                                Field stoppingFlag = bot.getClass().getDeclaredField("hl");
                                stoppingFlag.setAccessible(true);
                                boolean isStopping = stoppingFlag.getBoolean(bot);
                                if (isStopping) isRunning = false;
                            } catch (Throwable t) {}
                            try {
                                Field hoFlag = bot.getClass().getDeclaredField("ho");
                                hoFlag.setAccessible(true);
                                if (hoFlag.getBoolean(bot)) isRunning = false;
                            } catch (Throwable t) {}
                            
                            // Bổ sung/cập nhật info chi tiết từ bot cho UI
                            S info = buildInfoFromBot(bot, acc);
                            jsonAcc.put("info", info);
                        } else {
                            S info = (S) jsonAcc.get("info");
                            if (info == null) {
                                info = new S();
                                jsonAcc.put("info", info);
                            }
                            int savedCount = 0;
                            Integer countVal = fishCountMap.get(Integer.valueOf(i));
                            if (countVal != null) savedCount = Math.max(0, countVal.intValue());
                            info.put("dailyFishCount", savedCount);

                            int fishLimit = 550;
                            Object limitObj = jsonAcc.get("dailyFishLimit");
                            if (limitObj instanceof Number && ((Number) limitObj).intValue() > 0) {
                                fishLimit = ((Number) limitObj).intValue();
                            } else if (acc != null) {
                                try {
                                    for (Field f : acc.getClass().getDeclaredFields()) {
                                        if (f.getName().equalsIgnoreCase("dailyFishLimit") || f.getName().toLowerCase().contains("limit")) {
                                            f.setAccessible(true);
                                            Object v = f.get(acc);
                                            if (v instanceof Number && ((Number) v).intValue() > 0) {
                                                fishLimit = ((Number) v).intValue();
                                                break;
                                            }
                                        }
                                    }
                                } catch (Throwable ignored) {}
                            }
                            info.put("dailyFishLimit", fishLimit);

                            String savedMission = missionDetailMap.get(Integer.valueOf(i));
                            if (savedMission != null && !savedMission.trim().isEmpty() && !savedMission.equals("3.2.2")) {
                                info.put("currentMission", savedMission.trim());
                            } else {
                                info.put("currentMission", "Chưa có thông tin nhiệm vụ (sẽ tự động cập nhật khi bot chạy)");
                            }
                            if (info.get("xu") == null) info.put("xu", "0");
                            if (info.get("luong") == null) info.put("luong", "0");
                            if (info.get("luongKhoa") == null) info.put("luongKhoa", "0");
                            if (info.get("farmReturnAt") == null) info.put("farmReturnAt", 0L);

                            // Khôi phục character, level, levelPercent, xu, luong, luongKhoa đã lưu
                            Map<String, Object> stats = accountInfoMap.get(Integer.valueOf(i));
                            if (stats != null) {
                                if (stats.get("character") != null) info.put("character", stats.get("character"));
                                if (stats.get("level") != null) info.put("level", stats.get("level"));
                                if (stats.get("levelPercent") != null) info.put("levelPercent", stats.get("levelPercent"));
                                if (stats.get("xu") != null) info.put("xu", stats.get("xu"));
                                if (stats.get("luong") != null) info.put("luong", stats.get("luong"));
                                if (stats.get("luongKhoa") != null) info.put("luongKhoa", stats.get("luongKhoa"));
                            }
                            if (info.get("character") == null) info.put("character", "-");
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
