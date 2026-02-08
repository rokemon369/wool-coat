package org.example.woolcoat.agent.function;

import java.util.Map;

/**
 * 工具抽象接口（所有Agent工具的父接口，标准化入参出参）
 * 入参：Map<String, Object>（key=工具入参code，value=入参值），兼容任意类型入参
 * 出参：String（标准化返回结果，方便LLM处理和前端展示）
 */
public interface AgentTool {

    /**
     * 获取工具元数据（供LLM识别，返回ToolMeta）
     */
    ToolMeta getToolMeta();

    /**
     * 工具执行方法（核心，实现工具具体逻辑）
     * @param paramMap 入参Map（key=paramCode，value=入参值）
     * @return 工具执行结果（标准化String，方便LLM处理）
     * @throws Exception 执行异常（由FunctionCallService捕获，做反思纠错）
     */
    String execute(Map<String, Object> paramMap) throws Exception;

    /**
     * 获取工具唯一标识（对应ToolMeta的toolCode，方便注册和调用）
     */
    default String getToolCode() {
        return getToolMeta().getToolCode();
    }
}
