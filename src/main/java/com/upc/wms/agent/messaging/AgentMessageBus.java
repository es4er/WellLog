package com.upc.wms.agent.messaging;

import java.util.List;

public interface AgentMessageBus {
    void send(AgentMessage message);

    List<AgentMessage> drain(String receiver);

    List<AgentMessage> history(String conversationId);
}
