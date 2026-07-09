package com.synapse.tool.impl;
import com.synapse.model.ToolDef;
import com.synapse.tool.ToolExecutor;
import java.nio.file.*;
import java.util.*;
/** 文件读取工具 — 读取本地文件内容（限制在工作目录内） */
public class FileReadTool implements ToolExecutor {
    public String execute(Map<String,Object> args) {
        String path = (String)args.getOrDefault("path","");
        if(path.isEmpty()) return "请提供文件路径";
        try {
            Path target = Paths.get(path).normalize().toAbsolutePath();
            Path cwd = Paths.get(".").toAbsolutePath().normalize();
            if (!target.startsWith(cwd)) {
                return "【安全拦截】不允许读取工作目录外的文件: " + target;
            }
            return new String(Files.readAllBytes(target));
        }
        catch(Exception e) { return "读取失败: "+e.getMessage(); }
    }
    public ToolDef getDef() {
        ToolDef d = new ToolDef(); d.name = "file_read"; d.description = "读取文件内容";
        Map<String,Object> props = new LinkedHashMap<>();
        Map<String,Object> p = new LinkedHashMap<>(); p.put("type","string"); p.put("description","文件路径（仅限工作目录内）");
        props.put("path",p);
        Map<String,Object> schema = new LinkedHashMap<>(); schema.put("type","object"); schema.put("properties",props);
        List<String> req = new ArrayList<>(); req.add("path"); schema.put("required",req);
        d.parameters = schema; return d;
    }
    public int permissionLevel() { return 0; }
}
