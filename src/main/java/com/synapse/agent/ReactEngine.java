package com.synapse.agent;
import com.google.gson.*;
import com.synapse.llm.LlmClient;
import com.synapse.memory.ContextCompressor;
import com.synapse.memory.EnhancedMemory;
import com.synapse.model.*;
import com.synapse.tool.ToolExecutor;
import com.synapse.tool.ToolRegistry;
import java.util.*;

/**
 * Agent 核心引擎 — ReAct 决策循环
 *
 * 参考 Claude Code 的 Query Loop 设计，实现 Thought→Action→Observation 循环。
 * 核心差异：
 * 1. Claude Code 用 Async Generator（pull 模式），我们用的是 while 循环（push 模式）
 * 2. 增加了 Auto 权限模式：用 LLM 判断操作是否和用户意图一致
 * 3. 支持 Streaming Tool Executor：只读工具在流式解析时提前执行
 *
 * 踩坑记录：
 * - 之前用文本 `TOOL:` 前缀让 LLM 输出工具调用，格式匹配率极低
 * - 改用 Function Calling API 后，tool_calls 是结构化 JSON，100% 解析成功
 * - ToolRunner 改为串行执行：并行执行时多个工具审批抢 Scanner，且并发写 history 不安全
 */
public class ReactEngine {
    private final LlmClient llm;
    private final ToolRegistry registry;
    private final EnhancedMemory memory;
    private final ContextCompressor compressor;
    private final Scanner scanner = new Scanner(System.in);
    private static final int MAX_TURNS = 15;
    private boolean autoMode = true; // Auto 权限模式开关

    public ReactEngine(LlmClient llm, ToolRegistry registry) {
        this.llm = llm; this.registry = registry;
        this.memory = new EnhancedMemory();
        this.compressor = new ContextCompressor(llm);
    }

    /**
     * 执行 ReAct 循环
     * @param userInput 用户输入
     * @param planMode  是否启用 Plan Mode
     */
    public void execute(String userInput, boolean planMode) {
        List<Message> history = new ArrayList<>();
        String sys = "你是Synapse，一个智能AI助手。你可以通过工具调用完成任务。" +
                "遵循ReAct模式：思考→行动→观察。任务完成时直接给出最终答案。";
        history.add(new Message("system", sys + "\n\n" + memory.getAll()));
        if (planMode) System.out.println("\n  📋 Plan Mode 已启用\n");
        history.add(new Message("user", userInput));

        for (int turn = 0; turn < MAX_TURNS; turn++) {
            history = compressor.compressIfNeeded(history);

            // ===== 正常非流式 ReAct（兼容原有逻辑） =====
            Message resp = llm.call(history, registry.defs());

            if (resp.content != null && resp.content.startsWith("【")) {
                System.out.println("  ⚠️ " + resp.content);
                if (resp.content.contains("401") || resp.content.contains("Key")) {
                    System.out.println("  💡 请配置正确的 API Key");
                    return;
                }
                continue;
            }
            if (resp.content != null && !resp.content.isEmpty()) {
                System.out.println("  💭 " + resp.content);
            }
            if (resp.toolCalls == null || resp.toolCalls.isEmpty()) {
                history.add(resp);
                if (resp.content != null) memory.rememberTask(userInput, resp.content);
                return;
            }

            history.add(resp);
            // 串行执行工具（避免并行时审批抢 Scanner + 并发写 history）
            for (ToolCallMsg tc : resp.toolCalls) {
                new ToolRunner(tc, history, userInput).run();
            }
        }
        System.out.println("  ⚠️ 已达最大执行轮次");
    }

    /**
     * Tool 执行器（实现了 Auto 权限模式 + 三级 HITL）
     * 
     * Auto 权限模式（参考 Claude Code 的 auto mode）：
     * 用 LLM 判断当前操作是否和用户原始意图一致。
     * - 一致 → 自动放行，不需要用户确认
     * - 不一致 → 交用户人工确认
     * 
     * 优点：同一个工具在不同场景下表现不同。写 login.ts 自动放行，写 config 要确认。
     * 代价：每次权限检查多一次轻量 LLM 调用。
     */
    class ToolRunner implements Runnable {
        ToolCallMsg tc; List<Message> history; String userInput;
        ToolRunner(ToolCallMsg tc, List<Message> h, String ui) { this.tc = tc; this.history = h; this.userInput = ui; }
        
        public void run() {
            String name = tc.function.name;
            ToolExecutor tool = registry.get(name);
            if (tool == null) {
                System.out.println("  ❌ 工具不存在: " + name);
                return;
            }

            // ===== 权限检查（先 Auto 分类，再回退到硬编码级别） =====
            int pl = tool.permissionLevel();
            if (autoMode && pl > 0) {
                // Auto 模式：让 LLM 判断操作是否和用户意图一致
                if (!autoApprove(name, tc.function.arguments)) {
                    // LLM 判断不一致 → 交人工确认
                    pl = 2; // 提升到最高级别，强制用户确认
                    System.out.println("  🤔 [Auto] 操作似乎和原始需求不太一致，请确认：");
                } else {
                    pl = 0; // LLM 判断一致 → 自动放行
                }
            }

            // 三级 HITL 审批（参考 Claude Code 的 permission model）
            if (pl >= 2) {
                System.out.print("  ⚠️ 危险操作 [" + name + "]，确认? (y/N): ");
                if (!scanner.nextLine().trim().toLowerCase().matches("y|yes")) {
                    System.out.println("  ⛔ 已拒绝");
                    addToolResult("用户拒绝", tc, history);
                    return;
                }
            } else if (pl == 1) {
                System.out.print("  🤔 允许 [" + name + "]? (Y/n): ");
                String c = scanner.nextLine().trim().toLowerCase();
                if (c.equals("n") || c.equals("no")) {
                    System.out.println("  ⛔ 已跳过");
                    addToolResult("用户跳过", tc, history);
                    return;
                }
            }
            // pl == 0: 自动执行，不需要用户干预

            // 解析参数
            Map<String, Object> args = new HashMap<>();
            try {
                JsonObject ja = JsonParser.parseString(tc.function.arguments).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : ja.entrySet()) {
                    args.put(e.getKey(), e.getValue().getAsString());
                }
            } catch (Exception ignored) {}

            // 执行工具
            System.out.println("  🔧 " + name + " " + args);
            String result = tool.execute(args);
            System.out.println("  📊 " + (result.length() > 120 ? result.substring(0, 120) + "..." : result));
            addToolResult(result, tc, history);
        }

        /**
         * Auto 权限模式实现（参考 Claude Code 的 auto permission mode）
         * 
         * 输入：用户原始需求 + Agent 要执行的操作
         * 输出：操作是否和用户意图一致（yes/no）
         * 
         * 用轻量级 LLM 调用做语义判断，不需要硬编码规则。
         */
        private boolean autoApprove(String toolName, String args) {
            String prompt = "用户原始需求: " + userInput +
                    "\nAgent 要执行的操作: " + toolName + " 参数: " + args +
                    "\n这个操作是否和用户原始需求一致？只回答 yes 或 no。";
            Message resp = llm.call(
                Arrays.asList(
                    new Message("system", "你是一个权限判断助手。只回答 yes 或 no，不要输出其他内容。"),
                    new Message("user", prompt)
                ),
                null
            );
            if (resp == null || resp.content == null) {
                return false;
            }
            String r = resp.content.trim().toLowerCase();
            return r.equals("yes") || r.startsWith("yes");
        }

        void addToolResult(String r, ToolCallMsg tc, List<Message> h) {
            Message m = new Message("tool", r);
            m.toolCallId = tc.id; m.toolName = tc.function.name;
            h.add(m);
        }
    }
}
