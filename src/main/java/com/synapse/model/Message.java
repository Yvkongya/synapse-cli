package com.synapse.model;
import java.util.*;
/**
 * LLM 对话消息模型
 * role: system / user / assistant / tool
 * toolCalls: assistant 消息中携带的工具调用
 * toolCallId / toolName: tool 消息中回传的工具调用 ID
 */
public class Message {
    public String role;
    public String content;
    public List<ToolCallMsg> toolCalls;
    public String toolCallId;
    public String toolName;
    public Message() {}
    public Message(String role, String content) { this.role = role; this.content = content; }
}
