package com.upc.wms.service;

import com.upc.wms.dto.PmcAssistantChatResponse;
import com.upc.wms.dto.WorkbenchAssistantChatRequest;

/**
 * 全角色工作台助手：先判意图（问答 / 动作确认 / 澄清），动作确认后由前端启动 Orchestrator。
 */
public interface WorkbenchAssistantService {

    PmcAssistantChatResponse chat(WorkbenchAssistantChatRequest request);
}
