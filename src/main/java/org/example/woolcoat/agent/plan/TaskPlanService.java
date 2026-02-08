package org.example.woolcoat.agent.plan;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.woolcoat.agent.function.FunctionCallService;
import org.example.woolcoat.agent.function.ToolRegistry;
import org.example.woolcoat.exceptions.BusinessException;
import org.example.woolcoat.service.LLMService;
import org.example.woolcoat.service.SessionService;
import org.example.woolcoat.vo.common.Message;
import org.example.woolcoat.vo.request.LLMRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 多步任务规划核心服务（封装「任务拆解→步骤执行→结果汇总」全流程，Agent核心）
 * 基于工具调用能力，将复杂任务拆解为多个原子步骤，按顺序执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskPlanService {

    // 注入依赖：工具注册中心+工具调用服务+LLM服务+会话服务
    private final ToolRegistry toolRegistry;
    private final FunctionCallService functionCallService;
    private final LLMService llmService;
    private final SessionService sessionService;

    // 最大步骤数（避免LLM拆解过多步骤，导致Token溢出/执行超时）
    private static final int MAX_STEP_NUM = 5;

    /**
     * 核心方法：提交并执行多步任务
     * @param userQuery 用户复杂指令（如"帮我整理Redis相关的知识库内容，汇总成md文件"）
     * @param sessionId 会话ID（无则自动生成，沿用之前的finalSessionId变量名）
     * @param userId 用户ID（默认default_user，沿用之前的变量名）
     * @return 任务执行结果（包含任务ID、步骤、最终结果）
     */
    public Task submitTask(String userQuery, String sessionId, String userId) throws Exception {
        // 1. 初始化任务，处理会话ID/用户ID，沿用之前的finalSessionId命名
        String finalSessionId = sessionId == null || sessionId.isBlank() ? java.util.UUID.randomUUID().toString() : sessionId;
        String finalUserId = userId == null || userId.isBlank() ? "default_user" : userId;
        Task task = new Task();
        task.setSessionId(finalSessionId);
        task.setUserId(finalUserId);
        task.setUserQuery(userQuery);
        task.setStatus(TaskStatusEnum.RUNNING);
        log.info("多步任务规划：任务开始执行，taskId={}，sessionId={}，userQuery={}", task.getTaskId(), finalSessionId, userQuery);

        try {
            // 2. 步骤1：调用LLM，拆解任务为原子步骤
            List<Task.TaskStep> taskSteps = planTaskSteps(task);
            if (taskSteps.isEmpty()) {
                throw new BusinessException("任务拆解失败：LLM未返回任何任务步骤");
            }
            if (taskSteps.size() > MAX_STEP_NUM) {
                throw new BusinessException("任务拆解失败：步骤数超过最大值" + MAX_STEP_NUM + "，请简化指令");
            }
            task.setSteps(taskSteps);
            log.info("多步任务规划：任务拆解完成，taskId={}，共{}个步骤", task.getTaskId(), taskSteps.size());

            // 3. 步骤2：按顺序执行每个任务步骤（调用对应工具）
            executeTaskSteps(task);

            // 4. 步骤3：汇总所有步骤结果，生成任务最终结果
            String finalResult = summarizeTaskResult(task);
            task.setFinalResult(finalResult);
            task.setStatus(TaskStatusEnum.SUCCESS);
            log.info("多步任务规划：任务执行成功，taskId={}", task.getTaskId());

            // 5. 保存任务结果到会话（短期记忆），沿用之前的SessionService
            saveTaskToSession(task);

            return task;
        } catch (Exception e) {
            // 任务执行失败，更新状态和失败原因
            task.setStatus(TaskStatusEnum.FAIL);
            task.setFailReason(e.getMessage());
            log.error("多步任务规划：任务执行失败，taskId={}", task.getTaskId(), e);
            throw new BusinessException("任务执行失败：" + e.getMessage());
        }
    }

    /**
     * 子方法1：调用LLM，拆解任务为原子步骤
     */
    private List<Task.TaskStep> planTaskSteps(Task task) throws Exception {
        // 1. 获取工具元数据+任务规划Prompt模板
        String toolMetaStr = JSONUtil.toJsonStr(toolRegistry.getAllToolMetas());
        String systemPrompt = buildPlanSystemPrompt(toolMetaStr);

        // 2. 构造LLM请求，引导LLM拆解任务
        Message systemMsg = new Message("system", systemPrompt);
        Message userMsg = new Message("user", task.getUserQuery());
        LLMRequest llmRequest = new LLMRequest();
        llmRequest.setMessages(List.of(systemMsg, userMsg));
        llmRequest.setTemperature(0.1f); // 低温度，保证步骤拆解精准

        // 3. 调用LLM，获取步骤拆解结果
        var llmResponse = llmService.callLLM(llmRequest);
        if (!"success".equals(llmResponse.getStatus())) {
            throw new BusinessException("任务拆解失败：LLM调用异常，" + llmResponse.getContent());
        }

        // 4. 解析LLM返回的步骤（约定JSON格式为List<TaskStep>）
        String llmResult = llmResponse.getContent().trim().replaceAll("```json|```", "").trim();
        try {
            return JSONUtil.toList(llmResult, Task.TaskStep.class);
        } catch (Exception e) {
            throw new BusinessException("任务拆解失败：LLM返回步骤格式非法，非约定的JSON格式，步骤=" + llmResult);
        }
    }

    /**
     * 子方法2：按顺序执行每个任务步骤，调用对应工具
     */
    private void executeTaskSteps(Task task) throws Exception {
        List<Task.TaskStep> steps = task.getSteps();
        for (Task.TaskStep step : steps) {
            Integer stepIndex = step.getStepIndex();
            String toolCode = step.getToolCode();
            log.info("多步任务规划：开始执行步骤，taskId={}，stepIndex={}，toolCode={}", task.getTaskId(), stepIndex, toolCode);

            // 校验步骤信息
            if (stepIndex == null || toolCode == null || toolCode.isBlank()) {
                throw new BusinessException("步骤" + stepIndex + "信息非法：缺少stepIndex或toolCode");
            }
            if (toolRegistry.getToolByCode(toolCode) == null) {
                throw new BusinessException("步骤" + stepIndex + "执行失败：不存在该工具，toolCode=" + toolCode);
            }

            // 调用工具执行方法（复用FunctionCallService的工具执行逻辑）
            try {
                // 这里简化实现：直接调用工具的execute方法，后续可整合反思纠错
                String stepResult = toolRegistry.getToolByCode(toolCode).execute(step.getParamMap());
                step.setStepResult(stepResult);
                step.setStepSuccess(true);
                log.info("多步任务规划：步骤执行成功，taskId={}，stepIndex={}", task.getTaskId(), stepIndex);
            } catch (Exception e) {
                throw new BusinessException("步骤" + stepIndex + "执行失败：" + e.getMessage());
            }
        }
    }

    /**
     * 子方法3：汇总所有步骤结果，生成任务最终结果
     */
    private String summarizeTaskResult(Task task) {
        List<Task.TaskStep> steps = task.getSteps();
        // 拼接步骤结果
        String stepResultStr = steps.stream()
                .map(step -> "步骤" + step.getStepIndex() + "：" + step.getStepDesc() + "\n结果：" + step.getStepResult())
                .collect(Collectors.joining("\n\n"));
        // 构造最终结果
        return "【多步任务执行完成】\n任务ID：" + task.getTaskId() + "\n原始指令：" + task.getUserQuery() + "\n\n执行步骤及结果：\n" + stepResultStr + "\n\n【最终总结】：已按你的要求完成所有任务步骤，结果如上。";
    }

    /**
     * 子方法4：保存任务结果到会话（短期记忆），沿用SessionService
     */
    private void saveTaskToSession(Task task) {
        // 构造任务结果消息，沿用之前的Message实体
        Message taskMsg = new Message("assistant", "多步任务执行结果：\n" + task.getFinalResult());
        sessionService.saveSessionMessage(task.getSessionId(), taskMsg);
        log.info("多步任务规划：任务结果已保存到会话，taskId={}，sessionId={}", task.getTaskId(), task.getSessionId());
    }

    /**
     * 构建任务规划系统Prompt（从配置文件加载，配置化）
     */
    private String buildPlanSystemPrompt(String toolMetaStr) throws Exception {
        Resource resource = new ClassPathResource("prompt/plan-prompt.txt");
        if (!resource.exists()) {
            throw new BusinessException("任务规划Prompt模板文件不存在：prompt/plan-prompt.txt");
        }
        byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
        String promptTemplate = new String(bytes, StandardCharsets.UTF_8);
        return promptTemplate.replace("{{TOOL_METAS}}", toolMetaStr).replace("{{MAX_STEP_NUM}}", String.valueOf(MAX_STEP_NUM));
    }
}
