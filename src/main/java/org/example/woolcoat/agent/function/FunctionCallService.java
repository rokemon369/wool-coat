package org.example.woolcoat.agent.function;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.woolcoat.agent.reflection.ReflectionService;
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
 * 工具调用核心服务（封装Prompt构造→LLM解析→工具调用→结果封装全流程，Agent核心）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionCallService {

    // 注入依赖：工具注册中心 + LLM + 反思纠错
    private final ToolRegistry toolRegistry;
    private final LLMService llmService;
    private final ReflectionService reflectionService;

    /**
     * 核心方法：执行工具调用
     * @param userQuery 用户指令（如"帮我计算1+2*3"）
     * @param sessionId 会话ID（用于后续上下文关联，可传null）
     * @return 工具执行结果（标准化String）
     */
    public String callTool(String userQuery, String sessionId) throws Exception {
        // 步骤1：获取所有工具元数据（供LLM识别）
        List<ToolMeta> toolMetas = toolRegistry.getAllToolMetas();
        if (toolMetas.isEmpty()) {
            throw new BusinessException("无可用工具，工具调用功能不可用");
        }
        String toolMetaStr = JSONUtil.toJsonStr(toolMetas);
        log.info("工具调用：获取到{}个工具元数据，sessionId={}", toolMetas.size(), sessionId);

        // 步骤2：构造工具调用Prompt（配置化，从文件加载，不硬编码）
        String systemPrompt = buildToolCallSystemPrompt(toolMetaStr);
        Message systemMsg = new Message("system", systemPrompt);
        Message userMsg = new Message("user", userQuery);
        LLMRequest llmRequest = new LLMRequest();
        llmRequest.setMessages(List.of(systemMsg, userMsg));
        llmRequest.setTemperature(0.1f); // 低温度，保证LLM严格按格式返回

        // 步骤3：调用LLM，获取工具调用指令
        var llmResponse = llmService.callLLM(llmRequest);
        if (!"success".equals(llmResponse.getStatus())) {
            throw new BusinessException("工具调用失败：LLM调用异常，" + llmResponse.getContent());
        }
        String llmResult = llmResponse.getContent().trim();
        log.info("工具调用：LLM返回指令，sessionId={}，指令={}", sessionId, llmResult);

        // 步骤4：解析LLM返回的工具调用指令（必须是约定的JSON格式）
        ToolCallResult toolCallResult;
        try {
            // 去除LLM返回的多余内容（如```json、```），只保留纯JSON
            llmResult = llmResult.replaceAll("```json|```", "").trim();
            toolCallResult = JSONUtil.toBean(llmResult, ToolCallResult.class);
        } catch (Exception e) {
            throw new BusinessException("工具调用失败：LLM返回指令格式非法，非约定的JSON格式，指令=" + llmResult + "，错误=" + e.getMessage());
        }
        if (toolCallResult == null || toolCallResult.getTool_code() == null || toolCallResult.getTool_code().isBlank()) {
            throw new BusinessException("工具调用失败：LLM返回指令中缺少tool_code（工具标识）");
        }

        // 步骤5：根据toolCode获取工具
        String toolCode = toolCallResult.getTool_code();
        AgentTool tool = toolRegistry.getToolByCode(toolCode);
        if (tool == null) {
            throw new BusinessException("工具调用失败：不存在该工具，toolCode=" + toolCode + "，可用工具=" + toolRegistry.getToolMap().keySet());
        }

        // 步骤6：调用工具执行方法（失败时联动反思纠错）
        Map<String, Object> paramMap = toolCallResult.getParam_map() != null ? toolCallResult.getParam_map() : Map.of();
        log.info("工具调用：开始执行工具，sessionId={}，toolCode={}，paramMap={}", sessionId, toolCode, paramMap);
        String toolExecuteResult;
        try {
            toolExecuteResult = tool.execute(paramMap);
        } catch (Exception e) {
            log.warn("工具执行失败，触发反思纠错，sessionId={}，toolCode={}，原因={}", sessionId, toolCode, e.getMessage());
            return reflectionService.reflectAndRetry(userQuery, toolCode, paramMap, e.getMessage(), sessionId);
        }
        log.info("工具调用：工具执行成功，sessionId={}，toolCode={}", sessionId, toolCode);

        // 步骤7：封装结果返回
        return "【工具调用成功】\n工具名称：" + tool.getToolMeta().getToolName() + "（" + toolCode + "）\n执行结果：\n" + toolExecuteResult;
    }

    /**
     * 直接执行工具（已知 toolCode、paramMap），失败时联动反思纠错，供 TaskPlanService 等多步场景复用
     */
    public String executeToolWithReflection(String toolCode, Map<String, Object> paramMap, String userQuery, String sessionId) throws Exception {
        AgentTool tool = toolRegistry.getToolByCode(toolCode);
        if (tool == null) {
            throw new BusinessException("不存在该工具，toolCode=" + toolCode);
        }
        Map<String, Object> safeParamMap = paramMap != null ? paramMap : Map.of();
        log.info("直接执行工具（含反思纠错），sessionId={}，toolCode={}", sessionId, toolCode);
        try {
            String result = tool.execute(safeParamMap);
            return "【工具调用成功】\n工具名称：" + tool.getToolMeta().getToolName() + "（" + toolCode + "）\n执行结果：\n" + result;
        } catch (Exception e) {
            log.warn("工具执行失败，触发反思纠错，sessionId={}，toolCode={}，原因={}", sessionId, toolCode, e.getMessage());
            return reflectionService.reflectAndRetry(userQuery, toolCode, safeParamMap, e.getMessage(), sessionId);
        }
    }

    /**
     * 构建工具调用系统Prompt（从配置文件加载，配置化，热加载）
     * @param toolMetaStr 工具元数据JSON字符串
     * @return 完整的系统Prompt
     */
    private String buildToolCallSystemPrompt(String toolMetaStr) throws Exception {
        // 加载Prompt模板文件：src/main/resources/prompt/tool-description-prompt.txt
        Resource resource = new ClassPathResource("prompt/tool-description-prompt.txt");
        if (!resource.exists()) {
            throw new BusinessException("工具调用Prompt模板文件不存在：prompt/tool-description-prompt.txt");
        }
        // 读取模板内容
        byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
        String promptTemplate = new String(bytes, StandardCharsets.UTF_8);
        // 替换模板中的占位符：{{TOOL_METAS}} → 工具元数据JSON
        return promptTemplate.replace("{{TOOL_METAS}}", toolMetaStr);
    }
}
