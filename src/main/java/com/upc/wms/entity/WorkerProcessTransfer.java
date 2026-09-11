package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("worker_process_transfer")
public class WorkerProcessTransfer {
    @TableId(type = IdType.AUTO)
    private Long transferId;
    private Long workerId;
    private String transferCard;
    private String containerCode;
    private String fromProcess;
    private String toProcess;
    private String workOrderNo;
    private Long itemId;
    private String materialName;
    private BigDecimal qty;
    private String transferStatus;
    private String remark;
    private LocalDateTime submittedAt;
}
