package com.upc.wms.dto;

import lombok.Data;

import java.util.Map;

/**
 * PMC 工作台助手对话响应：先判意图再分流。
 * <ul>
 *   <li>answer：直接文字回答，不启动 Agent</li>
 *   <li>action：需用户确认后再启动 Agent 流水线</li>
 *   <li>clarify：信息不足，追问澄清</li>
 *   <li>forbidden：角色话题越权，拒绝回答/执行</li>
 * </ul>
 */
@Data
public class PmcAssistantChatResponse {
    /** answer | action | clarify | forbidden */
    private String intent;
    /** 动作类型，如 ORDER_PLAN_REQUISITION；问答时为 null */
    private String action;
    /** 启动 Agent 所需参数 */
    private Map<String, Object> params;
    /** 展示给用户的回复文案 */
    private String reply;
    /** 动作确认提示（intent=action 时） */
    private String confirmMessage;
}
