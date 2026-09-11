package com.upc.wms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 编排与 AI 分析相关配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private Boolean enabled = true;

    private String defaultModelProvider = "deepseek";

    private Boolean traceEnabled = true;

    private Boolean manualReviewEnabled = true;

    /** 任务完成后是否自动生成 AI 分析报告 */
    private Boolean autoAnalysisEnabled = true;
}
