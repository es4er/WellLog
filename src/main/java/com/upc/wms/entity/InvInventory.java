package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inv_inventory")
public class InvInventory {
    @TableId(type = IdType.AUTO)
    private Long inventoryId;
    private Long warehouseId;
    private Long locationId;
    private Long itemId;
    private Long batchId;
    private BigDecimal onhandQty;
    private BigDecimal availableQty;
    private BigDecimal reservedQty;
    private BigDecimal frozenQty;
    private String inventoryStatus;
    private LocalDateTime lastTxnAt;
    @Version
    private Integer version;
}
