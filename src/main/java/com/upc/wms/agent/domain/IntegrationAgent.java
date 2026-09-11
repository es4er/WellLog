package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.dto.IntegrationSyncResult;
import com.upc.wms.entity.IntIntegrationMessage;
import com.upc.wms.entity.OrdCustomerOrder;
import com.upc.wms.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 集成智能体：处理 ERP/MES 等外部系统消息；订单类消息会落库客户订单。
 */
@Component
@RequiredArgsConstructor
public class IntegrationAgent implements Agent {

    private final IntegrationService integrationService;

    @Override
    public String getName() {
        return AgentNames.INTEGRATION;
    }

    @Override
    public boolean support(String taskType) {
        return AgentTaskType.INTEGRATION_MESSAGE_PROCESS.name().equals(taskType)
                || AgentTaskType.SAFETY_STOCK_CHECK.name().equals(taskType);
    }

    @Override
    public AgentResult handle(AgentContext context) {
        String taskType = context.getTaskType();
        Map<String, Object> data = context.getData();
        Long systemId = AgentDataUtils.getLong(data, "systemId");

        if (AgentTaskType.SAFETY_STOCK_CHECK.name().equals(taskType)) {
            Object alerts = context.get("alerts");
            if (systemId != null) {
                IntIntegrationMessage msg = integrationService.saveOutboundMessage(systemId, "SAFETY_STOCK_ALERT",
                        context.getTaskNo(), AgentDataUtils.toJson(alerts));
                integrationService.markMessageSuccess(msg.getMessageId());
                context.put("pushMessageId", msg.getMessageId());
            }
            return AgentResult.success("安全库存预警已推送外部系统")
                    .next(AgentNames.AUDIT);
        }

        // 批量同步：从 ERP/MES 拉取订单并落库
        if (AgentDataUtils.getBoolean(data, "syncOrders") || "SYNC_ORDERS".equalsIgnoreCase(AgentDataUtils.getString(data, "messageType"))) {
            IntegrationSyncResult syncResult = integrationService.syncOrdersFromExternal();
            context.put("syncResult", syncResult);
            return AgentResult.success(syncResult.getMessage())
                    .business("SYNC-" + syncResult.getSyncedCount())
                    .next(AgentNames.AUDIT)
                    .put("syncedCount", syncResult.getSyncedCount())
                    .put("skippedCount", syncResult.getSkippedCount())
                    .put("pendingReview", syncResult.getPendingReview())
                    .put("orderNos", syncResult.getOrderNos())
                    .put("syncedAt", syncResult.getSyncedAt());
        }

        String messageType = AgentDataUtils.getString(data, "messageType");
        String businessKey = AgentDataUtils.getString(data, "businessKey");
        String payloadJson = AgentDataUtils.getString(data, "payloadJson");
        if (payloadJson == null) {
            payloadJson = AgentDataUtils.toJson(data.get("payload"));
        }
        if (systemId == null || messageType == null) {
            return AgentResult.failed("缺少 systemId 或 messageType，无法处理集成消息");
        }

        IntIntegrationMessage message = integrationService.saveInboundMessage(systemId, messageType, businessKey, payloadJson);
        try {
            OrdCustomerOrder order = integrationService.processInboundBusinessMessage(message);
            context.put("messageId", message.getMessageId());
            if (order != null) {
                context.put("orderId", order.getOrderId());
                context.put("orderNo", order.getOrderNo());
                return AgentResult.success("外部订单 " + order.getOrderNo() + " 已同步入库，状态待审核")
                        .business(order.getOrderNo())
                        .next(AgentNames.AUDIT)
                        .put("messageId", message.getMessageId())
                        .put("messageType", messageType)
                        .put("orderId", order.getOrderId())
                        .put("orderNo", order.getOrderNo());
            }
            return AgentResult.success("外部系统消息已接收并处理成功")
                    .business(businessKey)
                    .next(AgentNames.AUDIT)
                    .put("messageId", message.getMessageId())
                    .put("messageType", messageType);
        } catch (Exception e) {
            return AgentResult.failed("集成消息处理失败: " + e.getMessage())
                    .business(businessKey)
                    .put("messageId", message.getMessageId());
        }
    }
}
