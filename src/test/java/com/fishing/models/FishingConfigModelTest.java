package com.fishing.models;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class FishingConfigModelTest {
    private static final Path CONFIG_FILE = Paths.get("data", "app_config.json");
    private FishingConfigModel configModel;

    @Before
    public void setUp() {
        deleteConfigFile();
        configModel = new FishingConfigModel();
    }

    @After
    public void tearDown() {
        deleteConfigFile();
    }

    private void deleteConfigFile() {
        try {
            Files.deleteIfExists(CONFIG_FILE);
            Files.deleteIfExists(CONFIG_FILE.resolveSibling("app_config.json.tmp"));
        } catch (IOException ignored) {}
    }

    @Test
    public void testDefaultConfigValues() {
        assertEquals("", configModel.getUsername());
        assertEquals("", configModel.getPassword());
        assertEquals("Tự động (Auto)", configModel.getRunMode());
        assertEquals(2, configModel.getDelaySeconds());
        assertFalse(configModel.isAutoStart());
    }

    @Test
    public void testSaveAndReloadConfig() {
        configModel.setUsername("user_test_99");
        configModel.setPassword("secret123");
        configModel.setRunMode("Không giới hạn (Unlimited)");
        configModel.setDelaySeconds(5);
        configModel.setAutoStart(true);

        configModel.saveConfig();

        // Create a new instance to simulate restarting the tool
        FishingConfigModel reloaded = new FishingConfigModel();

        assertEquals("user_test_99", reloaded.getUsername());
        assertEquals("secret123", reloaded.getPassword());
        assertEquals("Không giới hạn (Unlimited)", reloaded.getRunMode());
        assertEquals(5, reloaded.getDelaySeconds());
        assertTrue(reloaded.isAutoStart());
    }
}
