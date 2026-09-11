package com.upc.wms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 3.x 全局配置类
 * SpringBoot 3.x 中无需添加 @EnableOpenApi（自动生效）
 */
@Configuration
public class SwaggerConfig {

    /**
     * 配置 OpenAPI 核心信息
     * @return OpenAPI 实例
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("测井装备 WMS 仓储管理系统 API 文档")
                .version("1.0.0")
                .description("测井装备仓储管理系统（WMS）后端接口文档，基于 SpringBoot 3.5 + Swagger 3.x，涵盖用户权限、主数据、仓库库位、订单、出入库、收货质检、库存盘点调拨、生产计划、系统集成与智能仓储等模块"));
    }
}
