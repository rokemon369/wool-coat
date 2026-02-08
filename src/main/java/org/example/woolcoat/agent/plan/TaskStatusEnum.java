package org.example.woolcoat.agent.plan;

import lombok.Getter;

/**
 * 任务状态枚举（多步任务规划用，标准化任务执行状态）
 */
@Getter
public enum TaskStatusEnum {
    PENDING("待执行", 0),
    RUNNING("执行中", 1),
    SUCCESS("执行成功", 2),
    FAIL("执行失败", 3),
    RETRY("重试中", 4);

    private final String name;
    private final Integer code;

    TaskStatusEnum(String name, Integer code) {
        this.name = name;
        this.code = code;
    }

    // 根据编码获取枚举，方便后续解析
    public static TaskStatusEnum getByCode(Integer code) {
        for (TaskStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return PENDING;
    }
}
