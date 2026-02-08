package org.example.woolcoat.vo.response;

import lombok.Data;

/**
 * LLM 调用响应对象
 */
@Data
public class LLMResponse {
    // 响应状态：success / fail
    private String status;
    // 助手返回内容
    private String content;
    // 错误信息（失败时填充）
    private String errorMsg;
    // 响应耗时（毫秒）
    private long costTime;
}
