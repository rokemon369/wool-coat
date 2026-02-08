package org.example.woolcoat.vo.request;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.Data;

/**
 * 多步任务提交请求VO
 */
@Data
public class TaskSubmitRequest {
    @Parameter(description = "用户复杂指令（如帮我整理Redis内容并导出md）", required = true)
    private String userQuery;

    @Parameter(description = "会话ID（无则自动生成）")
    private String sessionId;

    @Parameter(description = "用户ID（默认default_user）")
    private String userId;
}
