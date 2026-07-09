package com.synapse.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolDefTest {

    @Test
    void toJson_containsFunctionType() {
        ToolDef d = new ToolDef();
        d.name = "test_tool";
        d.description = "A test tool";
        String json = d.toJson();
        assertTrue(json.contains("\"type\":\"function\""), "should declare function type");
        assertTrue(json.contains("\"name\":\"test_tool\""), "should contain tool name");
    }

    @Test
    void toJson_withParameters() {
        ToolDef d = new ToolDef();
        d.name = "file_read";
        d.description = "读取文件";
        java.util.Map<String, Object> props = new java.util.LinkedHashMap<>();
        java.util.Map<String, Object> p = new java.util.LinkedHashMap<>();
        p.put("type", "string");
        props.put("path", p);
        java.util.Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        d.parameters = schema;

        String json = d.toJson();
        assertTrue(json.contains("file_read"), "should contain name");
        assertTrue(json.contains("读取文件"), "should contain description");
        assertTrue(json.contains("parameters"), "should contain parameters");
    }

    @Test
    void toJson_nullParameters_usesEmptyObject() {
        ToolDef d = new ToolDef();
        d.name = "no_params";
        d.parameters = null;
        String json = d.toJson();
        assertTrue(json.contains("\"parameters\":{}"), "null params becomes {}");
    }

    @Test
    void escape_specialChars() {
        ToolDef d = new ToolDef();
        d.name = "tool\"with\"quotes";
        d.description = "desc with\nnewline";
        String json = d.toJson();
        assertTrue(json.contains("\\\""), "quotes should be escaped");
    }
}
