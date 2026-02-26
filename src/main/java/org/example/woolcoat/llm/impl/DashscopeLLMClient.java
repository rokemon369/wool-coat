package org.example.woolcoat.llm.impl;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.models.QwenParam;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MessageManager;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 通义千问 LLM 客户端（在线模型）
 * 当 llm.type=dashscope 时，该Bean生效（适配项目 wool-coat）
 */
@Slf4j
@Component("dashscopeLLMClient")
@ConditionalOnProperty(name = "llm.type", havingValue = "dashscope")
public class DashscopeLLMClient implements LLMClient {

    private static final Gson GSON = new Gson();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final String STREAM_API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Value("${llm.dashscope.api-key}")
    private String apiKey;

    @Value("${llm.dashscope.model}")
    private String model;

    @Override
    public LLMResponse chat(LLMRequest request) {
        long startTime = System.currentTimeMillis();
        LLMResponse response = new LLMResponse();

        try {
            // 1. 配置通义千问 API Key
            System.setProperty("DASHSCOPE_API_KEY", apiKey);

            // 2. 转换请求参数（适配通义千问 SDK 格式）
            MessageManager messageManager = new MessageManager();
            for (org.example.woolcoat.vo.common.Message msg : request.getMessages()) {
                // 映射角色：system/user/assistant 对应通义千问 SDK 的 Role 枚举
                String roleStr;
                switch (msg.getRole()) {
                    case "system":
                        roleStr = Role.SYSTEM.getValue();
                        break;
                    case "assistant":
                        roleStr = Role.ASSISTANT.getValue();
                        break;
                    default:
                        roleStr = Role.USER.getValue();
                        break;
                }

                // 第二步：构建 SDK 自带的 Message 对象
                Message sdkMessage = Message.builder()
                        .role(roleStr)
                        .content(msg.getContent())
                        .build();

                // 第三步：调用 MessageManager 的 add(Message msg) 方法添加消息（核心，解决无 addMessage 的问题）
                messageManager.add(sdkMessage);
            }

            // 3. 构建通义千问请求参数
            QwenParam param = QwenParam.builder()
                    .model(model)
                    .messages(messageManager.get())
                    .temperature(request.getTemperature())
                    .resultFormat(QwenParam.ResultFormat.MESSAGE)
                    .build();

            // 4. 调用通义千问 API 获取响应
            Generation generation = new Generation();
            GenerationResult generationResult = generation.call(param);

            // 5. 封装成功响应结果
            response.setStatus("success");
            response.setContent(generationResult.getOutput().getChoices().get(0).getMessage().getContent());
            log.info("通义千问调用成功（项目：wool-coat），耗时：{}ms", System.currentTimeMillis() - startTime);

        } catch (NoApiKeyException | InputRequiredException e) {
            log.error("LLM 调用参数异常（项目：wool-coat）：", e);
            response.setStatus("fail");
            response.setErrorMsg("参数异常：" + e.getMessage());
        } catch (ApiException e) {
            log.error("LLM 调用 API 异常（项目：wool-coat）：", e);
            response.setStatus("fail");
            response.setErrorMsg("API 异常：" + e.getMessage());
        } catch (Exception e) {
            log.error("LLM 调用未知异常（项目：wool-coat）：", e);
            response.setStatus("fail");
            response.setErrorMsg("未知异常：" + e.getMessage());
        }

        // 填充响应耗时
        response.setCostTime(System.currentTimeMillis() - startTime);
        return response;
    }

    /**
     * 流式调用（基于 DashScope HTTP 流式 API，兼容 SDK 2.12.0）
     * 请求体使用 incremental_output，响应为 SSE，逐段回调 onChunk。
     */
    @Override
    public void chatStream(LLMRequest request, Consumer<String> onChunk) {
        try {
            List<JsonObject> messages = new ArrayList<>();
            for (org.example.woolcoat.vo.common.Message msg : request.getMessages()) {
                JsonObject m = new JsonObject();
                m.addProperty("role", msg.getRole());
                m.addProperty("content", msg.getContent());
                messages.add(m);
            }
            JsonObject input = new JsonObject();
            input.add("messages", GSON.toJsonTree(messages));
            JsonObject parameters = new JsonObject();
            parameters.addProperty("result_format", "message");
            parameters.addProperty("incremental_output", true);
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.add("input", input);
            body.add("parameters", parameters);

            Request httpRequest = new Request.Builder()
                    .url(STREAM_API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(GSON.toJson(body), JSON_MEDIA_TYPE))
                    .build();

            try (Response httpResponse = httpClient.newCall(httpRequest).execute();
                 ResponseBody bodyResp = httpResponse.body()) {
                if (!httpResponse.isSuccessful() || bodyResp == null) {
                    throw new RuntimeException("DashScope 流式调用失败，响应码：" + httpResponse.code());
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(bodyResp.byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) continue;
                        String json = line.substring(5).trim();
                        if ("[DONE]".equals(json)) break;
                        try {
                            JsonObject event = GSON.fromJson(json, JsonObject.class);
                            if (event == null) continue;
                            if (event.has("code") && !event.get("code").isJsonNull()) {
                                int code = event.get("code").getAsInt();
                                if (code != 0) {
                                    throw new RuntimeException("DashScope 返回错误：" + event.toString());
                                }
                            }
                            if (!event.has("output")) continue;
                            JsonObject output = event.getAsJsonObject("output");
                            if (!output.has("choices") || output.getAsJsonArray("choices").isEmpty()) continue;
                            JsonObject choice = output.getAsJsonArray("choices").get(0).getAsJsonObject();
                            if (!choice.has("message")) continue;
                            var msgObj = choice.getAsJsonObject("message");
                            if (!msgObj.has("content") || msgObj.get("content").isJsonNull()) continue;
                            String content = msgObj.get("content").getAsString();
                            if (content != null && !content.isEmpty()) onChunk.accept(content);
                        } catch (Exception e) {
                            if (!json.isEmpty()) log.warn("解析流式事件失败: {}", json, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Dashscope 流式调用异常", e);
            throw new RuntimeException("Dashscope 流式异常：" + e.getMessage());
        }
    }
}
