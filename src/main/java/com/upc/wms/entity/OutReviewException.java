package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("out_review_exception")
public class OutReviewException {
    @TableId(type = IdType.AUTO)
    private Long exceptionId;
    private Long reviewLineId;
    private String exceptionType;
    private String exceptionDesc;
    private String exceptionStatus;
    private LocalDateTime createdAt;
}
