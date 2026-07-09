package com.synapse.model;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/**
 * 配置文件管理 — 多 Provider LLM 配置 + 搜索配置
 * 存为 synapse_config.json，支持 DeepSeek/OpenAI 等
 * API Key 优先读取环境变量 SYNAPSE_API_KEY，再回退到配置文件
 */
public class Config {
    private static final String FILE = "synapse_config.json";
    private static final String ENV_API_KEY = "SYNAPSE_API_KEY";
    private static final String ENV_SEARCH_API_KEY = "SYNAPSE_SEARCH_API_KEY";

    public String activeProvider = "deepseek";
    public List<ProviderCfg> providers = new ArrayList<>();
    public SearchCfg search = new SearchCfg();

    public static class ProviderCfg {
        public String id = "deepseek"; public String name = "DeepSeek";
        public String url = "https://api.deepseek.com/v1"; public String model = "deepseek-chat";
        public String apiKey = ""; public boolean active = true;
    }

    public static class SearchCfg {
        public String provider = "duckduckgo"; // duckduckgo | bing | custom
        public String url = "https://api.duckduckgo.com/?q=%s&format=json&no_html=1";
        public String apiKey = "";
        public String apiKeyHeader = "Ocp-Apim-Subscription-Key"; // for Bing
        public String resultSelector = "RelatedTopics"; // JSON path to results
    }

    public static Config load() {
        try {
            File f = new File(FILE);
            Config c;
            if (f.exists()) {
                c = new Gson().fromJson(new String(Files.readAllBytes(f.toPath())), Config.class);
            } else {
                c = def();
            }
            // 环境变量覆盖 API Key
            String envKey = System.getenv(ENV_API_KEY);
            if (envKey != null && !envKey.isEmpty()) {
                for (ProviderCfg p : c.providers) {
                    p.apiKey = envKey;
                }
            }
            String envSearchKey = System.getenv(ENV_SEARCH_API_KEY);
            if (envSearchKey != null && !envSearchKey.isEmpty()) {
                c.search.apiKey = envSearchKey;
            }
            return c;
        } catch (Exception e) { return def(); }
    }
    public void save() { try { Files.write(Paths.get(FILE), new Gson().toJson(this).getBytes()); } catch(Exception ignored) {} }
    private static Config def() { Config c = new Config(); c.providers.add(new ProviderCfg()); c.save(); return c; }
    public ProviderCfg active() { for(ProviderCfg p:providers) if(p.id.equals(activeProvider)) return p; return providers.isEmpty()?new ProviderCfg():providers.get(0); }
}
