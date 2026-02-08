package org.example.woolcoat.vo.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

// 工具调用结果标准化格式（供LLM返回，约定JSON结构）
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallResult {
    private String tool_code; // 工具唯一标识
    private Map<String, Object> param_map; // 工具入参Map

    // getter/setter，方便JSON解析
    public String getTool_code() {
        return tool_code;
    }

    public void setTool_code(String tool_code) {
        this.tool_code = tool_code;
    }

    public Map<String, Object> getParam_map() {
        return param_map;
    }

    public void setParam_map(Map<String, Object> param_map) {
        this.param_map = param_map;
    }
}
