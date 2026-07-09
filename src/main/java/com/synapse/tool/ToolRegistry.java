package com.synapse.tool;
import com.synapse.model.ToolDef;
import java.util.*;
/**
 * 工具注册中心 — 启动时收集所有 ToolExecutor 实现
 * 参考 Spring 的 @Component 扫描思路，但没有使用 Spring
 * 新增工具只需 register()，不需要改其他代码
 */
public class ToolRegistry {
    public final Map<String,ToolExecutor> tools = new LinkedHashMap<>();
    public void register(ToolExecutor t) { tools.put(t.getDef().name, t); }
    public ToolExecutor get(String name) { return tools.get(name); }
    public List<ToolDef> defs() { List<ToolDef> l = new ArrayList<>(); for(ToolExecutor t:tools.values()) l.add(t.getDef()); return l; }
    public int size() { return tools.size(); }
}
