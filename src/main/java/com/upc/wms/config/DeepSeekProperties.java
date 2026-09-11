package com.upc.wms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 大模型配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /** API 密钥 */
    private String apiKey;

    /** Chat Completions 接口地址 */
    private String apiUrl = "https://api.deepseek.com/v1/chat/completions";

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 温度，越低越稳定 */
    private Double temperature = 0.2;

    /** 单次最大输出 token */
    private Integer maxTokens = 2048;

    /** 是否启用 */
    private Boolean enabled = true;
}
