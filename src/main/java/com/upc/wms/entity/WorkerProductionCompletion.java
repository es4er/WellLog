package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("worker_production_completion")
public class WorkerProductionCompletion {
    @TableId(type = IdType.AUTO)
    private Long completionId;
    private Long workerId;
    private String workOrderNo;
    private Long itemId;
    private Long batchId;
    private Long inventoryId;
    private String materialName;
    private String materialCode;
    private String batchNo;
    private BigDecimal qty;
    private String barcodeValue;
    private Long warehouseId;
    private Long locationId;
    private String locationCode;
    private String completionStatus;
    private String remark;
    private LocalDateTime submittedAt;
}
