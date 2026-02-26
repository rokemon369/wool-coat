package org.example.woolcoat.llm;

import org.example.woolcoat.vo.request.LLMRequest;
import org.example.woolcoat.vo.response.LLMResponse;

import java.util.function.Consumer;

public interface LLMClient {

    /**
     * 调用 LLM 获取响应（非流式）
     * @param request LLM 请求参数
     * @return LLM 响应结果
     */
    LLMResponse chat(LLMRequest request);

    /**
     * 流式调用 LLM，每产生一段内容就回调 onChunk；结束时不再调用 onChunk。
     * 若某客户端未实现流式，可用 chat() 结果一次性传入 onChunk 做兼容。
     * @param request LLM 请求参数
     * @param onChunk 每段内容的回调（一般为单 token 或一小段文本）
     */
    default void chatStream(LLMRequest request, Consumer<String> onChunk) {
        LLMResponse resp = chat(request);
        if (resp != null && "success".equals(resp.getStatus()) && resp.getContent() != null) {
            onChunk.accept(resp.getContent());
        }
    }
}
