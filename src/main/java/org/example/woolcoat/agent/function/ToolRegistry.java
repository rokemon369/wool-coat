package org.example.woolcoat.agent.function;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心（自动扫描所有AgentTool实现类，统一管理，供FunctionCallService调用）
 * 实现ApplicationContextAware+InitializingBean，项目启动时自动注册工具
 */
@Slf4j
@Component
public class ToolRegistry implements ApplicationContextAware, InitializingBean {

    // Spring容器上下文，用于扫描Bean
    private ApplicationContext applicationContext;

    /**
     * 工具注册表：key=toolCode，value=AgentTool（核心，快速根据toolCode获取工具）
     */
    @Getter
    private Map<String, AgentTool> toolMap = new HashMap<>();

    /**
     * 获取所有工具的元数据（供LLM识别，返回List<ToolMeta>）
     */
    public List<ToolMeta> getAllToolMetas() {
        return toolMap.values().stream()
                .map(AgentTool::getToolMeta)
                .toList();
    }

    /**
     * 根据toolCode获取工具（核心方法，供FunctionCallService调用）
     */
    public AgentTool getToolByCode(String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            return null;
        }
        return toolMap.get(toolCode.trim().toLowerCase());
    }

    /**
     * 项目启动时执行，自动扫描所有AgentTool实现类，注册到toolMap
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // 扫描Spring容器中所有实现AgentTool接口的Bean
        Map<String, AgentTool> beanMap = applicationContext.getBeansOfType(AgentTool.class);
        Collection<AgentTool> tools = beanMap.values();
        if (tools.isEmpty()) {
            log.warn("工具注册中心：未扫描到任何AgentTool实现类，工具调用功能不可用");
            return;
        }
        // 注册工具到toolMap
        for (AgentTool tool : tools) {
            String toolCode = tool.getToolCode();
            if (toolMap.containsKey(toolCode)) {
                log.error("工具注册中心：存在重复的toolCode，已覆盖，toolCode={}", toolCode);
            }
            toolMap.put(toolCode, tool);
            log.info("工具注册中心：成功注册工具，toolCode={}，toolName={}", toolCode, tool.getToolMeta().getToolName());
        }
        log.info("工具注册中心：工具注册完成，共注册{}个工具", toolMap.size());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}
