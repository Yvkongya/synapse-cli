package com.synapse.tool.impl;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FileWriteToolTest {

    @Test
    void emptyPath_returnsError() {
        FileWriteTool tool = new FileWriteTool();
        String result = tool.execute(new HashMap<>());
        assertTrue(result.contains("请提供文件路径"));
    }

    @Test
    void permissionLevel_is1() {
        FileWriteTool tool = new FileWriteTool();
        assertEquals(1, tool.permissionLevel());
    }
}
