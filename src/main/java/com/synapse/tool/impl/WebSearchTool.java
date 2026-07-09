package com.synapse.tool.impl;
import com.google.gson.*;
import com.synapse.model.Config;
import com.synapse.model.ToolDef;
import com.synapse.tool.ToolExecutor;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/**
 * 联网搜索工具 — 通过可配置的搜索 API 获取真实结果
 * 支持 DuckDuckGo（默认，无需 Key）、Bing Search API、自定义端点
 * 搜索配置通过 synapse_config.json 或环境变量 SYNAPSE_SEARCH_API_KEY 设置
 */
public class WebSearchTool implements ToolExecutor {
    private final Gson gson = new Gson();

    public String execute(Map<String,Object> args) {
        String q = (String)args.getOrDefault("query","");
        if (q.isEmpty()) return "请提供搜索关键词";

        Config cfg;
        try {
            cfg = Config.load();
        } catch (Exception e) {
            return mockResult(q);
        }

        Config.SearchCfg sc = cfg.search;
        if (sc == null) return mockResult(q);

        try {
            if ("duckduckgo".equals(sc.provider)) {
                return searchDuckDuckGo(q);
            } else if ("bing".equals(sc.provider)) {
                return searchBing(q, sc);
            } else if ("custom".equals(sc.provider) && sc.url != null && !sc.url.isEmpty()) {
                return searchCustom(q, sc);
            } else {
                return searchDuckDuckGo(q);
            }
        } catch (Exception e) {
            return "【搜索失败】" + e.getMessage() + "\n可检查网络连接或搜索 API 配置（/settings）";
        }
    }

    private String searchDuckDuckGo(String q) throws Exception {
        String urlStr = String.format("https://api.duckduckgo.com/?q=%s&format=json&no_html=1&skip_disambig=1",
                URLEncoder.encode(q, "UTF-8"));
        String json = httpGet(urlStr, null, null);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        StringBuilder sb = new StringBuilder("【搜索结果】" + q + "\n\n");

        // AbstractText（摘要）
        if (root.has("AbstractText") && !root.get("AbstractText").isJsonNull()) {
            String abstractText = root.get("AbstractText").getAsString();
            if (!abstractText.isEmpty()) {
                sb.append("📄 摘要: ").append(abstractText).append("\n");
                if (root.has("AbstractURL") && !root.get("AbstractURL").isJsonNull()) {
                    sb.append("🔗 ").append(root.get("AbstractURL").getAsString()).append("\n");
                }
                sb.append("\n");
            }
        }

        // RelatedTopics（相关结果）
        if (root.has("RelatedTopics") && !root.get("RelatedTopics").isJsonNull()) {
            JsonArray topics = root.getAsJsonArray("RelatedTopics");
            int count = 0;
            for (JsonElement te : topics) {
                if (count >= 5) break;
                JsonObject topic = te.getAsJsonObject();
                if (topic.has("Text") && topic.has("FirstURL")) {
                    count++;
                    String text = topic.get("Text").getAsString();
                    String url = topic.get("FirstURL").getAsString();
                    sb.append(count).append(". ").append(text.length() > 150 ? text.substring(0, 150) + "..." : text).append("\n");
                    sb.append("   ").append(url).append("\n\n");
                }
            }
            if (count == 0) sb.append("未找到具体结果条目\n");
        } else {
            sb.append("未找到相关结果\n");
        }

        return sb.toString();
    }

    private String searchBing(String q, Config.SearchCfg sc) throws Exception {
        String urlStr = "https://api.bing.microsoft.com/v7.0/search?q=" + URLEncoder.encode(q, "UTF-8") + "&count=5";
        String apiKey = sc.apiKey.isEmpty() ? System.getenv("SYNAPSE_SEARCH_API_KEY") : sc.apiKey;
        String json = httpGet(urlStr, sc.apiKeyHeader, apiKey);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        StringBuilder sb = new StringBuilder("【搜索结果】" + q + "\n\n");
        if (root.has("webPages") && root.getAsJsonObject("webPages").has("value")) {
            JsonArray values = root.getAsJsonObject("webPages").getAsJsonArray("value");
            int count = 0;
            for (JsonElement ve : values) {
                if (count >= 5) break;
                JsonObject page = ve.getAsJsonObject();
                count++;
                String name = page.has("name") ? page.get("name").getAsString() : "";
                String url = page.has("url") ? page.get("url").getAsString() : "";
                String snippet = page.has("snippet") ? page.get("snippet").getAsString() : "";
                sb.append(count).append(". ").append(name).append("\n");
                sb.append("   ").append(snippet.length() > 150 ? snippet.substring(0, 150) + "..." : snippet).append("\n");
                sb.append("   ").append(url).append("\n\n");
            }
        } else {
            sb.append("未找到结果\n");
        }
        return sb.toString();
    }

    private String searchCustom(String q, Config.SearchCfg sc) throws Exception {
        String urlStr = String.format(sc.url, URLEncoder.encode(q, "UTF-8"));
        String apiKey = sc.apiKey.isEmpty() ? System.getenv("SYNAPSE_SEARCH_API_KEY") : sc.apiKey;
        String json = httpGet(urlStr, sc.apiKeyHeader, apiKey);
        return "【搜索结果】" + q + "\n" + json.substring(0, Math.min(json.length(), 1000));
    }

    private String httpGet(String urlStr, String apiKeyHeader, String apiKey) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "SynapseCLI/0.1.0");
        if (apiKeyHeader != null && apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty(apiKeyHeader, apiKey);
        }
        int code = conn.getResponseCode();
        InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[4096]; int n;
        while ((n = is.read(b)) != -1) buf.write(b, 0, n);
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    /** 无网络或配置时的降级结果 */
    private String mockResult(String q) {
        return "【搜索】\"" + q + "\" 结果:\n1. " + q + " 相关文章\n2. " + q + " 最新进展\n(配置搜索API后可获取真实结果)";
    }

    public ToolDef getDef() {
        ToolDef d = new ToolDef(); d.name = "web_search"; d.description = "搜索网络信息（支持 DuckDuckGo/Bing/自定义）";
        d.parameters = new LinkedHashMap<>(); d.parameters.put("type","object");
        Map<String,Object> props = new LinkedHashMap<>();
        props.put("query", m("type","string","description","搜索关键词"));
        d.parameters.put("properties",props); d.parameters.put("required", l("query")); return d;
    }
    public int permissionLevel() { return 0; }
    private Map<String,Object> m(String...kv) { Map<String,Object> r=new LinkedHashMap<>(); for(int i=0;i<kv.length;i+=2) r.put(kv[i],kv[i+1]); return r; }
    private List<String> l(String...v) { return Arrays.asList(v); }
}
