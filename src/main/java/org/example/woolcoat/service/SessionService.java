package org.example.woolcoat.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.woolcoat.vo.common.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 会话服务（短期记忆：Redis 缓存，控制 Token 溢出，变量名完全不变）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    // 沿用之前的配置变量名：llm.dashscope.max-token
    @Value("${llm.dashscope.max-token}")
    private int maxToken;
    // 会话过期时间（24 小时，沿用之前的变量名风格）
    private static final long SESSION_EXPIRE_TIME = 24 * 60 * 60;
    // Token 估算系数（沿用之前的变量名，无变更）
    private static final float TOKEN_COEFFICIENT = 2.0f;

    /**
     * 获取会话消息列表（短期记忆，变量名不变）
     */
    @SuppressWarnings("unchecked")
    public List<Message> getSessionMessages(String sessionId) {
        String redisKey = buildSessionRedisKey(sessionId);
        List<Message> messages = (List<Message>) redisTemplate.opsForValue().get(redisKey);
        return messages == null ? new ArrayList<>() : messages;
    }

    /**
     * 保存会话消息（追加 + Token 裁剪，变量名不变）
     */
    public void saveSessionMessage(String sessionId, Message message) {
        String redisKey = buildSessionRedisKey(sessionId);
        List<Message> messages = getSessionMessages(sessionId);
        messages.add(message);
        List<Message> trimmedMessages = trimMessagesByToken(messages);
        redisTemplate.opsForValue().set(redisKey, trimmedMessages, SESSION_EXPIRE_TIME, TimeUnit.SECONDS);

        // 沿用之前的日志格式，变量名不变
        log.info("会话 {} 消息保存成功，当前消息数：{}，Token 估算：{}",
                sessionId, trimmedMessages.size(), calculateMessagesToken(trimmedMessages));
    }

    // 沿用之前的私有方法，变量名不变
    private String buildSessionRedisKey(String sessionId) {
        return "agent:session:messages:" + sessionId;
    }

    // 沿用之前的私有方法，变量名不变
    private int calculateMessagesToken(List<Message> messages) {
        int totalLength = 0;
        for (Message msg : messages) {
            totalLength += msg.getContent().length();
        }
        return (int) (totalLength * TOKEN_COEFFICIENT);
    }

    // 沿用之前的私有方法，变量名不变
    private List<Message> trimMessagesByToken(List<Message> messages) {
        int currentToken = calculateMessagesToken(messages);
        if (currentToken <= maxToken) {
            return messages;
        }

        List<Message> systemMessages = new ArrayList<>();
        List<Message> normalMessages = new ArrayList<>();
        for (Message msg : messages) {
            if ("system".equals(msg.getRole())) {
                systemMessages.add(msg);
            } else {
                normalMessages.add(msg);
            }
        }

        List<Message> trimmedNormalMessages = new ArrayList<>();
        int currentTrimmedToken = calculateMessagesToken(systemMessages);
        for (int i = normalMessages.size() - 1; i >= 0; i--) {
            Message msg = normalMessages.get(i);
            int msgToken = (int) (msg.getContent().length() * TOKEN_COEFFICIENT);
            if (currentTrimmedToken + msgToken <= maxToken) {
                trimmedNormalMessages.add(0, msg);
                currentTrimmedToken += msgToken;
            } else {
                break;
            }
        }

        List<Message> result = new ArrayList<>(systemMessages);
        result.addAll(trimmedNormalMessages);
        log.info("会话消息 Token 溢出，已裁剪，原消息数：{}，裁剪后：{}，Token 估算：{}",
                messages.size(), result.size(), currentTrimmedToken);
        return result;
    }
}
