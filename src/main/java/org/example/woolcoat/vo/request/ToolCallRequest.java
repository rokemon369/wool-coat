package org.example.woolcoat.vo.request;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.Data;

/**
 * 工具调用请求VO
 */
@Data
public class ToolCallRequest {
    @Parameter(description = "用户指令（如帮我计算1+2*3）", required = true)
    private String userQuery;

    @Parameter(description = "会话ID（无则自动生成）")
    private String sessionId;
}
