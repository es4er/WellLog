package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("out_review_task")
public class OutReviewTask {
    @TableId(type = IdType.AUTO)
    private Long reviewTaskId;
    private String reviewTaskNo;
    private Long outboundId;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewResult;
}
