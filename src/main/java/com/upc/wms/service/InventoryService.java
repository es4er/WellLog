package com.upc.wms.service;

import com.upc.wms.dto.CreateInventoryRequest;
import com.upc.wms.dto.DownShelfRequest;
import com.upc.wms.dto.LocationMapOperateRequest;
import com.upc.wms.entity.InvAlertRecord;
import com.upc.wms.entity.InvFreezeRecord;
import com.upc.wms.entity.InvInventory;
import com.upc.wms.entity.InvSafetyStockRule;
import com.upc.wms.entity.InvTransaction;
import com.upc.wms.vo.InvFreezeRecordVO;
import com.upc.wms.vo.InvInventoryDetailVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存服务接口：库存余额、流水、冻结解冻、调整、移库、安全库存预警。
 * 增减/调整/移库等方法被入库、出库、盘点、移库等业务复用。
 */
public interface InventoryService {

    List<InvInventory> queryInventory();

    List<InvInventoryDetailVO> queryInventoryWithDetail();

    InvInventory getInventoryDetail(Long inventoryId);

    List<InvInventory> queryInventoryByItem(Long itemId);

    List<InvInventory> queryInventoryByBatch(Long batchId);

    List<InvInventory> queryInventoryByLocation(Long locationId);

    List<InvTransaction> queryTransactions(Long itemId, Long batchId);

    List<InvTransaction> traceBatch(Long itemId, Long batchId);

    InvInventory increaseInventory(Long warehouseId, Long locationId, Long itemId, Long batchId,
                                   BigDecimal qty, String docType, Long docId, Long operatedBy);

    void deductInventory(Long inventoryId, BigDecimal qty, String docType, Long docId, Long operatedBy);

    InvFreezeRecord freezeInventory(Long inventoryId, BigDecimal qty, String reason, Long operatedBy);

    InvFreezeRecord unfreezeInventory(Long inventoryId, BigDecimal qty, String reason, Long operatedBy);

    BigDecimal adjustInventory(Long inventoryId, BigDecimal afterQty, String docType, Long docId, Long operatedBy);

    void moveInventory(Long inventoryId, Long toLocationId, BigDecimal qty, Long docId, Long operatedBy);

    InvSafetyStockRule setSafetyStockRule(InvSafetyStockRule rule);

    List<InvAlertRecord> checkSafetyStock();

    List<InvAlertRecord> listInventoryAlerts();

    void closeAlert(Long alertId);

    InvInventory createInventory(CreateInventoryRequest request);

    int importInventory(List<CreateInventoryRequest> rows);

    InvInventory putaway(LocationMapOperateRequest request);

    void downShelf(DownShelfRequest request);

    List<InvFreezeRecordVO> listFreezeRecords();
}
