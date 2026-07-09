package com.synapse.skill;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class SkillLoader {
    private static final String SKILLS_DIR = "synapse_skills";
    private final Map<String, SkillDef> skills = new LinkedHashMap<>();
    private final Map<String, SkillDef> draftSkills = new LinkedHashMap<>();
/** 技能加载器 — YAML文件定义 + 三级加载 + 按触发词匹配 */
    public static class SkillDef {
        public String name;
        public String description;
        public String triggerPattern;
        public List<String> steps;
        public String category;
        public String createdAt;
        public String status = "active";
    }
    public SkillLoader() { loadSkills(); }
    private void loadSkills() {
        File base = new File(SKILLS_DIR); if(!base.exists()) base.mkdirs();
        registerBuiltin();
        File[] dirs = base.listFiles(File::isDirectory);
        if(dirs != null) for(File dir : dirs) {
            File sf = new File(dir, "SKILL.md");
            if(sf.exists()) { SkillDef d = parseFile(sf, dir.getName()); if(d != null) skills.put(d.name, d); }
        }
    }
    private void registerBuiltin() {
        SkillDef s1 = new SkillDef(); s1.name = "web-research"; s1.description = "联网搜索并整理资料"; s1.triggerPattern = "搜索"; s1.category = "research"; s1.steps = Arrays.asList("web_search(query=$1)","整理结果到文件"); skills.put(s1.name, s1);
        SkillDef s2 = new SkillDef(); s2.name = "daily-report"; s2.description = "生成日报"; s2.triggerPattern = "日报"; s2.category = "work"; s2.steps = Arrays.asList("收集今日工作","整理日报格式","写入文件"); skills.put(s2.name, s2);
        SkillDef s3 = new SkillDef(); s3.name = "code-review"; s3.description = "审查代码"; s3.triggerPattern = "审查"; s3.category = "dev"; s3.steps = Arrays.asList("读取代码文件","分析质量问题","输出审查报告"); skills.put(s3.name, s3);
    }
    private SkillDef parseFile(File f, String defName) {
        try {
            String c = new String(Files.readAllBytes(f.toPath()));
            SkillDef d = new SkillDef(); d.name = defName;
            if(c.startsWith("---")) { int e = c.indexOf("---",3); if(e>0) for(String l : c.substring(3,e).split("\n")) { String[] kv = l.split(":",2); if(kv.length==2) { if(kv[0].trim().equals("name")) d.name=kv[1].trim(); if(kv[0].trim().equals("description")) d.description=kv[1].trim(); } } }
            if(d.description==null||d.description.isEmpty()) d.description = c.length()>80?c.substring(0,80):c;
            return d;
        } catch(Exception e) { return null; }
    }
    public SkillDef match(String input) {
        String lower = input.toLowerCase();
        for(SkillDef s : skills.values()) {
            if(s.triggerPattern != null && lower.contains(s.triggerPattern.toLowerCase())) return s;
            if(s.name != null && lower.contains(s.name.toLowerCase())) return s;
        }
        return null;
    }
    public void createDraft(String n, String desc, List<String> steps) {
        SkillDef d = new SkillDef(); d.name=n; d.description=desc; d.steps=steps; d.status="draft"; d.createdAt=new Date().toString(); draftSkills.put(n,d);
        try { File dir = new File(SKILLS_DIR,n); dir.mkdirs(); StringBuilder sb = new StringBuilder("---\nname: ").append(n).append("\ndescription: ").append(desc).append("\n---\n"); Files.write(Paths.get(dir.getPath(),"SKILL.md"),sb.toString().getBytes()); } catch(Exception ignored) {}
    }
    public void acceptDraft(String n) { SkillDef d = draftSkills.remove(n); if(d!=null){d.status="active";skills.put(n,d);} }
    public List<SkillDef> getAll() { return new ArrayList<>(skills.values()); }
    public List<SkillDef> getDrafts() { return new ArrayList<>(draftSkills.values()); }
    public String getSystemPrompt() {
        if(skills.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n【可用技能】\n");
        for(SkillDef sd : skills.values()) sb.append("- ").append(sd.name).append(": ").append(sd.description).append("\n");
        sb.append("用户请求匹配时直接使用技能。\n");
        return sb.toString();
    }
}
