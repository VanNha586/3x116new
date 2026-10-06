package com.fishing.services;

import com.fishing.models.FishingLogModel;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FishingServiceTest {
    private FishingLogModel logModel;
    private FishingService fishingService;

    @Before
    public void setUp() {
        logModel = new FishingLogModel();
        logModel.clearLogs();
        fishingService = new FishingService(logModel);
    }

    @Test
    public void testStartAndStopFishing() throws InterruptedException {
        CountDownLatch logLatch = new CountDownLatch(1);
        fishingService.setCallback(new FishingService.FishingCallback() {
            @Override
            public void onLogUpdate(String log) {
                logLatch.countDown();
            }

            @Override
            public void onFishingComplete() {}

            @Override
            public void onFishingStop() {}
        });

        fishingService.startFishing();
        assertTrue(fishingService.isRunning());
        
        boolean updated = logLatch.await(5, TimeUnit.SECONDS);
        assertTrue(updated);

        fishingService.stopFishing();
        assertFalse(fishingService.isRunning());
    }
}
