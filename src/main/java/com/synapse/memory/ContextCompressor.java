package com.synapse.memory;
import com.synapse.llm.LlmClient;
import com.synapse.model.*;
import java.util.*;
/**
 * 上下文压缩器 — 历史超长时自动摘要压缩，控制 Token 开销
 * 参考 Claude Code 的 context manager 设计：
 * 超过20条时，保留 system prompt + 最近10条，中间部分调 LLM 压缩成一段摘要
 */
public class ContextCompressor {
    private static final int MAX_HISTORY = 20;
    private final LlmClient llm;
    public ContextCompressor(LlmClient llm) { this.llm = llm; }
    public List<Message> compressIfNeeded(List<Message> history) {
        if(history.size() <= MAX_HISTORY) return history;
        List<Message> result = new ArrayList<>();
        for(Message m : history) { if("system".equals(m.role)) { result.add(m); break; } }
        StringBuilder toCompress = new StringBuilder();
        for(int i = 1; i < history.size() - 10; i++) {
            Message m = history.get(i);
            if("user".equals(m.role)) toCompress.append("用户: ").append(m.content).append("\n");
            else if("assistant".equals(m.role) && m.content != null && !m.content.isEmpty()) toCompress.append("助手: ").append(m.content).append("\n");
            else if("tool".equals(m.role) && m.content != null && !m.content.isEmpty()) toCompress.append("工具结果 [").append(m.toolName).append("]: ").append(m.content.length() > 100 ? m.content.substring(0, 100) + "..." : m.content).append("\n");
        }
        if(toCompress.length() > 0) {
            String summary = llm.call(Arrays.asList(
                new Message("system", "请简要总结以下对话的核心内容，保留关键信息，包括工具执行结果。"),
                new Message("user", toCompress.toString())
            ), null).content;
            result.add(new Message("system", "【历史摘要】" + (summary != null ? summary : "")));
        }
        for(int i = Math.max(1, history.size() - 10); i < history.size(); i++) result.add(history.get(i));
        return result;
    }
}
