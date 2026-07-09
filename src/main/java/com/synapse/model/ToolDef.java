package com.synapse.model;
import com.google.gson.*;
import java.util.*;
/**
 * 工具定义 — 对应 LLM Function Calling 的 tools 参数
 * 每个工具需要提供：name（唯一标识）、description（让 LLM 知道什么时候用）、parameters（JSON Schema）
 */
public class ToolDef {
    private static final Gson GSON = new Gson();
    public String name;
    public String description;
    public Map<String,Object> parameters;
    /** 转成 LLM API 所需的 JSON 格式 */
    public String toJson() {
        JsonObject outer = new JsonObject();
        outer.addProperty("type", "function");
        JsonObject fn = new JsonObject();
        fn.addProperty("name", name);
        fn.addProperty("description", description);
        fn.add("parameters", parameters == null ? new JsonObject() : GSON.toJsonTree(parameters));
        outer.add("function", fn);
        return GSON.toJson(outer);
    }
}
