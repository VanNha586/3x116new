package com.fishing.services;

import com.fishing.models.FishingLogModel;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns all fishing workers and guarantees that Stop interrupts and joins them
 * before the service reports itself stopped.
 */
public class FishingService {
    private final FishingLogModel logModel;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object lifecycleLock = new Object();
    private volatile Thread coordinator;
    private volatile Thread unlimitedWorker;
    private volatile Thread unclearWorker;
    private volatile FishingCallback callback;

    public interface FishingCallback {
        void onLogUpdate(String log);
        void onFishingComplete();
        void onFishingStop();
    }

    public FishingService(FishingLogModel logModel) {
        this.logModel = logModel;
    }

    public void setCallback(FishingCallback callback) {
        this.callback = callback;
    }

    public void startFishing() {
        synchronized (lifecycleLock) {
            if (running.get()) return;
            running.set(true);

            coordinator = new Thread(this::runFishing, "fishing-coordinator");
            coordinator.setDaemon(true);
            coordinator.start();
        }
    }

    private void runFishing() {
        Thread unlimited = createFishingThread("UNLIMITED", "Không giới hạn");
        Thread unclear = createFishingThread("UNCLEAR", "Không rõ");
        unlimitedWorker = unlimited;
        unclearWorker = unclear;

        try {
            append("SYSTEM", "🎣 Bắt đầu câu cá...");
            unlimited.start();
            unclear.start();

            unlimited.join();
            unclear.join();

            if (running.get()) {
                append("SYSTEM", "✅ Kết thúc câu cá");
                running.set(false);
                notifyComplete();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stopWorkersAndJoin();
            synchronized (lifecycleLock) {
                running.set(false);
                coordinator = null;
                unlimitedWorker = null;
                unclearWorker = null;
            }
        }
    }

    public void stopFishing() {
        synchronized (lifecycleLock) {
            if (!running.get()) return;
            append("SYSTEM", "⏹️ Người dùng bấm DỪNG");
            running.set(false);
            interruptWorkers();
            Thread c = coordinator;
            if (c != null && c != Thread.currentThread()) c.interrupt();
        }

        stopWorkersAndJoin();
        append("SYSTEM", "⏹️ Đã dừng toàn bộ luồng câu cá");
        notifyStop();
    }

    private void interruptWorkers() {
        Thread a = unlimitedWorker;
        Thread b = unclearWorker;
        if (a != null) a.interrupt();
        if (b != null) b.interrupt();
    }

    private void stopWorkersAndJoin() {
        interruptWorkers();
        joinQuietly(unlimitedWorker);
        joinQuietly(unclearWorker);
    }

    private static void joinQuietly(Thread worker) {
        if (worker == null || worker == Thread.currentThread()) return;
        try {
            worker.join(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Thread createFishingThread(String stream, String streamName) {
        return new Thread(() -> {
            int attempt = 1;
            try {
                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    String log = String.format("🐟 [%s - Lần %d] Câu được cá", streamName, attempt++);
                    append(stream, log);

                    // Do not leave a sleeping worker alive after Stop.
                    Thread.sleep(2000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                // Only write completion for a real stop/finish; never enqueue more
                // "fishing" work after running becomes false.
                if (!running.get()) {
                    append(stream, "⏹️ Dừng " + streamName);
                }
            }
        }, "fishing-" + stream);
    }

    private void append(String stream, String message) {
        String line = logModel.addLog(stream, message);
        FishingCallback cb = callback;
        if (cb != null) cb.onLogUpdate(line);
    }

    private void notifyComplete() {
        FishingCallback cb = callback;
        if (cb != null) cb.onFishingComplete();
    }

    private void notifyStop() {
        FishingCallback cb = callback;
        if (cb != null) cb.onFishingStop();
    }

    public boolean isRunning() {
        return running.get();
    }
}
