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
        String line = logModel.addLog("UNLIMITED", "Message 1");
        assertEquals(1, logModel.getLogCount());
        assertTrue(line.contains("[UNLIMITED]"));
        assertTrue(line.contains("Message 1"));
    }

    @Test
    public void testLogsFromTwoStreamsRemainOneChronologicalList() {
        String first = logModel.addLog("UNLIMITED", "A");
        String second = logModel.addLog("UNCLEAR", "B");
        assertEquals(2, logModel.getLogCount());
        assertTrue(logModel.getLogs().get(0).contains("A"));
        assertTrue(logModel.getLogs().get(1).contains("B"));
        assertFalse(first.equals(second));
    }

    @Test
    public void testClearLogs() {
        logModel.addLog("TEST", "Message 1");
        logModel.clearLogs();
        assertEquals(0, logModel.getLogCount());
    }
}
