package org.example.woolcoat.agent.function.impl;

import lombok.RequiredArgsConstructor;
import org.example.woolcoat.agent.function.AgentTool;
import org.example.woolcoat.agent.function.ToolMeta;
import org.example.woolcoat.agent.function.ToolTypeEnum;
import org.example.woolcoat.exceptions.BusinessException;
import org.example.woolcoat.service.rag.RagSearchService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本地RAG检索工具（真实Lucene实现，替换原模拟版本）
 */
@Component
@RequiredArgsConstructor
public class LocalRagSearchTool implements AgentTool {

    private final RagSearchService ragSearchService;

    private static final ToolMeta TOOL_META;

    static {
        List<ToolMeta.ParamMeta> paramMetas = new ArrayList<>();
        paramMetas.add(new ToolMeta.ParamMeta(
                "question",
                "检索问题",
                "String",
                true,
                "需要从本地知识库中检索的问题/关键词，如Redis持久化方式"
        ));
        paramMetas.add(new ToolMeta.ParamMeta(
                "top_k",
                "返回条数",
                "Integer",
                false,
                "需要返回的知识库相关内容条数，默认5条，最大值10"
        ));
        paramMetas.add(new ToolMeta.ParamMeta(
                "user_id",
                "用户ID",
                "String",
                true,
                "用户ID，仅检索该用户上传的文档"
        ));
        TOOL_META = new ToolMeta(
                "local_rag_search",
                "本地RAG检索",
                ToolTypeEnum.INFO,
                "从本地知识库中检索与问题相关的内容，解决LLM幻觉问题，支持关键词/自然语言检索",
                paramMetas,
                "返回检索到的知识库内容列表，包含分片内容、关联文档ID、相似度，按相关性排序"
        );
    }

    @Override
    public ToolMeta getToolMeta() {
        return TOOL_META;
    }

    @Override
    public String execute(Map<String, Object> paramMap) throws Exception {
        // 1. 校验必传参数
        if (!paramMap.containsKey("question") || paramMap.get("question") == null) {
            throw new BusinessException("本地RAG检索工具缺少必传入参：question（检索问题）");
        }
        if (!paramMap.containsKey("user_id") || paramMap.get("user_id") == null) {
            throw new BusinessException("本地RAG检索工具缺少必传入参：user_id（用户ID）");
        }
        String question = paramMap.get("question").toString().trim();
        String userId = paramMap.get("user_id").toString().trim();
        if (question.isBlank() || userId.isBlank()) {
            throw new BusinessException("检索问题/用户ID不能为空");
        }

        // 2. 处理可选参数
        int topK = 5;
        if (paramMap.containsKey("top_k") && paramMap.get("top_k") != null) {
            try {
                topK = Integer.parseInt(paramMap.get("top_k").toString());
                topK = Math.min(topK, 10);
                topK = Math.max(topK, 1);
            } catch (Exception e) {
                throw new BusinessException("top_k入参非法，必须是整数，默认使用5条");
            }
        }

        // 3. 调用真实RAG检索服务
        List<Map<String, Object>> searchResults = ragSearchService.search(question, topK, userId);
        if (searchResults.isEmpty()) {
            return "本地RAG检索结果：未找到与【" + question + "】相关的知识库内容";
        }

        // 4. 构建返回结果
        StringBuilder result = new StringBuilder();
        result.append("本地RAG检索结果（问题：").append(question).append("，返回").append(searchResults.size()).append("条）：\n");
        for (int i = 0; i < searchResults.size(); i++) {
            Map<String, Object> res = searchResults.get(i);
            result.append(i + 1).append(". 文档ID：").append(res.get("docId"))
                    .append(" | 分片索引：").append(res.get("chunkIndex"))
                    .append(" | 相似度：").append(String.format("%.2f", res.get("similarity")))
                    .append("\n   内容：").append(res.get("content")).append("\n");
        }
        return result.toString().trim();
    }
}