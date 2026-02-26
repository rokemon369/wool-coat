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
import org.example.woolcoat.service.memory.MemoryService;
import org.example.woolcoat.vo.common.Message;
import org.example.woolcoat.vo.request.LLMRequest;
import org.example.woolcoat.vo.response.CommonResponse;
import org.example.woolcoat.vo.response.LLMResponse;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.ArrayList;
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
    private final MemoryService memoryService;
    @Qualifier("sseStreamExecutor")
    private final Executor sseStreamExecutor;

    /**
     * 构建带「记忆联动」的对话消息：系统提示（短期+长期记忆融合）+ 会话历史 + 当前用户问题
     */
    private List<Message> buildMessagesWithMemory(String sessionId, String userId, String question) {
        String systemPrompt = memoryService.fuseMemoryToSystemPrompt(sessionId, userId);
        Message systemMsg = new Message("system", systemPrompt);

        List<Message> sessionMessages = sessionService.getSessionMessages(sessionId);
        List<Message> messages = new ArrayList<>();
        messages.add(systemMsg);
        for (Message m : sessionMessages) {
            if (!"system".equals(m.getRole())) messages.add(m);
        }
        messages.add(new Message("user", question));
        return messages;
    }

    /**
     * 基础对话接口（记忆+LLM 联动：短期会话 + 长期偏好融入 system，非流式）
     */
    @Operation(summary = "基础对话", description = "短期会话记忆 + 长期偏好记忆融合进 system，支持多模型")
    @GetMapping("/chat")
    public CommonResponse<LLMResponse> chat(
            @Parameter(description = "会话ID（无则自动生成）") @RequestParam(required = false) String sessionId,
            @Parameter(description = "用户ID（用于长期记忆，默认 default_user）") @RequestParam(required = false, defaultValue = "default_user") String userId,
            @Parameter(description = "用户问题") @RequestParam String question
    ) {
        String finalSessionId = StrUtil.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
        List<Message> messages = buildMessagesWithMemory(finalSessionId, userId, question);

        LLMRequest request = new LLMRequest();
        request.setMessages(messages);
        request.setTemperature(0.7f);

        LLMResponse response = llmService.callLLM(request);

        if ("success".equals(response.getStatus())) {
            Message assistantMsg = new Message("assistant", response.getContent());
            sessionService.saveSessionMessage(finalSessionId, assistantMsg);
        }
        response.setContent("会话ID：" + finalSessionId + "\n\n" + response.getContent());
        return CommonResponse.success(response);
    }

    /**
     * 流式对话接口（记忆+LLM 联动，SSE 流式返回）
     */
    @Operation(summary = "流式对话", description = "同上记忆联动，以 SSE 流式返回助手回复")
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @Parameter(description = "会话ID（无则自动生成）") @RequestParam(required = false) String sessionId,
            @Parameter(description = "用户ID（用于长期记忆）") @RequestParam(required = false, defaultValue = "default_user") String userId,
            @Parameter(description = "用户问题") @RequestParam String question
    ) {
        String finalSessionId = StrUtil.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
        List<Message> messages = buildMessagesWithMemory(finalSessionId, userId, question);

        LLMRequest request = new LLMRequest();
        request.setMessages(messages);
        request.setTemperature(0.7f);

        SseEmitter emitter = new SseEmitter(120_000L);
        StringBuilder fullContent = new StringBuilder();

        sseStreamExecutor.execute(() -> {
            try {
                llmService.callLLMStream(request, chunk -> {
                    if (chunk != null && !chunk.isEmpty()) {
                        fullContent.append(chunk);
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                Message assistantMsg = new Message("assistant", fullContent.toString());
                sessionService.saveSessionMessage(finalSessionId, assistantMsg);
                emitter.send(SseEmitter.event().name("done").data("会话ID：" + finalSessionId));
                emitter.complete();
            } catch (Exception e) {
                log.error("流式对话异常", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
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
