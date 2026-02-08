package org.example.woolcoat.prompt.impl;

import org.example.woolcoat.prompt.PromptService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Prompt模板管理服务实现
 */
@Service
public class PromptServiceImpl implements PromptService {

    // Prompt模板根路径（resources/prompt/）
    private static final String PROMPT_ROOT_PATH = "prompt/";

    @Override
    public String loadPromptTemplate(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            throw new RuntimeException("Prompt模板名称不能为空");
        }
        try {
            Resource resource = new ClassPathResource(PROMPT_ROOT_PATH + templateName);
            if (!resource.exists()) {
                throw new RuntimeException("Prompt模板不存在：" + PROMPT_ROOT_PATH + templateName);
            }
            byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("加载Prompt模板失败：" + e.getMessage());
        }
    }

    @Override
    public String renderPrompt(String templateName, Map<String, String> paramMap) {
        // 1. 加载原始模板
        String template = loadPromptTemplate(templateName);
        // 2. 替换变量（{{变量名}} -> 值）
        if (paramMap != null && !paramMap.isEmpty()) {
            for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String value = entry.getValue() == null ? "" : entry.getValue();
                template = template.replace(placeholder, value);
            }
        }
        return template;
    }
}
