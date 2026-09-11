package com.upc.wms.dto;

import lombok.Data;

/**
 * 全角色工作台助手对话请求。
 */
@Data
public class WorkbenchAssistantChatRequest {
    /** 角色：pmc / warehouse / quality / inventory / worker / admin */
    private String role;
    /** 用户输入 */
    private String message;
    /** 工人角色可选：当前工人用户 ID */
    private Long workerId;
}
