package org.example.woolcoat.service.memory;


import org.example.woolcoat.entity.UserLongMemory;
import org.example.woolcoat.vo.common.Message;

import java.util.List;

/**
 * 记忆管理服务（整合短期记忆+长期记忆）
 */
public interface MemoryService {

    // ========== 短期记忆（Redis，会话上下文） ==========
    /**
     * 获取会话短期记忆
     */
    List<Message> getShortTermMemory(String sessionId);

    /**
     * 保存会话短期记忆
     */
    void saveShortTermMemory(String sessionId, Message message);

    // ========== 长期记忆（MySQL，用户持久化记忆） ==========
    /**
     * 保存用户长期记忆
     */
    void saveLongTermMemory(String userId, String memoryType, String memoryContent, Float weight);

    /**
     * 查询用户长期记忆
     */
    List<UserLongMemory> getLongTermMemory(String userId, String memoryType);

    // ========== 记忆融合（短期+长期） ==========
    /**
     * 融合短期+长期记忆，生成LLM的system prompt
     */
    String fuseMemoryToSystemPrompt(String sessionId, String userId);
}