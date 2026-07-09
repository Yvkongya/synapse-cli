package com.synapse.tool.impl;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BashToolTest {

    @Test
    void emptyCommand_returnsError() {
        BashTool tool = new BashTool();
        String result = tool.execute(new HashMap<>());
        assertTrue(result.contains("请提供命令"));
    }

    @Test
    void disallowedCommand_isBlocked() {
        BashTool tool = new BashTool();
        Map<String, Object> args = new HashMap<>();
        args.put("command", "rm -rf /");
        String result = tool.execute(args);
        assertTrue(result.contains("安全拦截"));
    }

    @Test
    void echoWithRedirect_isBlocked() {
        BashTool tool = new BashTool();
        Map<String, Object> args = new HashMap<>();
        args.put("command", "echo test > file.txt");
        String result = tool.execute(args);
        assertTrue(result.contains("安全拦截"));
    }

    @Test
    void permissionLevel_is2() {
        BashTool tool = new BashTool();
        assertEquals(2, tool.permissionLevel());
    }
}
