package org.example.woolcoat.controller;

import cn.hutool.core.util.StrUtil;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.woolcoat.service.LLMService;
import org.example.woolcoat.service.SessionService;
import org.example.woolcoat.service.document.DocumentService;
import org.example.woolcoat.vo.common.Message;
import org.example.woolcoat.vo.request.LLMRequest;
import org.example.woolcoat.vo.response.CommonResponse;
import org.example.woolcoat.vo.response.LLMResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Agent 基础接口（对话 + 文档上传，变量名完全不变）
 */
@Slf4j
@Tag(name = "Agent 基础接口", description = "个人智能任务助理 - 基础对话 & 知识库上传")
@RestController
@RequestMapping("/agent/basic")
@RequiredArgsConstructor
public class AgentBasicController {

    private final LLMService llmService;
    private final SessionService sessionService;
    private final DocumentService documentService;

    /**
     * 基础对话接口（带会话记忆，沿用之前的变量名：sessionId、finalSessionId）
     */
    @Operation(summary = "基础对话", description = "带短期会话记忆，支持多模型切换")
    @GetMapping("/chat")
    public CommonResponse<LLMResponse> chat(
            @Parameter(description = "会话ID（无则自动生成）") @RequestParam(required = false) String sessionId,
            @Parameter(description = "用户问题") @RequestParam String question
    ) {
        // 1. 生成会话ID（沿用之前的变量名：finalSessionId）
        String finalSessionId = StrUtil.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;

        // 2. 构建系统消息（沿用之前的变量名：systemMsg）
        Message systemMsg = new Message("system", "你是一个友好的个人智能任务助理，回答简洁明了，贴合用户需求。");

        // 3. 获取会话历史消息（沿用之前的变量名：sessionMessages）
        List<Message> sessionMessages = sessionService.getSessionMessages(finalSessionId);
        if (sessionMessages.isEmpty()) {
            sessionMessages.add(systemMsg);
        }

        // 4. 追加用户当前消息（沿用之前的变量名：userMsg）
        Message userMsg = new Message("user", question);
        sessionMessages.add(userMsg);

        // 5. 构建 LLM 请求（沿用之前的变量名：request）
        LLMRequest request = new LLMRequest();
        request.setMessages(sessionMessages);
        request.setTemperature(0.7f);

        // 6. 调用 LLM 服务（沿用之前的变量名：response）
        LLMResponse response = llmService.callLLM(request);

        // 7. 保存助手响应（沿用之前的变量名：assistantMsg）
        if ("success".equals(response.getStatus())) {
            Message assistantMsg = new Message("assistant", response.getContent());
            sessionService.saveSessionMessage(finalSessionId, assistantMsg);
        }

        // 8. 补充会话ID（沿用之前的变量名：finalSessionId）
        response.setContent("会话ID：" + finalSessionId + "\n\n" + response.getContent());
        return CommonResponse.success(response);
    }

    /**
     * 知识库文档上传接口（沿用之前的变量名：file、userId、tag）
     */
    @Operation(summary = "知识库上传", description = "支持 md/txt/pdf 格式，自动解析分片入库")
    @PostMapping("/upload-document")
    public CommonResponse<String> uploadDocument(
            @Parameter(description = "上传文件") @RequestParam MultipartFile file,
            @Parameter(description = "用户ID（默认 default_user）") @RequestParam(defaultValue = "default_user") String userId,
            @Parameter(description = "文档标签（多个用逗号分隔）") @RequestParam(required = false, defaultValue = "") String tag
    ) {
        try {
            String result = documentService.uploadDocument(file, userId);
            return CommonResponse.success(result);
        } catch (Exception e) {
            log.error("文档上传失败", e);
            return CommonResponse.fail(e.getMessage());
        }
    }
}
