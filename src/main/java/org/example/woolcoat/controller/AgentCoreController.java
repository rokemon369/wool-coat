package org.example.woolcoat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.woolcoat.agent.function.FunctionCallService;
import org.example.woolcoat.agent.plan.TaskPlanService;
import org.example.woolcoat.vo.request.TaskSubmitRequest;
import org.example.woolcoat.vo.request.ToolCallRequest;
import org.example.woolcoat.vo.response.CommonResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent核心控制器（工具调用 + 多步任务规划，项目核心接口）
 */
@Slf4j
@Tag(name = "Agent核心接口", description = "个人智能任务助理 - 工具调用 & 多步任务规划")
@RestController
@RequestMapping("/agent/core")
@RequiredArgsConstructor
public class AgentCoreController {

    // 注入核心服务
    private final FunctionCallService functionCallService;
    private final TaskPlanService taskPlanService;

    /**
     * 工具调用接口（单步工具执行，核心）
     */
    @Operation(summary = "工具调用", description = "根据用户指令自动选择并执行工具，支持计算器/本地RAG/文本摘要/Markdown导出")
    @PostMapping("/tool-call")
    public CommonResponse<String> toolCall(@RequestBody ToolCallRequest request) {
        try {
            String result = functionCallService.callTool(request.getUserQuery(), request.getSessionId());
            return CommonResponse.success(result);
        } catch (Exception e) {
            log.error("工具调用失败", e);
            return CommonResponse.fail(e.getMessage());
        }
    }

    /**
     * 多步任务提交接口（复杂任务拆解执行，核心）
     */
    @Operation(summary = "多步任务提交", description = "将复杂指令拆解为多个原子步骤，按顺序执行工具，汇总结果")
    @PostMapping("/task-submit")
    public CommonResponse<?> taskSubmit(@RequestBody TaskSubmitRequest request) {
        try {
            var taskResult = taskPlanService.submitTask(request.getUserQuery(), request.getSessionId(), request.getUserId());
            return CommonResponse.success(taskResult);
        } catch (Exception e) {
            log.error("多步任务提交失败", e);
            return CommonResponse.fail(e.getMessage());
        }
    }
}
