package org.example.woolcoat.vo.request;

import lombok.Data;
import org.example.woolcoat.vo.common.Message;

import java.util.List;

/**
 * LLM 调用请求对象
 */
@Data
public class LLMRequest {
    // 对话消息列表（包含上下文/系统提示）
    private List<Message> messages;
    // 温度（0-1，越高越随机，越低越严谨，默认0.7）
    private Float temperature = 0.7f;
}