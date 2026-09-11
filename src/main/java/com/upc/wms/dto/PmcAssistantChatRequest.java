package com.upc.wms.dto;

import lombok.Data;

/**
 * PMC 工作台助手对话请求。
 */
@Data
public class PmcAssistantChatRequest {
    /** 用户输入 */
    private String message;
}
