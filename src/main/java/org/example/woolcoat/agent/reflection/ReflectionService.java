package org.example.woolcoat.agent.reflection;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.woolcoat.agent.function.AgentTool;
import org.example.woolcoat.agent.function.ToolRegistry;
import org.example.woolcoat.exceptions.BusinessException;
import org.example.woolcoat.service.LLMService;
import org.example.woolcoat.vo.common.Message;
import org.example.woolcoat.vo.common.ToolCallResult;
import org.example.woolcoat.vo.request.LLMRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 反思纠错核心服务（工具调用/步骤执行失败时，引导LLM修正并重新执行，Agent智能化关键）
 * 最大重试次数：3次，避免无限重试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReflectionService {

    // 注入依赖
    private final ToolRegistry toolRegistry;
    private final LLMService llmService;

    // 最大重试次数
    private static final int MAX_RETRY_NUM = 3;

    /**
     * 核心方法：反思并重新执行工具调用
     * @param userQuery 用户指令
     * @param failToolCode 失败的工具Code
     * @param failParamMap 失败的工具入参
     * @param failReason 失败原因
     * @param sessionId 会话ID
     * @return 重新执行的结果
     */
    public String reflectAndRetry(String userQuery, String failToolCode, Map<String, Object> failParamMap, String failReason, String sessionId) throws Exception {
        // 校验参数
        if (failToolCode == null || failToolCode.isBlank() || failReason == null || failReason.isBlank()) {
            throw new BusinessException("反思纠错失败：缺少失败工具Code或失败原因");
        }
        AgentTool failTool = toolRegistry.getToolByCode(failToolCode);
        if (failTool == null) {
            throw new BusinessException("反思纠错失败：不存在失败的工具，toolCode=" + failToolCode);
        }

        // 循环重试，最多MAX_RETRY_NUM次
        for (int retryNum = 1; retryNum <= MAX_RETRY_NUM; retryNum++) {
            log.info("反思纠错：开始第{}次重试，sessionId={}，failToolCode={}", retryNum, sessionId, failToolCode);
            try {
                // 步骤1：构造反思Prompt，引导LLM修正
                String systemPrompt = buildReflectionPrompt(failTool, failParamMap, failReason);
                Message systemMsg = new Message("system", systemPrompt);
                Message userMsg = new Message("user", userQuery);
                LLMRequest llmRequest = new LLMRequest();
                llmRequest.setMessages(List.of(systemMsg, userMsg));
                llmRequest.setTemperature(0.2f);

                // 步骤2：调用LLM，获取修正后的工具调用指令
                var llmResponse = llmService.callLLM(llmRequest);
                if (!"success".equals(llmResponse.getStatus())) {
                    throw new BusinessException("第" + retryNum + "次重试失败：LLM调用异常，" + llmResponse.getContent());
                }

                // 步骤3：解析修正后的指令（复用FunctionCallService的ToolCallResult）
                String llmResult = llmResponse.getContent().trim().replaceAll("```json|```", "").trim();
                ToolCallResult toolCallResult;
                try {
                    toolCallResult = JSONUtil.toBean(llmResult, ToolCallResult.class);
                } catch (Exception e) {
                    throw new BusinessException("第" + retryNum + "次重试失败：LLM返回修正指令格式非法，" + e.getMessage());
                }

                // 步骤4：执行修正后的工具调用
                String newToolCode = toolCallResult.getTool_code();
                Map<String, Object> newParamMap = toolCallResult.getParam_map();
                AgentTool newTool = toolRegistry.getToolByCode(newToolCode);
                if (newTool == null) {
                    throw new BusinessException("第" + retryNum + "次重试失败：LLM推荐的工具不存在，toolCode=" + newToolCode);
                }
                String result = newTool.execute(newParamMap);
                log.info("反思纠错：第{}次重试成功，sessionId={}", retryNum, sessionId);
                return "【反思纠错-重试成功】（第" + retryNum + "次）\n原工具：" + failToolCode + "\n修正后工具：" + newToolCode + "\n执行结果：\n" + result;
            } catch (Exception e) {
                log.warn("反思纠错：第{}次重试失败，sessionId={}，原因={}", retryNum, sessionId, e.getMessage());
                // 最后一次重试失败，抛异常
                if (retryNum == MAX_RETRY_NUM) {
                    throw new BusinessException("反思纠错失败：已重试" + MAX_RETRY_NUM + "次，均失败，原失败原因=" + failReason + "，最后一次重试失败原因=" + e.getMessage());
                }
            }
        }
        // 理论上不会走到这里，因为循环内已处理所有情况
        throw new BusinessException("反思纠错失败：未知错误");
    }

    /**
     * 构建反思Prompt（从配置文件加载，配置化）
     */
    private String buildReflectionPrompt(AgentTool failTool, Map<String, Object> failParamMap, String failReason) throws Exception {
        // 加载模板文件
        Resource resource = new ClassPathResource("prompt/reflection-prompt.txt");
        if (!resource.exists()) {
            throw new BusinessException("反思纠错Prompt模板文件不存在：prompt/reflection-prompt.txt");
        }
        byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
        String promptTemplate = new String(bytes, StandardCharsets.UTF_8);

        // 替换占位符
        return promptTemplate
                .replace("{{TOOL_NAME}}", failTool.getToolMeta().getToolName())
                .replace("{{TOOL_CODE}}", failTool.getToolCode())
                .replace("{{FAIL_PARAM_MAP}}", JSONUtil.toJsonStr(failParamMap))
                .replace("{{FAIL_REASON}}", failReason)
                .replace("{{TOOL_META}}", JSONUtil.toJsonStr(failTool.getToolMeta()));
    }
}
