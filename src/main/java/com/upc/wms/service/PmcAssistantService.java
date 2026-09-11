package com.upc.wms.service;

import com.upc.wms.dto.PmcAssistantChatRequest;
import com.upc.wms.dto.PmcAssistantChatResponse;

public interface PmcAssistantService {

    /**
     * 解析用户意图并分流：问答 / 动作确认 / 澄清追问。
     */
    PmcAssistantChatResponse chat(PmcAssistantChatRequest request);
}
