package com.synapse.memory;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/**
 * 基础记忆存储 — 简单的 kv 文件持久化
 * 存为 synapse_memory.json
 */
public class MemoryStore {
    private static final String FILE = "synapse_memory.json";
    private final Gson gson = new Gson();
    private final Map<String,String> store = new LinkedHashMap<>();
    public MemoryStore() { load(); }
    private void load() { try { File f = new File(FILE); if(f.exists()) { Map<String,String> m = gson.fromJson(new String(Files.readAllBytes(f.toPath())), new TypeToken<Map<String,String>>(){}.getType()); if(m != null) store.putAll(m); } } catch(Exception ignored) {} }
    public void save() { try { Files.write(Paths.get(FILE), gson.toJson(store).getBytes()); } catch(Exception ignored) {} }
    public void put(String key, String value) { store.put(key, value); save(); }
    public String get(String key) { return store.get(key); }
    public String getAll() { if(store.isEmpty()) return "暂无记忆"; StringBuilder sb = new StringBuilder("【记忆】\n"); for(Map.Entry<String,String> e : store.entrySet()) sb.append(e.getKey()).append(": ").append(e.getValue()).append("\n"); return sb.toString(); }
}
