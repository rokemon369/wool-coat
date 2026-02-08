package org.example.woolcoat.agent.function.impl;

import org.example.woolcoat.agent.function.AgentTool;
import org.example.woolcoat.agent.function.ToolMeta;
import org.example.woolcoat.agent.function.ToolTypeEnum;
import org.example.woolcoat.exceptions.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用工具——计算器（支持加减乘除，原子化实现）
 */
@Component
public class CalculatorTool implements AgentTool {

    // 工具元数据（供LLM识别，标准化描述）
    private static final ToolMeta TOOL_META;

    // 静态代码块初始化工具元数据，仅执行一次
    static {
        // 定义入参元数据（表达式：必传，String，如"1+2*3"）
        List<ToolMeta.ParamMeta> paramMetas = new ArrayList<>();
        paramMetas.add(new ToolMeta.ParamMeta(
                "expression",
                "计算表达式",
                "String",
                true,
                "需要计算的数学表达式，仅支持加减乘除，如1+2*3、(10-5)/2"
        ));
        // 初始化工具元数据
        TOOL_META = new ToolMeta(
                "calculator",
                "计算器",
                ToolTypeEnum.COMMON,
                "用于执行简单的数学加减乘除计算，输入标准数学表达式即可返回结果",
                paramMetas,
                "返回计算结果，如输入1+2，返回3；表达式非法则返回错误信息"
        );
    }

    @Override
    public ToolMeta getToolMeta() {
        return TOOL_META;
    }

    @Override
    public String execute(Map<String, Object> paramMap) throws Exception {
        // 1. 校验入参
        if (!paramMap.containsKey("expression") || paramMap.get("expression") == null) {
            throw new BusinessException("计算器工具缺少必传入参：expression（计算表达式）");
        }
        String expression = paramMap.get("expression").toString().trim();
        if (expression.isBlank()) {
            throw new BusinessException("计算表达式不能为空");
        }

        // 2. 执行计算（简单实现，可扩展更复杂的表达式解析）
        try {
            // 注：生产环境建议用安全的表达式解析器（如JEXL），避免ScriptEngine注入
            javax.script.ScriptEngine engine = new javax.script.ScriptEngineManager().getEngineByName("JavaScript");
            Object result = engine.eval(expression);
            return "计算结果：" + expression + " = " + result;
        } catch (Exception e) {
            throw new BusinessException("计算表达式非法，不支持该语法：" + expression + "，错误信息：" + e.getMessage());
        }
    }
}
