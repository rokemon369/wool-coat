package org.example.woolcoat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;

/**
 * 鉴权配置（API Key / Bearer Token）
 */
@Data
@ConfigurationProperties(prefix = "woolcoat.auth")
public class AuthProperties {

    /**
     * 是否启用鉴权（生产环境建议 true）
     */
    private boolean enabled = false;

    /**
     * API Key / Token，请求头需携带 Authorization: Bearer &lt;apiKey&gt; 或 X-API-Key: &lt;apiKey&gt;
     */
    private String apiKey = "";

    /**
     * 无需鉴权的路径（支持 Ant 风格，如 /actuator/**）
     */
    private List<String> excludePaths = List.of(
            "/actuator/health",
            "/actuator/info",
            "/error"
    );
}
