package com.upc.wms.agent.domain;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentDataUtils;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.agent.core.AgentTaskType;
import com.upc.wms.dto.WorkerScanResultVO;
import com.upc.wms.entity.IotEvent;
import com.upc.wms.service.SmartWarehouseService;
import com.upc.wms.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 智能仓储智能体：处理条码/RFID/AGV/IoT 等智能仓储扩展事件。
 * 第一阶段实现 IoT 设备事件的接收与登记，后续可扩展分派到库存/移库等智能体。
 */
@Component
@RequiredArgsConstructor
public class SmartWarehouseAgent implements Agent {

    private final SmartWarehouseService smartWarehouseService;
    private final WorkerService workerService;

    @Override
    public String getName() {
        return AgentNames.SMART_WAREHOUSE;
    }

    @Override
    public boolean support(String taskType) {
        return AgentTaskType.SMART_WAREHOUSE_EVENT_PROCESS.name().equals(taskType)
                || AgentTaskType.WORKER_PICKING_SCAN.name().equals(taskType);
    }

    @Override
    public AgentResult handle(AgentContext context) {
        if (AgentTaskType.WORKER_PICKING_SCAN.name().equals(context.getTaskType())) {
            WorkerScanResultVO scanResult = workerService.processScanFromAgent(context.getData());
            context.put("scanSuccess", scanResult.getSuccess());
            if (Boolean.FALSE.equals(scanResult.getSuccess())) {
                return AgentResult.manualRequired("工人扫码校验未通过，请核对物料/批次/数量")
                        .business(AgentDataUtils.getString(context.getData(), "barcodeValue"))
                        .put("messages", scanResult.getMessages());
            }
            return AgentResult.success("工人扫码校验通过，物料/批次/数量已确认")
                    .business(AgentDataUtils.getString(context.getData(), "barcodeValue"))
                    .next(AgentNames.INVENTORY)
                    .put("scanRecordId", scanResult.getRecord() != null ? scanResult.getRecord().getId() : null);
        }

        Map<String, Object> data = context.getData();

        IotEvent event = new IotEvent();
        event.setDeviceCode(AgentDataUtils.getString(data, "deviceCode"));
        event.setEventType(AgentDataUtils.getString(data, "eventType"));
        event.setLocationId(AgentDataUtils.getLong(data, "locationId"));
        String payloadJson = AgentDataUtils.getString(data, "payloadJson");
        event.setPayloadJson(payloadJson != null ? payloadJson : AgentDataUtils.toJson(data.get("payload")));
        event.setEventTime(LocalDateTime.now());
        event.setProcessedFlag(0);

        IotEvent saved = smartWarehouseService.saveIotEvent(event);
        smartWarehouseService.markIotEventProcessed(saved.getEventId());

        context.put("eventId", saved.getEventId());

        return AgentResult.success("智能仓储事件已接收并处理: " + event.getEventType())
                .business(event.getDeviceCode())
                .next(AgentNames.AUDIT)
                .put("eventId", saved.getEventId())
                .put("eventType", event.getEventType());
    }
}
