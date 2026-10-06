package com.fishing.services;

import com.fishing.models.FishingLogModel;
import java.util.concurrent.atomic.AtomicBoolean;

public class FishingService {
    private FishingLogModel logModel;
    private AtomicBoolean isRunning;
    private FishingCallback callback;

    public interface FishingCallback {
        void onLogUpdate(String log);
        void onFishingComplete();
        void onFishingStop();
    }

    public FishingService(FishingLogModel logModel) {
        this.logModel = logModel;
        this.isRunning = new AtomicBoolean(false);
    }

    public void setCallback(FishingCallback callback) {
        this.callback = callback;
    }

    /**
     * Bắt đầu câu cá (chạy trên thread riêng)
     */
    public void startFishing() {
        if (isRunning.getAndSet(true)) {
            return; // Đã chạy rồi
        }

        Thread fishingThread = new Thread(() -> {
            try {
                logModel.addLog("SYSTEM", "🎣 Bắt đầu câu cá...");
                if (callback != null) {
                    callback.onLogUpdate("🎣 Bắt đầu câu cá...");
                }

                // Giả lập 2 luồng câu cá khác nhau
                Thread unlimitedThread = createFishingThread("UNLIMITED", "Không giới hạn");
                Thread unclearThread = createFishingThread("UNCLEAR", "Không rõ");

                unlimitedThread.start();
                unclearThread.start();

                unlimitedThread.join();
                unclearThread.join();

                logModel.addLog("SYSTEM", "✅ Kết thúc câu cá");
                if (callback != null) {
                    callback.onFishingComplete();
                }
            } catch (InterruptedException e) {
                logModel.addLog("SYSTEM", "❌ Câu cá bị gián đoạn");
                if (callback != null) {
                    callback.onFishingStop();
                }
            } finally {
                isRunning.set(false);
            }
        });

        fishingThread.setDaemon(false);
        fishingThread.start();
    }

    /**
     * Dừng câu cá ngay lập tức
     */
    public void stopFishing() {
        if (!isRunning.getAndSet(false)) {
            return; // Không chạy
        }

        logModel.addLog("SYSTEM", "⏹️ Người dùng bấm DỪNG");
        if (callback != null) {
            callback.onFishingStop();
        }
    }

    /**
     * Tạo một luồng câu cá
     */
    private Thread createFishingThread(String type, String typeName) {
        return new Thread(() -> {
            for (int i = 1; i <= 10; i++) {
                if (!isRunning.get()) {
                    logModel.addLog(type, "⏹️ Dừng " + typeName);
                    break;
                }

                String log = String.format("🐟 [%s - Lần %d] Câu được cá", typeName, i);
                logModel.addLog(type, log);
                
                if (callback != null) {
                    callback.onLogUpdate(log);
                }

                try {
                    Thread.sleep(2000); // Chờ 2 giây giữa mỗi lần câu
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            logModel.addLog(type, "📝 Nhật ký " + typeName + " hoàn thành");
        });
    }

    /**
     * Kiểm tra xem có đang chạy không
     */
    public boolean isRunning() {
        return isRunning.get();
    }
}
