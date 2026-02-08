package org.example.woolcoat.service.memory.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.woolcoat.entity.UserLongMemory;
import org.example.woolcoat.mapper.UserLongMemoryMapper;
import org.example.woolcoat.service.SessionService;
import org.example.woolcoat.service.memory.MemoryService;
import org.example.woolcoat.utils.TokenUtils;
import org.example.woolcoat.vo.common.Message;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆管理服务实现（整合短期+长期记忆）
 */
@Service
@RequiredArgsConstructor
public class MemoryServiceImpl implements MemoryService {

    private final SessionService sessionService; // 复用原有SessionService处理短期记忆
    private final UserLongMemoryMapper longMemoryMapper;

    // 长期记忆最大Token数（融合时裁剪）
    private static final int LONG_MEMORY_MAX_TOKEN = 1024;

    @Override
    public List<Message> getShortTermMemory(String sessionId) {
        return sessionService.getSessionMessages(sessionId);
    }

    @Override
    public void saveShortTermMemory(String sessionId, Message message) {
        sessionService.saveSessionMessage(sessionId, message);
    }

    @Override
    public void saveLongTermMemory(String userId, String memoryType, String memoryContent, Float weight) {
        if (userId == null || userId.isBlank() || memoryType == null || memoryType.isBlank() || memoryContent.isBlank()) {
            throw new RuntimeException("长期记忆参数不能为空");
        }
        UserLongMemory memory = new UserLongMemory();
        memory.setUserId(userId);
        memory.setMemoryType(memoryType);
        memory.setMemoryContent(memoryContent);
        memory.setWeight(weight == null ? 0.5f : Math.max(0.0f, Math.min(1.0f, weight)));
        memory.setCreateTime(LocalDateTime.now());
        memory.setUpdateTime(LocalDateTime.now());
        longMemoryMapper.insert(memory);
    }

    @Override
    public List<UserLongMemory> getLongTermMemory(String userId, String memoryType) {
        LambdaQueryWrapper<UserLongMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLongMemory::getUserId, userId)
                .eq(UserLongMemory::getMemoryType, memoryType)
                .orderByDesc(UserLongMemory::getWeight) // 按权重排序
                .last("LIMIT 10"); // 最多返回10条
        return longMemoryMapper.selectList(wrapper);
    }

    @Override
    public String fuseMemoryToSystemPrompt(String sessionId, String userId) {
        // 1. 构建长期记忆文本（按权重排序，裁剪Token）
        List<UserLongMemory> longMemories = getLongTermMemory(userId, "preference"); // 示例：只取"偏好"类型
        String longMemoryText = longMemories.stream()
                .map(m -> "用户偏好：" + m.getMemoryContent() + "（权重：" + m.getWeight() + "）")
                .collect(Collectors.joining("\n"));
        // 裁剪长期记忆Token
        longMemoryText = TokenUtils.trimTextByToken(longMemoryText, LONG_MEMORY_MAX_TOKEN);

        // 2. 构建基础system prompt + 融合长期记忆
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("你是一个友好的个人智能任务助理，需遵循以下用户偏好：\n");
        if (!longMemoryText.isBlank()) {
            systemPrompt.append(longMemoryText).append("\n");
        }
        systemPrompt.append("回答简洁明了，结合用户的历史对话和偏好解决问题。");

        return systemPrompt.toString();
    }
}
