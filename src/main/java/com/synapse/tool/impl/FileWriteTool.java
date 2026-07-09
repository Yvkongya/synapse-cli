package com.synapse.tool.impl;
import com.synapse.model.ToolDef;
import com.synapse.tool.ToolExecutor;
import java.nio.file.*;
import java.util.*;
/** 文件写入工具 — 需用户确认（Level 1），限制在工作目录内，禁止覆盖关键配置文件 */
public class FileWriteTool implements ToolExecutor {
    private static final List<String> PROTECTED_FILES = Arrays.asList(
        "synapse_config.json", ".env", ".git/config", "pom.xml"
    );

    public String execute(Map<String,Object> args) {
        String path = (String)args.getOrDefault("path",""), content = (String)args.getOrDefault("content","");
        if(path.isEmpty()) return "请提供文件路径";
        try {
            Path target = Paths.get(path).normalize().toAbsolutePath();
            Path cwd = Paths.get(".").toAbsolutePath().normalize();
            if (!target.startsWith(cwd)) {
                return "【安全拦截】不允许写入工作目录外的文件: " + target;
            }
            String relPath = cwd.relativize(target).toString().replace("\\", "/");
            for (String p : PROTECTED_FILES) {
                if (relPath.equals(p) || relPath.startsWith(p + "/")) {
                    return "【安全拦截】不允许覆盖关键配置文件: " + p;
                }
            }
            Files.createDirectories(target.getParent());
            Files.write(target, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return "文件已写入: " + relPath;
        }
        catch(Exception e) { return "写入失败: "+e.getMessage(); }
    }
    public ToolDef getDef() {
        ToolDef d = new ToolDef(); d.name = "file_write"; d.description = "写入文件";
        Map<String,Object> props = new LinkedHashMap<>();
        Map<String,Object> p1 = new LinkedHashMap<>(); p1.put("type","string"); p1.put("description","文件路径（仅限工作目录内）"); props.put("path",p1);
        Map<String,Object> p2 = new LinkedHashMap<>(); p2.put("type","string"); p2.put("description","文件内容"); props.put("content",p2);
        Map<String,Object> schema = new LinkedHashMap<>(); schema.put("type","object"); schema.put("properties",props);
        List<String> req = new ArrayList<>(); req.add("path"); req.add("content"); schema.put("required",req);
        d.parameters = schema; return d;
    }
    public int permissionLevel() { return 1; }
}
