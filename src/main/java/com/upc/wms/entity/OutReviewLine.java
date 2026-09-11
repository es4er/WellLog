package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("out_review_line")
public class OutReviewLine {
    @TableId(type = IdType.AUTO)
    private Long reviewLineId;
    private Long reviewTaskId;
    private Long pickingLineId;
    private BigDecimal reviewQty;
    private String reviewResult;
}
