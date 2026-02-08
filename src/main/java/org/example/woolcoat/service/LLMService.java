package org.example.woolcoat.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.woolcoat.llm.LLMClient;
import org.example.woolcoat.vo.request.LLMRequest;
import org.example.woolcoat.vo.response.LLMResponse;
import org.springframework.stereotype.Service;

/**
 * LLM 服务层（封装调用，添加熔断重试，适配项目 wool-coat）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

    // 自动注入 LLMClient 实现类（通义千问/ Ollama，根据配置切换）
    private final LLMClient llmClient;

    /**
     * 调用 LLM 服务（添加熔断+重试）
     * @param request LLM 请求参数
     * @return LLM 响应结果
     */
    @Retry(name = "llmRetry") // 对应 application.yml 中的 llmRetry 配置
    @CircuitBreaker(name = "llmCircuitBreaker", fallbackMethod = "llmFallback") // 熔断+降级方法
    public LLMResponse callLLM(LLMRequest request) {
        return llmClient.chat(request);
    }

    /**
     * 降级方法：当 LLM 调用失败（超时/熔断）时，返回兜底响应
     */
    public LLMResponse llmFallback(LLMRequest request, Exception e) {
        log.error("LLM 调用降级（项目：wool-coat），原因：", e);
        LLMResponse fallbackResponse = new LLMResponse();
        fallbackResponse.setStatus("fail");
        fallbackResponse.setContent("抱歉，当前AI服务暂时不可用，请稍后再试～");
        fallbackResponse.setErrorMsg("服务降级：" + e.getMessage());
        fallbackResponse.setCostTime(0);
        return fallbackResponse;
    }
}
