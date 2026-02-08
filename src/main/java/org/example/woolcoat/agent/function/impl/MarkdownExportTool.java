package org.example.woolcoat.agent.function.impl;

import cn.hutool.core.io.FileUtil;
import org.example.woolcoat.agent.function.AgentTool;
import org.example.woolcoat.agent.function.ToolMeta;
import org.example.woolcoat.agent.function.ToolTypeEnum;
import org.example.woolcoat.exceptions.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 自动化工具——Markdown导出（将文本内容导出为md文件，路径配置化，沿用之前的FileUtil）
 */
@Component
public class MarkdownExportTool implements AgentTool {

    // 配置化导出路径，放在application.yml中，沿用之前的配置命名风格
    @Value("${agent.tool.markdown.export-path:./agent-export/markdown/}")
    private String markdownExportPath;

    private static final ToolMeta TOOL_META;

    static {
        // 入参：content（导出内容，必传）、file_name（文件名，非必传，默认UUID）
        List<ToolMeta.ParamMeta> paramMetas = new ArrayList<>();
        paramMetas.add(new ToolMeta.ParamMeta(
                "content",
                "导出内容",
                "String",
                true,
                "需要导出为Markdown的文本内容，支持Markdown语法"
        ));
        paramMetas.add(new ToolMeta.ParamMeta(
                "file_name",
                "文件名",
                "String",
                false,
                "导出的Markdown文件名，无需加.md后缀，默认使用UUID生成"
        ));
        // 工具元数据
        TOOL_META = new ToolMeta(
                "markdown_export",
                "Markdown导出",
                ToolTypeEnum.AUTOMATION,
                "将文本内容导出为Markdown文件，支持自定义文件名，文件自动保存到指定路径",
                paramMetas,
                "返回文件导出成功信息，包含文件完整路径和文件名"
        );
    }

    @Override
    public ToolMeta getToolMeta() {
        return TOOL_META;
    }

    @Override
    public String execute(Map<String, Object> paramMap) throws Exception {
        // 1. 校验入参
        if (!paramMap.containsKey("content") || paramMap.get("content") == null) {
            throw new BusinessException("Markdown导出工具缺少必传入参：content（导出内容）");
        }
        String content = paramMap.get("content").toString().trim();
        if (content.isBlank()) {
            throw new BusinessException("导出内容不能为空");
        }

        // 2. 处理文件名
        String fileName = paramMap.containsKey("file_name") && paramMap.get("file_name") != null
                ? paramMap.get("file_name").toString().trim()
                : UUID.randomUUID().toString();
        // 拼接.md后缀，避免用户传入
        if (!fileName.endsWith(".md")) {
            fileName += ".md";
        }

        // 3. 导出文件，沿用之前的FileUtil（Hutool），创建目录（不存在则自动创建）
        FileUtil.mkdir(markdownExportPath);
        String fullPath = markdownExportPath + fileName;
        FileUtil.writeString(content, fullPath, "UTF-8");

        // 4. 返回结果
        return "Markdown文件导出成功！\n文件名称：" + fileName + "\n文件完整路径：" + fullPath;
    }
}
