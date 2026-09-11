package com.upc.wms.service;

import com.upc.wms.dto.IntegrationSyncResult;
import com.upc.wms.entity.IntIntegrationMessage;
import com.upc.wms.entity.IntSystem;
import com.upc.wms.entity.OrdCustomerOrder;

import java.util.List;
import java.util.Map;

/**
 * ERP/MES 接口集成服务接口。
 */
public interface IntegrationService {

    List<IntSystem> listSystems();

    IntSystem addSystem(IntSystem system);

    IntIntegrationMessage saveInboundMessage(Long systemId, String messageType, String businessKey, String payloadJson);

    IntIntegrationMessage saveOutboundMessage(Long systemId, String messageType, String businessKey, String payloadJson);

    List<IntIntegrationMessage> listPendingMessages();

    void markMessageSuccess(Long messageId);

    void markMessageFailed(Long messageId, String errorMessage);

    void retryMessage(Long messageId);

    /**
     * 处理入站消息：CUSTOMER_ORDER / MES_ORDER 会落库客户订单。
     */
    OrdCustomerOrder processInboundBusinessMessage(IntIntegrationMessage message);

    /**
     * 从 ERP/MES 拉取（模拟）订单并经集成通道落库。
     */
    IntegrationSyncResult syncOrdersFromExternal();

    Map<String, Object> latestOrderSyncSummary();
}
