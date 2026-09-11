package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("out_picking_task")
public class OutPickingTask {
    @TableId(type = IdType.AUTO)
    private Long pickingTaskId;
    private String pickingTaskNo;
    private Long outboundId;
    private Long assignedTo;
    private String taskStatus;
    private String priority;
    private String productName;
    private BigDecimal planQty;
    private String unit;
    private String handlerName;
    private Integer cancelled;
    private String workOrderNo;
    private String requisitionNo;
    private LocalDateTime plannedPickTime;
    private LocalDateTime createdAt;
}
