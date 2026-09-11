package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.common.BusinessException;
import com.upc.wms.common.NoGenerator;
import com.upc.wms.dto.CreateInventoryRequest;
import com.upc.wms.dto.DownShelfRequest;
import com.upc.wms.dto.LocationMapOperateRequest;
import com.upc.wms.entity.InvAlertRecord;
import com.upc.wms.entity.InvFreezeRecord;
import com.upc.wms.entity.InvInventory;
import com.upc.wms.entity.InvSafetyStockRule;
import com.upc.wms.entity.InvTransaction;
import com.upc.wms.entity.MdBatch;
import com.upc.wms.entity.MdItem;
import com.upc.wms.mapper.InvAlertRecordMapper;
import com.upc.wms.mapper.InvFreezeRecordMapper;
import com.upc.wms.mapper.InvInventoryMapper;
import com.upc.wms.mapper.InvSafetyStockRuleMapper;
import com.upc.wms.mapper.InvTransactionMapper;
import com.upc.wms.mapper.MdBatchMapper;
import com.upc.wms.mapper.MdItemMapper;
import com.upc.wms.service.InventoryService;
import com.upc.wms.vo.InvFreezeRecordVO;
import com.upc.wms.vo.InvInventoryDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InvInventoryMapper invInventoryMapper;
    private final InvTransactionMapper invTransactionMapper;
    private final InvFreezeRecordMapper invFreezeRecordMapper;
    private final InvSafetyStockRuleMapper invSafetyStockRuleMapper;
    private final InvAlertRecordMapper invAlertRecordMapper;
    private final MdItemMapper mdItemMapper;
    private final MdBatchMapper mdBatchMapper;

    @Override
    public List<InvInventory> queryInventory() {
        return invInventoryMapper.selectList(null);
    }

    @Override
    public List<InvInventoryDetailVO> queryInventoryWithDetail() {
        return invInventoryMapper.selectListWithDetail();
    }

    @Override
    public InvInventory getInventoryDetail(Long inventoryId) {
        return invInventoryMapper.selectById(inventoryId);
    }

    @Override
    public List<InvInventory> queryInventoryByItem(Long itemId) {
        return invInventoryMapper.selectList(new LambdaQueryWrapper<InvInventory>().eq(InvInventory::getItemId, itemId));
    }

    @Override
    public List<InvInventory> queryInventoryByBatch(Long batchId) {
        return invInventoryMapper.selectList(new LambdaQueryWrapper<InvInventory>().eq(InvInventory::getBatchId, batchId));
    }

    @Override
    public List<InvInventory> queryInventoryByLocation(Long locationId) {
        return invInventoryMapper.selectList(new LambdaQueryWrapper<InvInventory>().eq(InvInventory::getLocationId, locationId));
    }

    @Override
    public List<InvTransaction> queryTransactions(Long itemId, Long batchId) {
        return invTransactionMapper.selectByItemBatch(itemId, batchId);
    }

    @Override
    public List<InvTransaction> traceBatch(Long itemId, Long batchId) {
        return invTransactionMapper.selectByItemBatch(itemId, batchId);
    }

    @Override
    @Transactional
    public InvInventory increaseInventory(Long warehouseId, Long locationId, Long itemId, Long batchId,
                                          BigDecimal qty, String docType, Long docId, Long operatedBy) {
        InvInventory inv = invInventoryMapper.selectByItemBatchLocation(warehouseId, locationId, itemId, batchId);
        BigDecimal before;
        if (inv == null) {
            inv = new InvInventory();
            inv.setWarehouseId(warehouseId);
            inv.setLocationId(locationId);
            inv.setItemId(itemId);
            inv.setBatchId(batchId);
            inv.setOnhandQty(qty);
            inv.setAvailableQty(qty);
            inv.setReservedQty(BigDecimal.ZERO);
            inv.setFrozenQty(BigDecimal.ZERO);
            inv.setInventoryStatus("AVAILABLE");
            inv.setLastTxnAt(LocalDateTime.now());
            invInventoryMapper.insert(inv);
            before = BigDecimal.ZERO;
        } else {
            before = inv.getOnhandQty();
            invInventoryMapper.increaseStock(inv.getInventoryId(), qty);
        }
        writeTransaction(inv.getInventoryId(), warehouseId, locationId, itemId, batchId,
                "INBOUND", docType, docId, before, qty, before.add(qty), operatedBy);
        return inv;
    }

    @Override
    @Transactional
    public void deductInventory(Long inventoryId, BigDecimal qty, String docType, Long docId, Long operatedBy) {
        InvInventory inv = invInventoryMapper.selectById(inventoryId);
        if (inv == null) {
            throw new BusinessException("库存记录不存在: " + inventoryId);
        }
        int rows = invInventoryMapper.deductStock(inventoryId, qty);
        if (rows == 0) {
            throw new BusinessException("库存不足、被冻结或状态不可用，扣减失败");
        }
        BigDecimal before = inv.getOnhandQty();
        writeTransaction(inventoryId, inv.getWarehouseId(), inv.getLocationId(), inv.getItemId(), inv.getBatchId(),
                "OUTBOUND", docType, docId, before, qty.negate(), before.subtract(qty), operatedBy);
    }

    @Override
    @Transactional
    public InvFreezeRecord freezeInventory(Long inventoryId, BigDecimal qty, String reason, Long operatedBy) {
        InvInventory inv = invInventoryMapper.selectById(inventoryId);
        if (inv == null) {
            throw new BusinessException("库存记录不存在: " + inventoryId);
        }
        if (invInventoryMapper.freezeStock(inventoryId, qty) == 0) {
            throw new BusinessException("可用数量不足，冻结失败");
        }
        InvFreezeRecord record = new InvFreezeRecord();
        record.setFreezeNo(NoGenerator.next("FZ"));
        record.setInventoryId(inventoryId);
        record.setFreezeType("FREEZE");
        record.setFreezeQty(qty);
        record.setReason(reason);
        record.setOperatedBy(operatedBy);
        record.setOperatedAt(LocalDateTime.now());
        invFreezeRecordMapper.insert(record);
        writeTransaction(inventoryId, inv.getWarehouseId(), inv.getLocationId(), inv.getItemId(), inv.getBatchId(),
                "FREEZE", "FREEZE", record.getFreezeId(), inv.getOnhandQty(), BigDecimal.ZERO, inv.getOnhandQty(), operatedBy);
        return record;
    }

    @Override
    @Transactional
    public InvFreezeRecord unfreezeInventory(Long inventoryId, BigDecimal qty, String reason, Long operatedBy) {
        InvInventory inv = invInventoryMapper.selectById(inventoryId);
        if (inv == null) {
            throw new BusinessException("库存记录不存在: " + inventoryId);
        }
        if (invInventoryMapper.unfreezeStock(inventoryId, qty) == 0) {
            throw new BusinessException("冻结数量不足，解冻失败");
        }
        InvFreezeRecord record = new InvFreezeRecord();
        record.setFreezeNo(NoGenerator.next("UF"));
        record.setInventoryId(inventoryId);
        record.setFreezeType("UNFREEZE");
        record.setFreezeQty(qty);
        record.setReason(reason);
        record.setOperatedBy(operatedBy);
        record.setOperatedAt(LocalDateTime.now());
        invFreezeRecordMapper.insert(record);
        writeTransaction(inventoryId, inv.getWarehouseId(), inv.getLocationId(), inv.getItemId(), inv.getBatchId(),
                "UNFREEZE", "UNFREEZE", record.getFreezeId(), inv.getOnhandQty(), BigDecimal.ZERO, inv.getOnhandQty(), operatedBy);
        return record;
    }

    @Override
    @Transactional
    public BigDecimal adjustInventory(Long inventoryId, BigDecimal afterQty, String docType, Long docId, Long operatedBy) {
        InvInventory inv = invInventoryMapper.selectById(inventoryId);
        if (inv == null) {
            throw new BusinessException("库存记录不存在: " + inventoryId);
        }
        BigDecimal before = inv.getOnhandQty();
        BigDecimal delta = afterQty.subtract(before);
        inv.setOnhandQty(afterQty);
        inv.setAvailableQty(inv.getAvailableQty().add(delta));
        inv.setLastTxnAt(LocalDateTime.now());
        invInventoryMapper.updateById(inv);
        writeTransaction(inventoryId, inv.getWarehouseId(), inv.getLocationId(), inv.getItemId(), inv.getBatchId(),
                "ADJUST", docType, docId, before, delta, afterQty, operatedBy);
        return delta;
    }

    @Override
    @Transactional
    public void moveInventory(Long inventoryId, Long toLocationId, BigDecimal qty, Long docId, Long operatedBy) {
        InvInventory source = invInventoryMapper.selectById(inventoryId);
        if (source == null) {
            throw new BusinessException("源库存记录不存在: " + inventoryId);
        }
        if (invInventoryMapper.deductStock(inventoryId, qty) == 0) {
            throw new BusinessException("源库位可用库存不足，移库失败");
        }
        writeTransaction(inventoryId, source.getWarehouseId(), source.getLocationId(), source.getItemId(),
                source.getBatchId(), "MOVE", "TRANSFER_ORDER", docId, source.getOnhandQty(),
                qty.negate(), source.getOnhandQty().subtract(qty), operatedBy);
        increaseInventory(source.getWarehouseId(), toLocationId, source.getItemId(), source.getBatchId(),
                qty, "TRANSFER_ORDER", docId, operatedBy);
    }

    @Override
    public InvSafetyStockRule setSafetyStockRule(InvSafetyStockRule rule) {
        InvSafetyStockRule exist = invSafetyStockRuleMapper.selectOne(new LambdaQueryWrapper<InvSafetyStockRule>()
                .eq(InvSafetyStockRule::getWarehouseId, rule.getWarehouseId())
                .eq(InvSafetyStockRule::getItemId, rule.getItemId()));
        if (exist == null) {
            invSafetyStockRuleMapper.insert(rule);
            return rule;
        }
        rule.setRuleId(exist.getRuleId());
        invSafetyStockRuleMapper.updateById(rule);
        return rule;
    }

    @Override
    @Transactional
    public List<InvAlertRecord> checkSafetyStock() {
        List<InvSafetyStockRule> rules = invSafetyStockRuleMapper.selectList(
                new LambdaQueryWrapper<InvSafetyStockRule>().eq(InvSafetyStockRule::getEnabledFlag, 1));
        for (InvSafetyStockRule rule : rules) {
            List<InvInventory> invList = invInventoryMapper.selectList(new LambdaQueryWrapper<InvInventory>()
                    .eq(InvInventory::getWarehouseId, rule.getWarehouseId())
                    .eq(InvInventory::getItemId, rule.getItemId()));
            BigDecimal total = invList.stream().map(InvInventory::getOnhandQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(rule.getMinQty()) < 0) {
                InvAlertRecord alert = new InvAlertRecord();
                alert.setRuleId(rule.getRuleId());
                alert.setWarehouseId(rule.getWarehouseId());
                alert.setItemId(rule.getItemId());
                alert.setAlertType("LOW");
                alert.setAlertQty(total);
                alert.setAlertStatus("OPEN");
                alert.setCreatedAt(LocalDateTime.now());
                invAlertRecordMapper.insert(alert);
            }
        }
        return listInventoryAlerts();
    }

    @Override
    public List<InvAlertRecord> listInventoryAlerts() {
        return invAlertRecordMapper.selectList(new LambdaQueryWrapper<InvAlertRecord>()
                .eq(InvAlertRecord::getAlertStatus, "OPEN"));
    }

    @Override
    public void closeAlert(Long alertId) {
        InvAlertRecord alert = invAlertRecordMapper.selectById(alertId);
        if (alert != null) {
            alert.setAlertStatus("CLOSED");
            invAlertRecordMapper.updateById(alert);
        }
    }

    @Override
    @Transactional
    public InvInventory createInventory(CreateInventoryRequest request) {
        if (request == null || request.getWarehouseId() == null || request.getLocationId() == null) {
            throw new BusinessException("仓库与库位不能为空");
        }
        BigDecimal qty = request.getOnhandQty() != null ? request.getOnhandQty() : request.getAvailableQty();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("库存数量必须大于0");
        }
        ResolvedItemBatch resolved = resolveItemAndBatch(request.getItemId(), request.getItemCode(),
                request.getBatchId(), request.getBatchNo());
        return increaseInventory(request.getWarehouseId(), request.getLocationId(),
                resolved.itemId(), resolved.batchId(), qty, "MANUAL_CREATE", null,
                request.getOperatedBy() != null ? request.getOperatedBy() : 1L);
    }

    @Override
    @Transactional
    public int importInventory(List<CreateInventoryRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (CreateInventoryRequest row : rows) {
            createInventory(row);
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public InvInventory putaway(LocationMapOperateRequest request) {
        if (request == null || request.getLocationId() == null) {
            throw new BusinessException("库位不能为空");
        }
        if (request.getQty() == null || request.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("上架数量必须大于0");
        }
        Long warehouseId = request.getWarehouseId() != null ? request.getWarehouseId() : 1L;
        ResolvedItemBatch resolved = resolveItemAndBatch(null, request.getItemCode(), null, request.getBatchNo());
        return increaseInventory(warehouseId, request.getLocationId(),
                resolved.itemId(), resolved.batchId(), request.getQty(), "PUTAWAY", null, 1L);
    }

    @Override
    @Transactional
    public void downShelf(DownShelfRequest request) {
        if (request == null || request.getInventoryId() == null) {
            throw new BusinessException("库存记录不能为空");
        }
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("下架数量必须大于0");
        }
        Long operatedBy = request.getOperatedBy() != null ? request.getOperatedBy() : 1L;
        deductInventory(request.getInventoryId(), request.getQuantity(), "DOWN_SHELF", null, operatedBy);
    }

    @Override
    public List<InvFreezeRecordVO> listFreezeRecords() {
        return invFreezeRecordMapper.selectListWithDetail();
    }

    private ResolvedItemBatch resolveItemAndBatch(Long itemId, String itemCode, Long batchId, String batchNo) {
        MdItem item;
        if (itemId != null) {
            item = mdItemMapper.selectById(itemId);
        } else if (StringUtils.hasText(itemCode)) {
            item = mdItemMapper.selectByCode(itemCode.trim());
        } else {
            throw new BusinessException("物料编码不能为空");
        }
        if (item == null) {
            throw new BusinessException("物料不存在: " + (itemCode != null ? itemCode : itemId));
        }

        MdBatch batch;
        if (batchId != null) {
            batch = mdBatchMapper.selectById(batchId);
            if (batch == null) {
                throw new BusinessException("批次不存在: " + batchId);
            }
        } else if (StringUtils.hasText(batchNo)) {
            batch = mdBatchMapper.selectOne(new LambdaQueryWrapper<MdBatch>()
                    .eq(MdBatch::getItemId, item.getItemId())
                    .eq(MdBatch::getBatchNo, batchNo.trim())
                    .last("LIMIT 1"));
            if (batch == null) {
                batch = new MdBatch();
                batch.setItemId(item.getItemId());
                batch.setBatchNo(batchNo.trim());
                batch.setQualityStatus("QUALIFIED");
                mdBatchMapper.insert(batch);
            }
        } else {
            throw new BusinessException("批次号不能为空");
        }
        return new ResolvedItemBatch(item.getItemId(), batch.getBatchId());
    }

    private record ResolvedItemBatch(Long itemId, Long batchId) {
    }

    /**
     * 写库存流水（内部复用）。
     * source_doc_id 在库表为非空字段，无业务单据时写入 0。
     */
    private void writeTransaction(Long inventoryId, Long warehouseId, Long locationId, Long itemId, Long batchId,
                                  String businessType, String docType, Long docId,
                                  BigDecimal before, BigDecimal change, BigDecimal after, Long operatedBy) {
        InvTransaction txn = new InvTransaction();
        txn.setTransactionNo(NoGenerator.next("TXN"));
        txn.setInventoryId(inventoryId);
        txn.setWarehouseId(warehouseId);
        txn.setLocationId(locationId);
        txn.setItemId(itemId);
        txn.setBatchId(batchId);
        txn.setBusinessType(businessType);
        txn.setSourceDocType(docType != null ? docType : "MANUAL");
        txn.setSourceDocId(docId != null ? docId : 0L);
        txn.setBeforeQty(before);
        txn.setChangeQty(change);
        txn.setAfterQty(after);
        txn.setOperatedBy(operatedBy != null ? operatedBy : 1L);
        txn.setOperatedAt(LocalDateTime.now());
        invTransactionMapper.insert(txn);
    }
}
