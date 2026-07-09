package com.synapse.memory;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.text.SimpleDateFormat;
/**
 * 增强记忆系统 — 四分类文件持久化
 * 参考 GenericAgent 的分层记忆设计：
 * - preferences:    用户偏好（"回答要简洁"）
 * - tasks:          任务历史（"搜索了AI新闻"）
 * - knowledge:      知识积累（"Java 21支持虚拟线程"）
 * - chat:           对话片段
 * 每类最多50条，自动淘汰最早的。输出到 System Prompt 时每类最多5条。
 */
public class EnhancedMemory {
    private static final String BASE_DIR = "synapse_memory";
    private final Gson gson = new Gson();
    private final Map<String, Map<String,String>> store = new LinkedHashMap<>();
    private static final String[] CATEGORIES = {"preferences","tasks","knowledge","chat"};
    public EnhancedMemory() { init(); }
    private void init() {
        File dir = new File(BASE_DIR); if(!dir.exists()) dir.mkdirs();
        for(String cat:CATEGORIES) {
            File f = new File(BASE_DIR, cat+".json");
            if(f.exists()) { try { Map<String,String> m = gson.fromJson(new String(Files.readAllBytes(f.toPath())),new TypeToken<Map<String,String>>(){}.getType()); if(m!=null) store.put(cat,m); } catch(Exception ignored) {} }
            if(!store.containsKey(cat)) store.put(cat,new LinkedHashMap<>());
        }
    }
    private void persist(String c) { try { Map<String,String> m=store.get(c); if(m!=null) Files.write(Paths.get(BASE_DIR,c+".json"),gson.toJson(m).getBytes()); } catch(Exception ignored) {} }

    /** 通用 put：按分类存储键值对 */
    public void put(String c, String k, String v) {
        if(!store.containsKey(c)) store.put(c,new LinkedHashMap<>());
        store.get(c).put(k,v);
        if(store.get(c).size()>50) { List<Map.Entry<String,String>> e=new ArrayList<>(store.get(c).entrySet()); for(int i=0;i<e.size()-50;i++) store.get(c).remove(e.get(i).getKey()); }
        persist(c);
    }

    /** 通用 get：按分类读取 */
    public String get(String c, String k) {
        Map<String,String> m = store.get(c);
        return m == null ? null : m.get(k);
    }

    /** 快捷方法 */
    public void rememberPref(String k,String v) { put("preferences",k,v); }
    public void rememberTask(String t,String r) { put("tasks",new SimpleDateFormat("MMdd-HHmm").format(new Date())+" "+t,r.length()>200?r.substring(0,200):r); }
    public void rememberKnow(String k,String v) { put("knowledge",k,v); }

    /** 获取所有记忆文本（用于注入 System Prompt） */
    public String getAll() {
        StringBuilder sb = new StringBuilder("\n【记忆】\n"); boolean has=false;
        for(String cat:CATEGORIES) {
            Map<String,String> m=store.get(cat);
            if(m!=null&&!m.isEmpty()) {
                has=true; String cn="preferences".equals(cat)?"偏好":"tasks".equals(cat)?"任务":"knowledge".equals(cat)?"知识":"对话";
                sb.append("[").append(cn).append("]\n"); int cnt=0;
                for(Map.Entry<String,String> e:m.entrySet()) { if(cnt++>=5){sb.append("  ...还有").append(m.size()-5).append("条\n");break;} sb.append("  ").append(e.getKey()).append(": ").append(e.getValue().length()>80?e.getValue().substring(0,80)+"...":e.getValue()).append("\n"); }
            }
        }
        if(!has) sb.append("  暂无记忆\n");
        return sb.toString();
    }

    /** 清空指定分类 */
    public void clear(String cat) {
        Map<String,String> m = store.get(cat);
        if (m != null) { m.clear(); persist(cat); }
    }

    /** 清空所有记忆 */
    public void clearAll() {
        for (String cat : CATEGORIES) clear(cat);
    }
}
