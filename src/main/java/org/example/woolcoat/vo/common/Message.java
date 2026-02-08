package org.example.woolcoat.vo.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息实体：包含角色和内容
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    // 角色：system（系统提示）、user（用户）、assistant（助手）
    private String role;
    // 消息内容
    private String content;
}
