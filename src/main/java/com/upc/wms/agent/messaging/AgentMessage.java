package com.upc.wms.agent.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AgentMessage(
        String messageId,
        String conversationId,
        String correlationId,
        String sender,
        String receiver,
        MessageType type,
        String schemaVersion,
        Map<String, Object> payload,
        Instant createdAt,
        Instant deadline,
        String traceId) {

    public AgentMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(type, "type");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        schemaVersion = schemaVersion == null ? "1.0" : schemaVersion;
        traceId = traceId == null ? conversationId : traceId;
    }

    public static AgentMessage create(String conversationId, String sender, String receiver,
                                      MessageType type, Map<String, Object> payload) {
        return new AgentMessage(UUID.randomUUID().toString(), conversationId, null, sender, receiver,
                type, "1.0", payload, Instant.now(), null, conversationId);
    }

    public AgentMessage reply(String replySender, MessageType replyType, Map<String, Object> replyPayload) {
        return new AgentMessage(UUID.randomUUID().toString(), conversationId, messageId,
                replySender, sender, replyType, schemaVersion, replyPayload, Instant.now(), deadline, traceId);
    }

    public boolean expired(Instant now) {
        return deadline != null && now.isAfter(deadline);
    }
}
