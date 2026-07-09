package com.synapse.tool.impl;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FileReadToolTest {

    @Test
    void emptyPath_returnsError() {
        FileReadTool tool = new FileReadTool();
        String result = tool.execute(new HashMap<>());
        assertTrue(result.contains("请提供文件路径"));
    }

    @Test
    void permissionLevel_is0() {
        FileReadTool tool = new FileReadTool();
        assertEquals(0, tool.permissionLevel());
    }
}
