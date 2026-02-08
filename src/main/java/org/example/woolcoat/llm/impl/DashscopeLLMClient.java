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
import lombok.extern.slf4j.Slf4j;
import org.example.woolcoat.llm.LLMClient;
import org.example.woolcoat.vo.request.LLMRequest;
import org.example.woolcoat.vo.response.LLMResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 通义千问 LLM 客户端（在线模型）
 * 当 llm.type=dashscope 时，该Bean生效（适配项目 wool-coat）
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.type", havingValue = "dashscope")
public class DashscopeLLMClient implements LLMClient {

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
}
