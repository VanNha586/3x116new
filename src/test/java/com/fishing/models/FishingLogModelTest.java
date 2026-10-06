package com.fishing.models;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class FishingLogModelTest {
    private FishingLogModel logModel;

    @Before
    public void setUp() {
        logModel = new FishingLogModel();
        logModel.clearLogs();
    }

    @After
    public void tearDown() {
        logModel.clearLogs();
    }

    @Test
    public void testAddLogAndGetCount() {
        assertEquals(0, logModel.getLogCount());
        logModel.addLog("TEST", "Message 1");
        assertEquals(1, logModel.getLogCount());
        assertTrue(logModel.getLogs().get(0).contains("Message 1"));
    }

    @Test
    public void testClearLogs() {
        logModel.addLog("TEST", "Message 1");
        logModel.clearLogs();
        assertEquals(0, logModel.getLogCount());
    }
}
