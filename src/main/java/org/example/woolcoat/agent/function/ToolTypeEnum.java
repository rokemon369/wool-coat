package org.example.woolcoat.agent.function;

import lombok.Getter;

/**
 * 工具类型枚举（沿用之前的工具分类，无变更）
 */
@Getter
public enum ToolTypeEnum {
    COMMON("通用工具", "基础计算、文本处理等通用能力"),
    INFO("信息工具", "本地RAG检索、网络搜索等信息获取能力"),
    AUTOMATION("自动化工具", "文档导出、日程记录等自动化操作能力");

    private final String name;
    private final String desc;

    ToolTypeEnum(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }
}
