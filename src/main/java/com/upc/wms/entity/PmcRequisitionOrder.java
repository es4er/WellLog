package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pmc_requisition_order")
public class PmcRequisitionOrder {
    @TableId(type = IdType.AUTO)
    private Long requisitionId;
    private String requisitionNo;
    private Long sourceOrderId;
    private Long sourcePlanId;
    private String requisitionDept;
    private Long requestedBy;
    private LocalDateTime requestedAt;
    private String requisitionStatus;
}
