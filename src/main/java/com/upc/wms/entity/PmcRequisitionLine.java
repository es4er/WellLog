package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pmc_requisition_line")
public class PmcRequisitionLine {
    @TableId(type = IdType.AUTO)
    private Long requisitionLineId;
    private Long requisitionId;
    private Long itemId;
    private BigDecimal requiredQty;
    private BigDecimal issuedQty;
    private LocalDateTime requiredAt;
    private String lineStatus;
}
