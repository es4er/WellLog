package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("worker_replenish_request")
public class WorkerReplenishRequest {
    @TableId(type = IdType.AUTO)
    private Long replenishId;
    private Long pickingTaskId;
    private Long pickingLineId;
    private Long workerId;
    private String workOrderNo;
    private String requisitionNo;
    private String productName;
    private Long itemId;
    private String materialCode;
    private String materialName;
    private String specModel;
    private BigDecimal requestQty;
    private String reason;
    private String note;
    private String requestStatus;
    private LocalDateTime submittedAt;
    private String handlerName;
}
