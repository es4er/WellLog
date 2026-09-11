package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("worker_handover")
public class WorkerHandover {
    @TableId(type = IdType.AUTO)
    private Long handoverId;
    private Long pickingTaskId;
    private Long workerId;
    private String workOrderNo;
    private String requisitionNo;
    private String warehouseHandler;
    private LocalDateTime handoverTime;
    private String remark;
}
