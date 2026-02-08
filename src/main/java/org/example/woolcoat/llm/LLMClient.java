package org.example.woolcoat.llm;

import org.checkerframework.checker.units.qual.C;
import org.example.woolcoat.vo.request.LLMRequest;
import org.example.woolcoat.vo.response.LLMResponse;
import org.springframework.stereotype.Component;

public interface LLMClient {

    /**
     * 调用 LLM 获取响应
     * @param request LLM 请求参数
     * @return LLM 响应结果
     */
    LLMResponse chat(LLMRequest request);
}
