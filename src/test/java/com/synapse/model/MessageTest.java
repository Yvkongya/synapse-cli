package com.synapse.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void constructor_setsRoleAndContent() {
        Message m = new Message("user", "hello");
        assertEquals("user", m.role);
        assertEquals("hello", m.content);
    }

    @Test
    void defaultConstructor_fieldsAreNull() {
        Message m = new Message();
        assertNull(m.role);
        assertNull(m.content);
        assertNull(m.toolCalls);
    }

    @Test
    void toolFields_roundTrip() {
        Message m = new Message("tool", "result data");
        m.toolCallId = "call_123";
        m.toolName = "file_read";
        assertEquals("tool", m.role);
        assertEquals("result data", m.content);
        assertEquals("call_123", m.toolCallId);
        assertEquals("file_read", m.toolName);
    }
}
