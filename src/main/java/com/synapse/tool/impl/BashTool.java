package com.synapse.tool.impl;
import com.synapse.model.ToolDef;
import com.synapse.tool.ToolExecutor;
import java.io.*;
import java.util.*;
/** Shell 命令执行 — 白名单命令 + 参数安全检查 + 危险操作强制审批 */
public class BashTool implements ToolExecutor {
    private static final Set<String> SAFE_COMMANDS = new HashSet<>(Arrays.asList(
        "ls","dir","cat","echo","pwd","whoami","date","head","tail","find","grep","wc","sort","uniq","which"
    ));
    private static final List<String> DANGEROUS_PATTERNS = Arrays.asList(";", "|", "&&", "||", "`", "$(", "$", ">", "<", "&", "|&");

    public String execute(Map<String,Object> args) {
        String cmd = ((String)args.getOrDefault("command","")).trim();
        if(cmd.isEmpty()) return "请提供命令";

        // 提取命令名（第一个 token）
        String[] parts = cmd.split("\\s+", 2);
        String cmdName = parts[0].toLowerCase();

        // 白名单检查
        if (!SAFE_COMMANDS.contains(cmdName)) {
            return "【安全拦截】命令不允许: " + cmdName + "（白名单: " + String.join(", ", SAFE_COMMANDS) + "）";
        }

        // 阻止危险 shell 操作符（find -exec 属于正常使用，放行）
        String rest = parts.length > 1 ? parts[1] : "";
        if (cmdName.equals("echo") || cmdName.equals("cat")) {
            // echo/cat 不应该包含重定向或管道
            for (String p : DANGEROUS_PATTERNS) {
                if (rest.contains(p)) {
                    return "【安全拦截】参数包含危险操作符: " + p;
                }
            }
        }

        try {
            // 使用 ProcessBuilder 避免 shell 解释
            List<String> cmdList = new ArrayList<>(Arrays.asList(parts));
            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder(); String line;
            while((line = r.readLine()) != null) sb.append(line).append("\n");
            p.waitFor();
            return sb.toString();
        } catch(Exception e) { return "执行失败: "+e.getMessage(); }
    }
    public ToolDef getDef() {
        ToolDef d = new ToolDef(); d.name = "bash"; d.description = "执行系统命令（白名单: ls/cat/echo/pwd 等只读命令）";
        Map<String,Object> props = new LinkedHashMap<>();
        Map<String,Object> c = new LinkedHashMap<>(); c.put("type","string"); c.put("description","要执行的命令（仅白名单命令，不支持管道/重定向）"); props.put("command",c);
        Map<String,Object> schema = new LinkedHashMap<>(); schema.put("type","object"); schema.put("properties",props);
        List<String> req = new ArrayList<>(); req.add("command"); schema.put("required",req);
        d.parameters = schema; return d;
    }
    public int permissionLevel() { return 2; }
}
