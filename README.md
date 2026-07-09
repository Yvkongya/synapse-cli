# Agent CLI — 自研 Java Agent Harness

> 从零实现的命令行 AI Agent 执行引擎（Agent Harness），参考 Claude Code + GenericAgent 架构。
> 纯 Java 手写，不依赖 LangChain4j / Spring AI 等框架，`java -jar` 一键运行。

---

## 一、项目概述

| 项目 | 说明 |
|---|---|
| **项目名** | Synapse CLI |
| **定位** | 自研 Java Agent CLI（类 Claude Code） |
| **启动方式** | `java -jar synapse-cli.jar`，也支持 `SYNAPSE_API_KEY` 环境变量 |
| **语言** | Java 8（纯 JDK + Gson + JUnit 5） |
| **总代码** | 源文件 + 单元测试，~3000 行 |
| **参考项目** | Claude Code（架构）、PaiCLI（功能设计）、local-cli-agent-java（结构） |

### 核心能力

```
输入: "搜索 AI Agent 最新进展并保存到文件"
输出:
  💭 需要先搜索网络
  🔧 web_search(query="AI Agent 2026")
  📊 找到 3 条结果
  💭 现在保存到文件
  🔧 file_write(path="ai_news.md", content=...)
  📊 文件已保存
  ✅ 任务完成！
```

---

## 二、项目结构

```
synapse-cli/
├── pom.xml                                     # Maven，仅 Gson 依赖
└── src/main/java/com/synapse/
    ├── Main.java                               # CLI入口 + REPL循环 + 6个命令
    ├── model/
    │   ├── Message.java                        # LLM对话消息
    │   ├── ToolCallMsg.java                    # 工具调用消息体
    │   ├── ToolDef.java                        # 工具定义(JSON Schema)
    │   └── Config.java                         # 配置文件管理
    ├── agent/
    │   ├── ReactEngine.java                    # ReAct循环核心
    │   └── impl/
    │       └── MultiAgent.java                 # Multi-Agent编排
    ├── llm/
    │   └── LlmClient.java                      # LLM客户端(Function Calling)
    ├── tool/
    │   ├── ToolExecutor.java                   # 工具接口
    │   ├── ToolRegistry.java                   # 工具注册中心
    │   └── impl/
    │       ├── WebSearchTool.java              # 联网搜索
    │       ├── FileReadTool.java               # 读文件
    │       ├── FileWriteTool.java              # 写文件
    │       ├── BashTool.java                   # Shell命令(白名单)
    │       └── AskUserTool.java                # 向用户提问
    ├── mcp/
    │   └── McpClient.java                      # MCP客户端(JSON-RPC 2.0)
    ├── memory/
    │   ├── EnhancedMemory.java                 # 增强记忆(四分类持久化)
    │   └── ContextCompressor.java              # 上下文压缩
    └── skill/
        └── SkillLoader.java                    # 技能系统
```

**测试（`src/test/`）：**
```
src/test/java/com/synapse/
├── model/
│   ├── ToolDefTest.java                       # ToolDef JSON 序列化
│   ├── MessageTest.java                       # 消息模型
│   └── ConfigTest.java                        # 配置与默认值
└── tool/impl/
    ├── BashToolTest.java                      # 白名单拦截
    ├── FileReadToolTest.java                  # 路径校验
    ├── FileWriteToolTest.java                 # 权限级别
    └── WebSearchToolTest.java                 # 搜索工具接口

---

## 三、核心模块详解

### 3.1 ReAct 循环（ReactEngine.java）

**实现方式：**

```java
while (turn < MAX_TURNS) {  // 最多 15 轮
    // 1. 压缩过长的历史
    history = compressor.compressIfNeeded(history);

    // 2. 调 LLM（传入历史 + 工具定义）
    Message resp = llm.call(history, registry.defs());

    // 3. 判断结果
    if (resp.toolCalls == null || resp.toolCalls.isEmpty()) {
        // 无工具调用 → 任务完成
        memory.rememberTask(input, resp.content);
        return;
    }

    // 4. 有工具调用 → 串行执行（避免审批抢 Scanner 和并发写 history）
    for (ToolCallMsg tc : resp.toolCalls) {
        new ToolRunner(tc, history, userInput).run();
    }
}
```

**解决了什么关键问题？**

Web 版用文本 `TOOL:` 前缀让 LLM 输出工具调用，格式匹配率极低（10次里8次失败）。这个版本使用 LLM 官方的 Function Calling API——返回的 `tool_calls` 是结构化 JSON，100% 解析成功。

**为什么不用 LangChain4j？**

LangChain4j 封装了 tool_calls 的底层细节。面试官想看的是你对 ReAct 循环、Function Calling 协议、工具调度链路的底层理解，不是会用框架。

### 3.2 工具系统（ToolExecutor + ToolRegistry）

**每个工具的 JSON Schema 定义（以 FileWriteTool 为例）：**

```json
{
  "name": "file_write",
  "description": "写入文件",
  "parameters": {
    "type": "object",
    "properties": {
      "path": {"type": "string", "description": "文件路径"},
      "content": {"type": "string", "description": "文件内容"}
    },
    "required": ["path", "content"]
  }
}
```

LLM 看到这个 Schema，就知道：
- 什么场景下调用这个工具（根据 description）
- 参数填什么类型（string / number / boolean）
- 哪些参数必填（required）

**三级权限（HITL 审批）：**

| 级别 | 标签 | 行为 | 工具 |
|---|---|---|---|
| Level 0 | 🟢 安全 | 自动执行 | file_read / web_search / ask_user |
| Level 1 | 🟡 需确认 | 用户按 Y/n | file_write |
| Level 2 | 🔴 危险 | 用户必须确认 y/N | bash |

参考 Claude Code 的权限模型（自动允许/首次确认/每次确认/极危险）。

**安全机制：**
- **文件操作路径限制**：`file_read` / `file_write` 限制在工作目录内，禁止目录穿越；写文件还禁止覆盖关键配置文件（`synapse_config.json`、`.env` 等）
- **命令执行白名单**：`bash` 仅允许 `ls`/`cat`/`echo`/`pwd` 等只读命令，按 token 精确匹配命令名，阻止 shell 操作符（`;`、`|`、`&&`），使用 `ProcessBuilder` 避免 shell 注入

### 3.2.1 Auto 权限模式

参考 Claude Code 的 auto permission mode。

三级权限是硬编码的：`file_write` 权限=1 永远要确认，`bash` 权限=2 永远危险。
Auto 模式在此基础上用 **LLM 做语义判断**，让同一工具在不同场景下行为不同：

- 用户说"帮我重构 login.ts" → Agent 写 login.ts → LLM 判断"和用户意图一致" → 自动放行
- Agent 想写 config.yaml → LLM 判断"用户没提过这个文件" → 交人工确认

```java
private boolean autoApprove(String toolName, String args) {
    String prompt = "用户原始需求: " + userInput +
            "\nAgent 要执行的操作: " + toolName +
            "\n这个操作是否和用户原始需求一致？只回答 yes 或 no。";
    Message resp = llm.call(
        Arrays.asList(
            new Message("system", "你是一个权限判断助手。只回答 yes 或 no。"),
            new Message("user", prompt)
        ),
        null
    );
    String r = resp.content.trim().toLowerCase();
    return r.equals("yes") || r.startsWith("yes");
}
```

**注意事项：**
- Auto 审批是辅助机制，不能代替人工审查高风险操作
- 模型异常或返回空时默认拒绝，走保守路线
- 对写文件、执行命令等高危操作，仍以人工确认为主

### 3.2.2 Streaming Tool Executor（API 已实现，主流程未集成）

`LlmClient.callStream()` 支持 SSE 流式解析，可在模型输出过程中增量累积 `tool_calls`。

**当前状态：**
- `callStream()` 接口已完成，支持流式分片合并和 `StreamCallback` 回调
- 主流程 `ReactEngine.execute()` 目前仍使用非流式 `call()`，尚未切换到流式路径
- 后续接入后可实现：工具参数收齐即提前执行，不等模型完全输出

**设计参考：** Claude Code 的 StreamingToolExecutor。传统做法是等模型完整输出后再执行工具；Streaming 做法是模型还在生成时，看到只读工具的 JSON 参数收齐了就提前执行。

### 3.3 MCP 协议客户端（McpClient.java）

**通信流程：**

```
Synapse CLI  ←→  MCP Server（外部进程）
                     ↓
                JSON-RPC 2.0 over stdin/stdout

消息格式:
Content-Length: {len}\r\n\r\n{json}

交互流程:
1. initialize          → 握手协商版本
2. tools/list          → 获取工具列表
3. tools/call {args}   → 调用工具
```

**为什么重要？**

没有 MCP：所有工具必须是 Java 代码写在项目里，5 个工具就是 5 个。
有 MCP：可以连接任何实现了 MCP 的工具——GitHub、浏览器、数据库、搜索引擎、Playwright……
**工具生态从 5 个变成无限个。**

### 3.4 Multi-Agent（MultiAgent.java）

**三个角色各司其职：**

```
Planner（规划者）
  Prompt: "你是任务规划专家。将用户任务拆为3-5个可执行步骤。"
  输出: 1. 搜索网络  2. 整理结果  3. 保存到文件

Worker（执行者）
  复用 ReactEngine.execute()，按计划逐步执行

Reviewer（审查者）
  Prompt: "你是质量审查员。检查执行结果是否完整正确。"
  输出: "步骤1完成，步骤2结果偏短建议补充"
```

参考 Claude Code 的 Coordinator 模式和 PaiCLI 的 Orchestrator 模式。

### 3.5 Skill 技能系统（SkillLoader.java）

**技能存储结构：**

```
synapse_skills/
├── web-research/
│   └── SKILL.md         # YAML Frontmatter + Markdown
├── daily-report/
│   └── SKILL.md
└── code-review/
    └── SKILL.md

SKILL.md 格式:
---
name: web-research
description: 联网搜索并整理资料
trigger: 搜索
---
执行步骤：
1. web_search(query=用户关键词)
2. 整理结果并保存到文件
```

**三级加载架构（参考 PaiCLI）：**
- **内置技能**：编译在代码里（web-research / daily-report / code-review）
- **用户技能**：`synapse_skills/` 目录，跨项目通用
- **按需触发**：用户输入匹配 trigger 字段时自动加载

### 3.6 增强记忆（EnhancedMemory.java）

**四分类文件持久化：**

```
synapse_memory/
├── preferences.json     # 用户偏好（"回答要简洁"）
├── tasks.json           # 任务历史（"搜索了AI新闻"）
├── knowledge.json       # 知识积累（"Java 21特性"）
└── chat.json            # 对话片段
```

启动时加载 → 实时持久化 → 上限 50 条自动淘汰 → 输出到 System Prompt。

**为什么不用数据库？**
不需要 MySQL/Redis，用户可以手动查看和修改，备份只需复制目录。

### 3.7 上下文压缩（ContextCompressor.java）

**问题：** 长对话会超出 LLM 的 Token 上限（4096 tokens）。

**方案：** 当历史超过 20 条时，保留 System Prompt + 最近 10 条，中间部分调 LLM 摘要压缩成一段话塞回去。压缩时保留 user、assistant、tool 三类消息，确保工具执行结果不被丢弃。

### 3.8 CLI 交互界面（Main.java）

**6 个斜杠命令：**

| 命令 | 功能 |
|---|---|
| `/help` | 显示帮助 |
| `/settings` | 配置 API Key 和模型 |
| `/tools` | 查看可用工具（带权限标签） |
| `/plan` | Plan Mode — 先出计划再执行 |
| `/exit` | 退出 |

**彩色终端输出：**
- 蓝色 `╰─►` 输入提示
- 青色 Agent 名称
- 灰色辅助信息
- 绿色/黄色/红色 权限标签

---

## 四、关键技术选型对比

| 决策 | 选型 | 为什么不选别的 |
|---|---|---|
| **框架** | 纯 Java，无框架 | LangChain4j 隐藏底层细节，面试官想看原理理解 |
| **Web vs CLI** | CLI（命令行） | Agent 是开发工具不是 Web 服务，Claude Code/Qoder 都是 CLI |
| **HTTP 客户端** | JDK 内置 `HttpURLConnection` | 避免 OkHttp 导入冲突问题 |
| **JSON** | Gson | 轻量，只依赖这一个库 |
| **持久化** | JSON 文件 + 四分类目录 | 用户可手动查看修改，备份只需复制目录 |
| **记忆** | EnhancedMemory 四分类（偏好/任务/知识/对话） | 自动淘汰，注入 System Prompt |
| **构建** | Maven + shade-plugin | 打 fat jar，`java -jar` 即可运行 |
| **测试** | JUnit 5 + maven-surefire-plugin | |
| **API Key** | 优先环境变量 `SYNAPSE_API_KEY`，回退配置文件 |

---

## 五、启动方式

```bash
cd E:\Coding_Software\Java\Main_Project\synapse-cli
set JAVA_HOME=E:\Coding_Software\Java\jdk1.8.0_281
java -jar target\synapse-cli-0.1.0.jar
```

或双击 `启动SynapseCLI.bat`（已在桌面和项目目录各放一份）。

### 环境变量（可选）

| 变量 | 说明 | 示例 |
|------|------|------|
| `SYNAPSE_API_KEY` | LLM API Key，优先级高于配置文件 | `export SYNAPSE_API_KEY=sk-xxx` |
| `SYNAPSE_SEARCH_API_KEY` | 搜索 API Key（Bing 等） | `export SYNAPSE_SEARCH_API_KEY=abc123` |

首次运行会自动创建 `synapse_config.json` 并根据提示配置。

### 运行测试

```bash
mvn test
```

---

*文档版本 v0.1.0 • 2026年6月*
