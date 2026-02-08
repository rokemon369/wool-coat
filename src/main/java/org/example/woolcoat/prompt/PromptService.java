package org.example.woolcoat.prompt;

import java.util.Map;

/**
 * Prompt模板管理服务（统一加载、解析、替换模板）
 */
public interface PromptService {

    /**
     * 加载Prompt模板（从resources/prompt目录）
     * @param templateName 模板文件名（如tool-description-prompt.txt）
     * @return 模板原始内容
     */
    String loadPromptTemplate(String templateName);

    /**
     * 替换模板中的变量
     * @param templateName 模板文件名
     * @param paramMap 变量键值对（如{{TOOL_METAS}} -> 工具列表JSON）
     * @return 替换后的完整Prompt
     */
    String renderPrompt(String templateName, Map<String, String> paramMap);
}
