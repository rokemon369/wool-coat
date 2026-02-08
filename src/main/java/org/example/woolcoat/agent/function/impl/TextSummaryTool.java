package org.example.woolcoat.agent.function.impl;

import lombok.RequiredArgsConstructor;
import org.example.woolcoat.agent.function.AgentTool;
import org.example.woolcoat.agent.function.ToolMeta;
import org.example.woolcoat.agent.function.ToolTypeEnum;
import org.example.woolcoat.exceptions.BusinessException;
import org.example.woolcoat.service.LLMService;
import org.example.woolcoat.vo.common.Message;
import org.example.woolcoat.vo.request.LLMRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用工具——文本摘要（对接LLMService，利用LLM实现文本精准摘要，支持自定义摘要长度）
 */
@Component
@RequiredArgsConstructor
public class TextSummaryTool implements AgentTool {

    // 注入基础层的LLMService，直接调用，无代码变更
    private final LLMService llmService;

    private static final ToolMeta TOOL_META;

    static {
        // 入参：content（待摘要文本，必传）、summary_length（摘要长度，非必传，默认100字）
        List<ToolMeta.ParamMeta> paramMetas = new ArrayList<>();
        paramMetas.add(new ToolMeta.ParamMeta(
                "content",
                "待摘要文本",
                "String",
                true,
                "需要进行摘要的原始文本，支持任意格式，长度不限"
        ));
        paramMetas.add(new ToolMeta.ParamMeta(
                "summary_length",
                "摘要长度",
                "Integer",
                false,
                "期望的摘要字数，默认100字，最大值500字，最小值50字"
        ));
        // 工具元数据
        TOOL_META = new ToolMeta(
                "text_summary",
                "文本摘要",
                ToolTypeEnum.COMMON,
                "利用大模型对原始文本进行精准摘要，支持自定义摘要长度，保留核心信息",
                paramMetas,
                "返回指定长度的文本摘要，语言简洁，核心信息完整"
        );
    }

    @Override
    public ToolMeta getToolMeta() {
        return TOOL_META;
    }

    @Override
    public String execute(Map<String, Object> paramMap) throws Exception {
        // 1. 校验入参
        if (!paramMap.containsKey("content") || paramMap.get("content") == null) {
            throw new BusinessException("文本摘要工具缺少必传入参：content（待摘要文本）");
        }
        String content = paramMap.get("content").toString().trim();
        if (content.isBlank()) {
            throw new BusinessException("待摘要文本不能为空");
        }
        // 处理非必传入参summary_length
        int summaryLength = 100;
        if (paramMap.containsKey("summary_length") && paramMap.get("summary_length") != null) {
            try {
                summaryLength = Integer.parseInt(paramMap.get("summary_length").toString());
                summaryLength = Math.min(summaryLength, 500);
                summaryLength = Math.max(summaryLength, 50);
            } catch (Exception e) {
                throw new BusinessException("summary_length入参非法，必须是整数，默认使用100字");
            }
        }

        // 2. 调用基础层LLMService实现摘要，沿用之前的LLMRequest/Message变量名
        Message systemMsg = new Message("system", "你是一个专业的文本摘要助手，需要将用户提供的文本总结为" + summaryLength + "字左右的内容，保留核心信息，语言简洁，不要冗余。");
        Message userMsg = new Message("user", "请对以下文本进行摘要：\n" + content);
        LLMRequest llmRequest = new LLMRequest();
        llmRequest.setMessages(List.of(systemMsg, userMsg));
        llmRequest.setTemperature(0.3f); // 低温度，保证摘要精准

        // 调用LLM，沿用之前的llmService.callLLM方法
        var llmResponse = llmService.callLLM(llmRequest);
        if (!"success".equals(llmResponse.getStatus())) {
            throw new BusinessException("文本摘要失败，LLM调用异常：" + llmResponse.getContent());
        }

        return "文本摘要结果（约" + summaryLength + "字）：\n" + llmResponse.getContent();
    }
}
