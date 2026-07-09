package com.synapse.mcp;
import com.google.gson.*;
import com.synapse.model.ToolDef;
import java.io.*;
import java.util.*;
/**
 * MCP 协议客户端 — 基于 JSON-RPC 2.0 over stdio
 * 
 * MCP = Model Context Protocol，2025 年 Agent 生态标准协议
 * 让 Agent 可以动态发现和调用外部工具（GitHub/浏览器/数据库等）
 * 
 * 通信流程：
 * 1. initialize → 握手协商版本
 * 2. tools/list → 获取工具列表（动态发现）
 * 3. tools/call → 调用具体工具
 * 
 * 消息格式：Content-Length: {len}\r\n\r\n{json}
 * 通过进程的 stdin/stdout 传输
 */
public class McpClient {
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private final Gson gson = new Gson();
    private int reqId = 0;
    private boolean connected = false;

    /** 连接 MCP Server。command 为可执行文件路径，args 为参数列表 */
    public boolean connect(String command, List<String> args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(command);
            if (args != null) cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            process = pb.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            connected = true;
            // 初始化握手
            JsonObject init = new JsonObject();
            init.addProperty("jsonrpc", "2.0"); init.addProperty("id", ++reqId); init.addProperty("method", "initialize");
            JsonObject params = new JsonObject();
            JsonObject clientInfo = new JsonObject(); clientInfo.addProperty("name", "synapse-cli"); clientInfo.addProperty("version", "0.1.0");
            params.add("clientInfo", clientInfo); params.addProperty("protocolVersion", "2025-03-26");
            init.add("params", params);
            send(init);
            JsonObject resp = recv();
            if (resp != null && resp.has("result")) {
                JsonObject notif = new JsonObject(); notif.addProperty("jsonrpc", "2.0"); notif.addProperty("method", "notifications/initialized");
                send(notif);
                return true;
            }
        } catch (Exception e) { System.err.println("  MCP 连接失败: " + e.getMessage()); }
        return false;
    }

    /** 获取 MCP Server 提供的工具列表 */
    public List<ToolDef> listTools() {
        List<ToolDef> result = new ArrayList<>();
        if (!connected) return result;
        try {
            JsonObject req = new JsonObject(); req.addProperty("jsonrpc","2.0"); req.addProperty("id",++reqId); req.addProperty("method","tools/list");
            send(req);
            JsonObject resp = recv();
            if (resp != null && resp.has("result")) {
                JsonArray tools = resp.getAsJsonObject("result").getAsJsonArray("tools");
                for (JsonElement te : tools) {
                    JsonObject t = te.getAsJsonObject();
                    ToolDef d = new ToolDef(); d.name = t.get("name").getAsString(); d.description = t.has("description")?t.get("description").getAsString():"";
                    if (t.has("inputSchema")) d.parameters = gson.fromJson(t.get("inputSchema"), Map.class);
                    result.add(d);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    /** 调用 MCP 工具的 tools/call 方法 */
    public String callTool(String name, Map<String,Object> args) {
        if (!connected) return "MCP 未连接";
        try {
            JsonObject req = new JsonObject(); req.addProperty("jsonrpc","2.0"); req.addProperty("id",++reqId); req.addProperty("method","tools/call");
            JsonObject params = new JsonObject(); params.addProperty("name", name); params.add("arguments", gson.toJsonTree(args));
            req.add("params", params);
            send(req);
            JsonObject resp = recv();
            if (resp != null && resp.has("result")) {
                JsonArray content = resp.getAsJsonObject("result").getAsJsonArray("content");
                StringBuilder sb = new StringBuilder();
                for (JsonElement ce : content) { JsonObject c = ce.getAsJsonObject(); if ("text".equals(c.get("type").getAsString())) sb.append(c.get("text").getAsString()); }
                return sb.toString();
            }
            return "MCP 调用失败";
        } catch (Exception e) { return "MCP 错误: " + e.getMessage(); }
    }

    public void close() { try { if (process != null) process.destroy(); } catch (Exception ignored) {} }

    private void send(JsonObject msg) throws IOException {
        String json = gson.toJson(msg);
        writer.write("Content-Length: " + json.getBytes("UTF-8").length + "\r\n\r\n" + json);
        writer.flush();
    }

    private JsonObject recv() throws IOException {
        String line; int len = 0;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("Content-Length: ")) {
                len = Integer.parseInt(line.substring(16).trim());
            } else if (line.isEmpty() && len > 0) {
                // 循环读取直到收满 len 字节（reader.read 不保证一次读满）
                char[] buf = new char[len];
                int totalRead = 0;
                while (totalRead < len) {
                    int n = reader.read(buf, totalRead, len - totalRead);
                    if (n == -1) throw new IOException("MCP 流提前关闭");
                    totalRead += n;
                }
                return JsonParser.parseString(new String(buf)).getAsJsonObject();
            }
        }
        return null;
    }
}
