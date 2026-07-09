package com.synapse.model;
/**
 * 工具调用消息体 — 对应 LLM 返回的 tool_calls 中的每一项
 * id: 工具调用唯一 ID
 * function.name: 工具名
 * function.arguments: JSON 格式的参数
 */
public class ToolCallMsg {
    public String id;
    public String type = "function";
    public FunctionCall function;
    public static class FunctionCall {
        public String name;
        public String arguments;
    }
}
