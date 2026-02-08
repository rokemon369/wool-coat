package org.example.woolcoat.agent.plan;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 任务实体（多步任务规划用，包含任务基本信息、步骤、状态、结果）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Task {
    /**
     * 任务唯一ID（自动生成，UUID）
     */
    private String taskId = UUID.randomUUID().toString();
    /**
     * 会话ID（关联会话，保证上下文连贯）
     */
    private String sessionId;
    /**
     * 用户ID（默认default_user，沿用之前的变量名）
     */
    private String userId = "default_user";
    /**
     * 用户原始指令（如"帮我整理Redis相关的知识库内容，汇总成md文件"）
     */
    private String userQuery;
    /**
     * 任务状态（对应TaskStatusEnum）
     */
    private TaskStatusEnum status = TaskStatusEnum.PENDING;
    /**
     * 任务步骤列表（按执行顺序排列，每个步骤调用一个工具）
     */
    private List<TaskStep> steps;
    /**
     * 任务最终结果（所有步骤执行完成后汇总）
     */
    private String finalResult;
    /**
     * 任务执行失败原因（状态为FAIL时非空）
     */
    private String failReason;

    /**
     * 任务步骤实体（原子步骤，对应一次工具调用）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskStep {
        /**
         * 步骤序号（从1开始）
         */
        private Integer stepIndex;
        /**
         * 步骤描述（如"检索Redis相关的知识库内容"）
         */
        private String stepDesc;
        /**
         * 步骤调用的工具Code（如local_rag_search）
         */
        private String toolCode;
        /**
         * 步骤工具入参Map
         */
        private java.util.Map<String, Object> paramMap;
        /**
         * 步骤执行结果（执行成功后非空）
         */
        private String stepResult;
        /**
         * 步骤执行状态（true=成功，false=失败）
         */
        private Boolean stepSuccess = false;
    }
}
