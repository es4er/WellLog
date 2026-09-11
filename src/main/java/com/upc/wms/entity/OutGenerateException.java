package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出库单生成异常：Agent 在 REQUISITION_OUTBOUND 中因缺料等返回 MANUAL_REQUIRED / FAILED 时落库。
 */
@Data
@TableName("out_generate_exception")
public class OutGenerateException {
    @TableId(type = IdType.AUTO)
    private Long exceptionId;
    private Long agentTaskId;
    private Long requisitionId;
    private String requisitionNo;
    private Long planId;
    private String planNo;
    private Long itemId;
    private String materialName;
    private BigDecimal shortageQty;
    private String exceptionType;
    private String exceptionDesc;
    private String exceptionStatus;
    private String resolveResult;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
