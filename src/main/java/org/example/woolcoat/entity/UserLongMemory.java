package org.example.woolcoat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户长期记忆实体（存储到MySQL，补充Redis短期记忆）
 */
@Data
@TableName("user_long_memory")
public class UserLongMemory {
    @TableId(type = IdType.AUTO)
    private Long id;
    // 用户ID
    private String userId;
    // 记忆类型（偏好/知识/习惯等）
    private String memoryType;
    // 记忆内容
    private String memoryContent;
    // 记忆权重（0-1，越高越重要）
    private Float weight = 0.5f;
    // 创建时间
    private LocalDateTime createTime;
    // 更新时间
    private LocalDateTime updateTime;
}
