package com.upc.wms.agent.messaging;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class InMemoryAgentMessageBus implements AgentMessageBus {

    private final Map<String, ConcurrentLinkedQueue<AgentMessage>> inboxes = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<AgentMessage> log = new ConcurrentLinkedQueue<>();

    @Override
    public void send(AgentMessage message) {
        if (message.expired(Instant.now())) {
            throw new IllegalArgumentException("Cannot send an expired agent message: " + message.messageId());
        }
        log.add(message);
        inboxes.computeIfAbsent(message.receiver(), ignored -> new ConcurrentLinkedQueue<>()).add(message);
    }

    @Override
    public List<AgentMessage> drain(String receiver) {
        ConcurrentLinkedQueue<AgentMessage> inbox = inboxes.computeIfAbsent(receiver,
                ignored -> new ConcurrentLinkedQueue<>());
        List<AgentMessage> messages = new ArrayList<>();
        AgentMessage message;
        while ((message = inbox.poll()) != null) {
            messages.add(message);
        }
        return List.copyOf(messages);
    }

    @Override
    public List<AgentMessage> history(String conversationId) {
        return log.stream().filter(message -> message.conversationId().equals(conversationId)).toList();
    }
}
