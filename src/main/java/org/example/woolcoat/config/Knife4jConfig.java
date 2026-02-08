package org.example.woolcoat.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 新版Knife4j（OpenAPI 3）配置类
 */
@Configuration
public class Knife4jConfig {

    /**
     * 配置OpenAPI文档的基本信息（标题、版本、描述等）
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("项目API文档") // 你的项目名称
                        .version("1.0.0") // 版本
                        .description("这是基于Knife4j OpenAPI 3的接口文档")); // 描述
    }
}