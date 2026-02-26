package org.example.woolcoat.llm.impl;

import com.google.gson.Gson;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.example.woolcoat.llm.LLMClient;
import org.example.woolcoat.vo.request.LLMRequest;
import org.example.woolcoat.vo.response.LLMResponse;
import org.example.woolcoat.vo.common.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Ollama 本地 LLM 客户端（离线模型，兜底使用）
 * 当 llm.type=ollama 时，该Bean生效（适配项目 wool-coat）
 */
@Slf4j
@Component("ollamaLLMClient")
@ConditionalOnExpression("'${llm.type:ollama}'=='ollama' || '${llm.fallback-switch:false}'=='true'")
public class OllamaLLMClient implements LLMClient {

    private static final Gson GSON = new Gson();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient okHttpClient;

    @Value("${llm.ollama.base-url}")
    private String baseUrl;

    @Value("${llm.ollama.model}")
    private String model;

    // 初始化 OkHttpClient，设置超时时间（适配本地模型调用，超时时间稍长）
    public OllamaLLMClient() {
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        long startTime = System.currentTimeMillis();
        LLMResponse response = new LLMResponse();

        try {
            // 1. 构建 Ollama 请求参数（适配 Ollama 接口格式）
            OllamaRequest ollamaRequest = new OllamaRequest();
            ollamaRequest.setModel(model);
            ollamaRequest.setMessages(convertToOllamaMessages(request.getMessages()));
            ollamaRequest.setStream(false);
            ollamaRequest.setTemperature(request.getTemperature());

            // 2. 构建 HTTP POST 请求
            String requestJson = GSON.toJson(ollamaRequest);
            RequestBody requestBody = RequestBody.create(requestJson, JSON_MEDIA_TYPE);
            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/chat")
                    .post(requestBody)
                    .build();

            // 3. 调用 Ollama 本地接口
            okhttp3.Response httpResponse = okHttpClient.newCall(httpRequest).execute();
            if (!httpResponse.isSuccessful()) {
                throw new RuntimeException("Ollama 调用失败，响应码：" + httpResponse.code());
            }

            // 4. 解析 Ollama 响应结果
            String responseJson = httpResponse.body().string();
            OllamaResponse ollamaResponse = GSON.fromJson(responseJson, OllamaResponse.class);

            // 5. 封装成功响应结果
            response.setStatus("success");
            response.setContent(ollamaResponse.getMessage().getContent());
            log.info("Ollama 调用成功（项目：wool-coat），耗时：{}ms", System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("Ollama 调用异常（项目：wool-coat）：", e);
            response.setStatus("fail");
            response.setErrorMsg("Ollama 异常：" + e.getMessage());
        }

        // 填充响应耗时
        response.setCostTime(System.currentTimeMillis() - startTime);
        return response;
    }

    @Override
    public void chatStream(LLMRequest request, Consumer<String> onChunk) {
        try {
            OllamaRequest ollamaRequest = new OllamaRequest();
            ollamaRequest.setModel(model);
            ollamaRequest.setMessages(convertToOllamaMessages(request.getMessages()));
            ollamaRequest.setStream(true);
            ollamaRequest.setTemperature(request.getTemperature());

            String requestJson = GSON.toJson(ollamaRequest);
            RequestBody requestBody = RequestBody.create(requestJson, JSON_MEDIA_TYPE);
            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/chat")
                    .post(requestBody)
                    .build();

            try (Response httpResponse = okHttpClient.newCall(httpRequest).execute();
                 ResponseBody body = httpResponse.body()) {
                if (!httpResponse.isSuccessful() || body == null) {
                    throw new RuntimeException("Ollama 流式调用失败，响应码：" + (httpResponse.code()));
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;
                        OllamaStreamChunk chunk = GSON.fromJson(line, OllamaStreamChunk.class);
                        if (chunk != null && chunk.getMessage() != null && chunk.getMessage().getContent() != null) {
                            onChunk.accept(chunk.getMessage().getContent());
                        }
                        if (chunk != null && chunk.getDone()) break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ollama 流式调用异常：", e);
            throw new RuntimeException("Ollama 流式异常：" + e.getMessage());
        }
    }

    // 转换消息格式：项目自定义 Message → Ollama 所需消息格式
    private List<OllamaMessage> convertToOllamaMessages(List<Message> messages) {
        return messages.stream()
                .map(msg -> {
                    OllamaMessage ollamaMessage = new OllamaMessage();
                    ollamaMessage.setRole(msg.getRole());
                    ollamaMessage.setContent(msg.getContent());
                    return ollamaMessage;
                })
                .toList();
    }

    // ========== 内部封装：Ollama 请求/响应 格式 ==========
    @Data
    static class OllamaRequest {
        private String model;
        private List<OllamaMessage> messages;
        private boolean stream;
        private Float temperature;
    }

    @Data
    static class OllamaMessage {
        private String role;
        private String content;
    }

    @Data
    static class OllamaResponse {
        private OllamaMessage message;
        private boolean done;
    }

    /** 流式响应每行 JSON */
    @Data
    static class OllamaStreamChunk {
        private OllamaMessage message;
        private Boolean done;
    }
}
