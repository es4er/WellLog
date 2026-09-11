package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agv_task")
public class AgvTask {
    @TableId(type = IdType.AUTO)
    private Long agvTaskId;
    private String agvTaskNo;
    private String sourceDocType;
    private Long sourceDocId;
    private Long fromLocationId;
    private Long toLocationId;
    private String taskStatus;
    private LocalDateTime dispatchedAt;
    private LocalDateTime completedAt;
}
