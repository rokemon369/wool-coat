package org.example.woolcoat.config;

import lombok.extern.slf4j.Slf4j;
import org.example.woolcoat.llm.LLMClient;
import org.example.woolcoat.vo.request.LLMRequest;
import org.example.woolcoat.vo.response.LLMResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.function.Consumer;

/**
 * LLM 降级配置：llm.fallback-switch=true 时，Dashscope 失败自动切换 Ollama
 * 需同时存在 dashscopeLLMClient 和 ollamaLLMClient，故 fallback 时 Ollama 也需加载（通过 llm.type 轮流选主）
 * 简化实现：当 type=dashscope 且 fallback=true 时，本配置生效，需 Ollama 也作为 Bean。
 * 因此 OllamaLLMClient 需在 fallback 模式下也被加载，通过 Condition 控制。
 */
@Slf4j
@Configuration
public class LLMFallbackConfig {

    /**
     * 当 fallback-switch=true 且 type=dashscope 时：主用 Dashscope，失败自动切 Ollama
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "llm.fallback-switch", havingValue = "true")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "llm.type", havingValue = "dashscope")
    public LLMClient fallbackLLMClient(
            @Qualifier("dashscopeLLMClient") LLMClient primary,
            @Qualifier("ollamaLLMClient") LLMClient fallback) {
        log.info("LLM 降级已开启：primary=dashscope，fallback=ollama");
        return new FallbackLLMClient(primary, fallback);
    }

    /**
     * Fallback 封装：主客户端失败时自动尝试备用
     */
    @Slf4j
    static class FallbackLLMClient implements LLMClient {
        private final LLMClient primary;
        private final LLMClient fallback;

        FallbackLLMClient(LLMClient primary, LLMClient fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }

        @Override
        public LLMResponse chat(LLMRequest request) {
            try {
                LLMResponse r = primary.chat(request);
                if (r != null && "success".equals(r.getStatus())) return r;
            } catch (Exception e) {
                log.warn("主 LLM 调用异常，尝试降级到 Ollama", e);
            }
            log.warn("主 LLM 调用失败，尝试降级到 Ollama");
            return fallback.chat(request);
        }

        @Override
        public void chatStream(LLMRequest request, Consumer<String> onChunk) {
            try {
                primary.chatStream(request, onChunk);
            } catch (Exception e) {
                log.warn("主 LLM 流式调用失败，尝试降级到 Ollama", e);
                fallback.chatStream(request, onChunk);
            }
        }
    }
}
