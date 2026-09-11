package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("worker_notification")
public class WorkerNotification {
    @TableId(type = IdType.AUTO)
    private Long notificationId;
    private Long workerId;
    private String notifyType;
    private String title;
    private String content;
    private String relatedDoc;
    private Long pickingTaskId;
    private Integer readFlag;
    private LocalDateTime createdAt;
}
