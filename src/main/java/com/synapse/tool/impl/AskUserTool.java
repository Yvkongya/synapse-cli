package com.synapse.tool.impl;
import com.synapse.model.ToolDef;
import com.synapse.tool.ToolExecutor;
import java.util.*;
/** 向用户提问工具 — Agent 需要补充信息时使用 */
public class AskUserTool implements ToolExecutor {
    public String execute(Map<String,Object> args) {
        String q = (String)args.getOrDefault("question","");
        return "【需要用户确认】"+q+" (请在终端输入 yes/no 或直接回复)";
    }
    public ToolDef getDef() {
        ToolDef d = new ToolDef(); d.name = "ask_user"; d.description = "向用户提问";
        Map<String,Object> props = new LinkedHashMap<>();
        Map<String,Object> q = new LinkedHashMap<>(); q.put("type","string"); q.put("description","问题内容"); props.put("question",q);
        Map<String,Object> schema = new LinkedHashMap<>(); schema.put("type","object"); schema.put("properties",props);
        List<String> req = new ArrayList<>(); req.add("question"); schema.put("required",req);
        d.parameters = schema; return d;
    }
    public int permissionLevel() { return 0; }
}
