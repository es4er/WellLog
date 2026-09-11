package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("worker_exception")
public class WorkerException {
    @TableId(type = IdType.AUTO)
    private Long exceptionId;
    private Long pickingTaskId;
    private Long pickingLineId;
    private Long workerId;
    private String workOrderNo;
    private String requisitionNo;
    private String materialName;
    private BigDecimal requiredQty;
    private BigDecimal actualQty;
    private BigDecimal shortageQty;
    private String exceptionType;
    private String exceptionNote;
    private String locationCode;
    private String exceptionStatus;
    private LocalDateTime submittedAt;
}
