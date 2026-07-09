package com.synapse.agent.impl;
import com.synapse.agent.ReactEngine;
import com.synapse.llm.LlmClient;
import com.synapse.model.*;
import com.synapse.tool.ToolRegistry;
import java.util.*;
/** Multi-Agent 编排器 — Planner 分解 + Worker 执行 + Reviewer 验证 */
public class MultiAgent {
    private final LlmClient llm;
    private final ReactEngine worker;

    public MultiAgent(LlmClient llm, ReactEngine worker) {
        this.llm = llm; this.worker = worker;
    }

    public void execute(String task) {
        System.out.println("\n  📋 Multi-Agent 模式启动");
        System.out.println("  ┌─────────────────────────────────────────────┐");
        System.out.println("  │  Planner: 拆解任务                          │");
        System.out.println("  │  Worker:  逐步执行                          │");
        System.out.println("  │  Reviewer:验证结果                          │");
        System.out.println("  └─────────────────────────────────────────────┘\n");

        // Phase 1: Planner 拆解任务
        System.out.println("  🔍 Planner 正在分析任务...");
        Message planResp = llm.call(Arrays.asList(
            new Message("system", "你是任务规划专家。将用户任务拆分为3-5个可执行的步骤，每个步骤一句话。只输出步骤编号+描述。"),
            new Message("user", task)
        ), null);
        String plan = planResp.content != null ? planResp.content : "1. " + task;
        System.out.println("  📋 执行计划:");
        for (String line : plan.split("\n")) {
            String l = line.trim();
            if (!l.isEmpty()) System.out.println("    " + l);
        }
        System.out.println();

        // Phase 2: Worker 逐步执行
        String[] steps = plan.split("\n");
        List<String> results = new ArrayList<>();
        for (int i = 0; i < Math.min(steps.length, 8); i++) {
            String step = steps[i].replaceAll("^[0-9]+[. ]*", "").trim();
            if (step.isEmpty()) continue;
            System.out.println("  ⚙️  Worker 执行步骤 " + (i + 1) + ": " + step);
            worker.execute(step, false);
            results.add(step + " -> 已完成");
            System.out.println();
        }

        // Phase 3: Reviewer 验证结果 (如有结果)
        if (results.size() >= 2) {
            System.out.println("  ✅ Reviewer 验证执行结果...");
            StringBuilder summary = new StringBuilder();
            for (String r : results) summary.append(r).append("\n");
            Message reviewResp = llm.call(Arrays.asList(
                new Message("system", "你是质量审查员。检查以下任务执行结果是否完整、正确。如有问题请指出。"),
                new Message("user", "原始任务: " + task + "\n\n执行结果:\n" + summary.toString())
            ), null);
            if (reviewResp.content != null && !reviewResp.content.isEmpty()) {
                System.out.println("  📊 审查意见: " + reviewResp.content);
            }
        }
        System.out.println("  ✅ Multi-Agent 任务完成\n");
    }
}
