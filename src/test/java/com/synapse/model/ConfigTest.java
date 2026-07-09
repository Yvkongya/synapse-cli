package com.synapse.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void defaultConfig_usesDeepSeek() {
        Config cfg = new Config();
        assertEquals("deepseek", cfg.activeProvider);
        assertEquals(1, cfg.providers.size());
        assertEquals("DeepSeek", cfg.providers.get(0).name);
    }

    @Test
    void activeProvider_returnsCorrect() {
        Config cfg = new Config();
        Config.ProviderCfg p = cfg.active();
        assertNotNull(p);
        assertEquals("deepseek", p.id);
    }

    @Test
    void providerCfg_defaults() {
        Config.ProviderCfg p = new Config.ProviderCfg();
        assertEquals("https://api.deepseek.com/v1", p.url);
        assertEquals("deepseek-chat", p.model);
        assertEquals("", p.apiKey);
    }

    @Test
    void searchCfg_defaults() {
        Config.SearchCfg sc = new Config.SearchCfg();
        assertEquals("duckduckgo", sc.provider);
        assertTrue(sc.url.contains("duckduckgo.com"));
    }
}
