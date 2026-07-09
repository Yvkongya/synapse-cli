package com.synapse.tool;
import com.synapse.model.ToolDef;
import java.util.*;
/**
 * 工具执行器接口 — 所有工具必须实现此接口
 * 5 个维度（参考 Claude Code 的工具设计）：
 * 1. getDef()     — 工具的身份和 Schema
 * 2. execute()    — 执行逻辑
 * 3. permissionLevel() — 权限级别
 */
public interface ToolExecutor {
    String execute(Map<String,Object> args);
    ToolDef getDef();
    int permissionLevel(); // 0=自动, 1=需确认, 2=危险
}
