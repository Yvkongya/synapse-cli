package com.synapse.tool.impl;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WebSearchToolTest {

    @Test
    void emptyQuery_returnsError() {
        WebSearchTool tool = new WebSearchTool();
        String result = tool.execute(new HashMap<>());
        assertTrue(result.contains("请提供搜索关键词"));
    }

    @Test
    void permissionLevel_is0() {
        WebSearchTool tool = new WebSearchTool();
        assertEquals(0, tool.permissionLevel());
    }

    @Test
    void toolDef_hasCorrectName() {
        WebSearchTool tool = new WebSearchTool();
        assertEquals("web_search", tool.getDef().name);
    }
}
