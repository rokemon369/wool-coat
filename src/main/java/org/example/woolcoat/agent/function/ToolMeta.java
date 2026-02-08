package org.example.woolcoat.agent.function;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工具元数据（供LLM识别的工具描述，标准化格式）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolMeta {
    /**
     * 工具唯一标识（小写+下划线，如calculator、local_rag_search），LLM通过该字段调用工具
     */
    private String toolCode;
    /**
     * 工具名称（如计算器、本地RAG检索）
     */
    private String toolName;
    /**
     * 工具类型（对应ToolTypeEnum）
     */
    private ToolTypeEnum toolType;
    /**
     * 工具描述（详细说明工具用途，引导LLM正确使用）
     */
    private String toolDesc;
    /**
     * 工具入参描述（List，方便LLM识别入参名/类型/用途）
     */
    private List<ParamMeta> paramMetas;
    /**
     * 工具出参描述（说明返回结果格式/含义）
     */
    private String resultDesc;

    /**
     * 入参元数据（子节点，标准化入参描述）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParamMeta {
        /**
         * 入参名（小写+下划线，如question、document_id），LLM通过该字段传参
         */
        private String paramCode;
        /**
         * 入参名称（如问题、文档ID）
         */
        private String paramName;
        /**
         * 入参类型（如String、Integer、Long）
         */
        private String paramType;
        /**
         * 入参是否必传（true/false）
         */
        private Boolean required;
        /**
         * 入参描述（说明入参含义，引导LLM正确传参）
         */
        private String paramDesc;
    }
}
