package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("worker_scan_record")
public class WorkerScanRecord {
    @TableId(type = IdType.AUTO)
    private Long scanId;
    private Long pickingTaskId;
    private Long pickingLineId;
    private Long workerId;
    private String barcodeValue;
    private Long itemId;
    private Long batchId;
    private String materialName;
    private String batchNo;
    private String scanResult;
    private String resultMessage;
    private LocalDateTime scannedAt;
}
