package com.synapse;
import com.synapse.agent.ReactEngine;
import com.synapse.llm.LlmClient;
import com.synapse.model.*;
import com.synapse.tool.ToolRegistry;
import com.synapse.tool.impl.*;
import java.util.*;
/**
 * Synapse CLI 入口 — REPL 循环 + 彩色终端 UI
 * 
 * 启动方式：java -jar synapse-cli-0.1.0.jar
 * 
 * 支持命令：
 * /help      — 显示帮助
 * /settings  — 配置 API Key 和模型
 * /tools     — 查看可用工具
 * /plan      — Plan Mode
 * /exit      — 退出
 */
public class Main {
    static String RESET = "\u001B[0m";
    static String CYAN = "\u001B[36m";
    static String GREEN = "\u001B[32m";
    static String YELLOW = "\u001B[33m";
    static String RED = "\u001B[31m";
    static String BLUE = "\u001B[34m";
    static String BOLD = "\u001B[1m";
    static String DIM = "\u001B[2m";

    public static void main(String[] args) {
        System.out.println(BOLD + CYAN + "\n  ╔══════════════════════════════════════╗");
        System.out.println("  ║        " + BOLD + "Synapse" + RESET + CYAN + " CLI v0.1.0          ║");
        System.out.println("  ║    " + DIM + "Java Agent — ReAct + FC + HITL" + RESET + CYAN + "   ║");
        System.out.println("  ╚══════════════════════════════════════╝" + RESET + "\n");

        // 加载配置
        Config config = Config.load();
        Config.ProviderCfg provider = config.active();
        if(provider.apiKey == null || provider.apiKey.isEmpty()) {
            System.out.println(YELLOW + "  ⚠️ 首次使用，请先配置 API Key" + RESET);
            setupConfig(config);
            provider = config.active();
        }
        System.out.println("  " + DIM + "模型: " + provider.name + " (" + provider.model + ")" + RESET);

        // 初始化工具和引擎
        LlmClient llm = new LlmClient(provider);
        ToolRegistry registry = new ToolRegistry();
        registry.register(new WebSearchTool());
        registry.register(new FileReadTool());
        registry.register(new FileWriteTool());
        registry.register(new BashTool());
        registry.register(new AskUserTool());
        System.out.println("  " + DIM + "工具: " + registry.size() + " 个已加载" + RESET);
        System.out.println("  " + DIM + "输入 /help 查看命令, /settings 配置, /exit 退出" + RESET + "\n");

        ReactEngine engine = new ReactEngine(llm, registry);
        Scanner scanner = new Scanner(System.in);

        // REPL 主循环
        while(true) {
            System.out.print(BLUE + "  ╰─► " + RESET);
            String input = scanner.nextLine().trim();
            if(input.isEmpty()) continue;

            if(input.equals("/exit") || input.equals("exit")) {
                System.out.println("  " + GREEN + "再见！" + RESET); break;
            } else if(input.equals("/help")) { printHelp();
            } else if(input.equals("/settings")) { setupConfig(config); llm.setProvider(config.active()); System.out.println(GREEN + "  ✅ 配置已更新" + RESET);
            } else if(input.equals("/tools")) {
                System.out.println("  " + BOLD + "可用工具:" + RESET);
                for(ToolDef td : registry.defs()) {
                    String level = td.name.equals("bash") ? RED+"[危险]"+RESET : td.name.contains("write") ? YELLOW+"[需确认]"+RESET : GREEN+"[安全]"+RESET;
                    System.out.println("    " + level + " " + td.name + ": " + td.description);
                }
                // 显示搜索配置状态
                Config.SearchCfg sc = config.search;
                if (sc != null) {
                    System.out.println("  " + DIM + "搜索: " + sc.provider + (sc.apiKey.isEmpty() && !"duckduckgo".equals(sc.provider) ? " (未配置 Key)" : "") + RESET);
                }
            } else if(input.equals("/plan")) {
                System.out.print("  输入需求: "); String task = scanner.nextLine().trim();
                if(!task.isEmpty()) engine.execute(task, true);
            } else if(input.startsWith("/")) {
                System.out.println("  " + RED + "未知命令: " + input + "，输入 /help 查看可用命令" + RESET);
            } else {
                engine.execute(input, false);
                System.out.println();
            }
        }
    }

    static void printHelp() {
        System.out.println("  " + BOLD + "可用命令:" + RESET);
        System.out.println("    /help       显示帮助");
        System.out.println("    /settings   配置 API Key 和模型");
        System.out.println("    /tools      查看可用工具");
        System.out.println("    /plan       Plan Mode — 先出计划再执行");
        System.out.println("    /exit       退出");
        System.out.println("    " + DIM + "直接输入文字即可与 Agent 对话" + RESET);
    }

    static void setupConfig(Config config) {
        Scanner sc = new Scanner(System.in);
        System.out.println("  " + BOLD + "配置 LLM 提供商:" + RESET);
        System.out.println("  1. DeepSeek (默认)");
        System.out.println("  2. OpenAI");
        System.out.println("  3. 自定义");
        System.out.print("  选择 (1-3) [1]: ");
        String choice = sc.nextLine().trim();
        Config.ProviderCfg p = new Config.ProviderCfg();
        if(choice.equals("2")) { p.id = "openai"; p.name = "OpenAI"; p.url = "https://api.openai.com/v1"; p.model = "gpt-4"; }
        else if(choice.equals("3")) {
            System.out.print("  名称: "); p.name = sc.nextLine().trim();
            System.out.print("  API URL: "); p.url = sc.nextLine().trim();
            System.out.print("  模型: "); p.model = sc.nextLine().trim();
        }
        System.out.print("  API Key（留空使用 SYNAPSE_API_KEY 环境变量）: "); String key = sc.nextLine().trim();
        if(!key.isEmpty()) p.apiKey = key;
        config.providers.clear(); config.providers.add(p); config.activeProvider = p.id;

        // 搜索配置
        System.out.println("\n  " + BOLD + "搜索配置:" + RESET);
        System.out.println("  1. DuckDuckGo（默认，无需 Key）");
        System.out.println("  2. Bing Search API");
        System.out.println("  3. 自定义");
        System.out.print("  选择 (1-3) [1]: ");
        String scChoice = sc.nextLine().trim();
        Config.SearchCfg search = new Config.SearchCfg();
        if (scChoice.equals("2")) {
            search.provider = "bing";
            search.url = "https://api.bing.microsoft.com/v7.0/search";
            System.out.print("  Bing API Key（或使用 SYNAPSE_SEARCH_API_KEY 环境变量）: ");
            String sk = sc.nextLine().trim();
            if (!sk.isEmpty()) search.apiKey = sk;
        } else if (scChoice.equals("3")) {
            search.provider = "custom";
            System.out.print("  搜索 URL（用 %s 表示查询词位置）: "); search.url = sc.nextLine().trim();
            System.out.print("  API Key Header: "); search.apiKeyHeader = sc.nextLine().trim();
            System.out.print("  API Key: "); String sk = sc.nextLine().trim();
            if (!sk.isEmpty()) search.apiKey = sk;
        }
        config.search = search;
        config.save();
    }
}
