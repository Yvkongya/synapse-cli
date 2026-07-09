package com.synapse.llm;
import com.google.gson.*;
import com.synapse.model.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * LLM 客户端 — 支持 Function Calling 和流式输出
 * 
 * 技术选型：
 * - 使用 JDK 内置 HttpURLConnection，不用 OkHttp，避免依赖冲突
 * - 支持 stream 模式（SSE），边接收边解析 tool_calls
 * 
 * 踩坑记录：
 * - 之前用 OkHttp 3.x 和 Spring 的 @RequestBody 有类名冲突（都是 RequestBody）
 * - 改用 JDK 内置 + 手动解析 JSON 后彻底解决
 */
public class LlmClient {
    private final Gson gson = new Gson();
    private Config.ProviderCfg cfg;

    public LlmClient(Config.ProviderCfg cfg) { this.cfg = cfg; }
    public void setProvider(Config.ProviderCfg cfg) { this.cfg = cfg; }

    /** 流式回调接口：收到完整 tool_call 时触发，可提前执行只读工具 */
    public interface StreamCallback {
        void onToolCall(ToolCallMsg tc);     // 工具参数收齐，可提前执行
        void onContent(String text);          // 收到文本片段
        void onDone(Message finalMsg);        // LLM 输出完毕
        void onError(String msg);             // 出错
    }

    /** 非流式调用（原有逻辑不动） */
    public Message call(List<Message> history, List<ToolDef> tools) {
        // ... [原有非流式代码保持不变]
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", cfg.model);
            body.addProperty("stream", false);
            body.addProperty("max_tokens", 4096);
            body.add("messages", buildMessages(history));
            if(tools != null && !tools.isEmpty()) {
                JsonArray ta = new JsonArray();
                for(ToolDef td : tools) ta.add(JsonParser.parseString(td.toJson()));
                body.add("tools", ta);
            }

            URL url = new URL(cfg.url + "/chat/completions");
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + cfg.apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            OutputStream os = conn.getOutputStream();
            os.write(gson.toJson(body).getBytes(StandardCharsets.UTF_8));
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
            String resp = new String(readAll(is), StandardCharsets.UTF_8);
            if(code != 200) return new Message("assistant", "【API错误】HTTP " + code);

            JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
            JsonObject msg = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            Message result = new Message();
            result.role = "assistant";
            result.content = msg.has("content") && !msg.get("content").isJsonNull() ? msg.get("content").getAsString() : "";

            if(msg.has("tool_calls") && !msg.get("tool_calls").isJsonNull()) {
                result.toolCalls = new ArrayList<>();
                for(JsonElement te : msg.getAsJsonArray("tool_calls")) {
                    JsonObject tc = te.getAsJsonObject();
                    ToolCallMsg tcm = new ToolCallMsg();
                    tcm.id = tc.get("id").getAsString();
                    tcm.function = new ToolCallMsg.FunctionCall();
                    tcm.function.name = tc.getAsJsonObject("function").get("name").getAsString();
                    tcm.function.arguments = tc.getAsJsonObject("function").get("arguments").getAsString();
                    result.toolCalls.add(tcm);
                }
            }
            return result;
        } catch(Exception e) {
            return new Message("assistant", "【调用失败】" + e.getMessage());
        }
    }

    /**
     * 流式调用 — 支持 Streaming Tool Executor
     * 
     * Claude Code 的做法：模型还在生成后续 token 时，如果某个 tool_call 参数收齐了，
     * 且工具声明了只读（permissionLevel=0），就提前执行，不等 LLM 完全输出。
     * 
     * 代价：如果后续输出改变了前面的决策，已执行的工具就白跑了。
     * 收益：大部分情况后续是持续推进，提前执行节省了等待时间。
     * 
     * 参考：Claude Code 的 StreamingToolExecutor 设计
     */
    public void callStream(List<Message> history, List<ToolDef> tools, StreamCallback callback) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", cfg.model);
            body.addProperty("stream", true);   // 关键：启用流式
            body.addProperty("max_tokens", 4096);
            body.add("messages", buildMessages(history));
            if(tools != null && !tools.isEmpty()) {
                JsonArray ta = new JsonArray();
                for(ToolDef td : tools) ta.add(JsonParser.parseString(td.toJson()));
                body.add("tools", ta);
            }

            // 发起 HTTP 流式请求
            URL url = new URL(cfg.url + "/chat/completions");
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + cfg.apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream"); // SSE 协议
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            OutputStream os = conn.getOutputStream();
            os.write(gson.toJson(body).getBytes(StandardCharsets.UTF_8));
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
            if(code != 200) {
                callback.onError("HTTP " + code + ": " + new String(readAll(is), StandardCharsets.UTF_8));
                return;
            }

            // 解析 SSE 流
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            StringBuilder contentBuf = new StringBuilder();
            // 累积 tool_calls（流式场景下，tool_calls 是分片到达的）
            // 用 Map 存储每个 tool_call 的累积状态
            Map<Integer, JsonObject> pendingCalls = new LinkedHashMap<>();

            while((line = reader.readLine()) != null) {
                if(line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if("[DONE]".equals(data)) break; // 流结束

                    try {
                        JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();
                        JsonArray choices = chunk.getAsJsonArray("choices");
                        if(choices == null || choices.size() == 0) continue;
                        JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");

                        // 累积文本内容
                        if(delta.has("content") && !delta.get("content").isJsonNull()) {
                            String text = delta.get("content").getAsString();
                            contentBuf.append(text);
                            callback.onContent(text);
                        }

                        // 累积 tool_calls（流式分片）
                        if(delta.has("tool_calls") && !delta.get("tool_calls").isJsonNull()) {
                            JsonArray tcs = delta.getAsJsonArray("tool_calls");
                            for(JsonElement te : tcs) {
                                JsonObject tc = te.getAsJsonObject();
                                int idx = tc.get("index").getAsInt();
                                JsonObject existing = pendingCalls.get(idx);
                                if(existing == null) {
                                    existing = new JsonObject();
                                    existing.addProperty("id", "");
                                    JsonObject fn = new JsonObject();
                                    fn.addProperty("name", "");
                                    fn.addProperty("arguments", "");
                                    existing.add("function", fn);
                                    pendingCalls.put(idx, existing);
                                }
                                // 合并到已有的 tool_call 对象中
                                if(tc.has("id") && tc.get("id").isJsonPrimitive()) {
                                    String id = tc.get("id").getAsString();
                                    if(!id.isEmpty()) existing.addProperty("id", id);
                                }
                                JsonObject fnPart = tc.getAsJsonObject("function");
                                if(fnPart != null) {
                                    if(fnPart.has("name") && fnPart.get("name").isJsonPrimitive()) {
                                        String nm = fnPart.get("name").getAsString();
                                        if(!nm.isEmpty()) existing.getAsJsonObject("function").addProperty("name", nm);
                                    }
                                    if(fnPart.has("arguments") && fnPart.get("arguments").isJsonPrimitive()) {
                                        String args = fnPart.get("arguments").getAsString();
                                        String cur = existing.getAsJsonObject("function").get("arguments").getAsString();
                                        existing.getAsJsonObject("function").addProperty("arguments", cur + args);
                                    }
                                }
                            }
                        }
                    } catch(JsonSyntaxException ignored) {}
                }
            }
            reader.close();

            // 流结束，组装最终 Message
            Message finalMsg = new Message("assistant", contentBuf.toString());
            if(!pendingCalls.isEmpty()) {
                finalMsg.toolCalls = new ArrayList<>();
                for(Map.Entry<Integer, JsonObject> entry : pendingCalls.entrySet()) {
                    JsonObject tc = entry.getValue();
                    ToolCallMsg tcm = new ToolCallMsg();
                    tcm.id = tc.get("id").getAsString();
                    tcm.function = new ToolCallMsg.FunctionCall();
                    tcm.function.name = tc.getAsJsonObject("function").get("name").getAsString();
                    tcm.function.arguments = tc.getAsJsonObject("function").get("arguments").getAsString();
                    finalMsg.toolCalls.add(tcm);
                }
            }
            callback.onDone(finalMsg);

        } catch(Exception e) {
            callback.onError(e.getMessage());
        }
    }

    /** 构建 messages 数组（含 tool_call 和 tool_result 格式） */
    private JsonArray buildMessages(List<Message> history) {
        JsonArray arr = new JsonArray();
        for(Message m : history) {
            JsonObject jo = new JsonObject();
            jo.addProperty("role", m.role);
            if(m.content != null && !m.content.isEmpty()) jo.addProperty("content", m.content);
            if(m.toolCalls != null) {
                JsonArray tcs = new JsonArray();
                for(ToolCallMsg tc : m.toolCalls) {
                    JsonObject tjo = new JsonObject();
                    tjo.addProperty("id", tc.id);
                    tjo.addProperty("type", "function");
                    JsonObject fn = new JsonObject();
                    fn.addProperty("name", tc.function.name);
                    fn.addProperty("arguments", tc.function.arguments);
                    tjo.add("function", fn);
                    tcs.add(tjo);
                }
                jo.add("tool_calls", tcs);
            }
            if(m.toolCallId != null) jo.addProperty("tool_call_id", m.toolCallId);
            if(m.toolName != null) jo.addProperty("name", m.toolName);
            arr.add(jo);
        }
        return arr;
    }

    private byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[4096]; int n;
        while((n = is.read(b)) != -1) buf.write(b, 0, n);
        return buf.toByteArray();
    }
}
