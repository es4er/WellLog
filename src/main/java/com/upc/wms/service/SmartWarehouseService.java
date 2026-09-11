package com.upc.wms.service;

import com.upc.wms.entity.AgvTask;
import com.upc.wms.entity.BarcodeLabel;
import com.upc.wms.entity.IotEvent;
import com.upc.wms.entity.RfidTag;

import java.util.List;

/**
 * 智能仓储扩展服务接口：条码、RFID、AGV、IoT。
 */
public interface SmartWarehouseService {

    BarcodeLabel bindBarcode(BarcodeLabel label);

    BarcodeLabel scanBarcode(String barcodeValue);

    RfidTag bindRfid(RfidTag tag);

    void updateRfidLastSeen(Long rfidId);

    AgvTask createAgvTask(AgvTask task);

    void updateAgvTaskStatus(Long agvTaskId, String status);

    IotEvent saveIotEvent(IotEvent event);

    List<IotEvent> listUnprocessedIotEvents();

    void markIotEventProcessed(Long eventId);
}
