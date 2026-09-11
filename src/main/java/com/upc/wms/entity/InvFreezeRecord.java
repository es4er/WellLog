package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inv_freeze_record")
public class InvFreezeRecord {
    @TableId(type = IdType.AUTO)
    private Long freezeId;
    private String freezeNo;
    private Long inventoryId;
    private String freezeType;
    private BigDecimal freezeQty;
    private String reason;
    private Long operatedBy;
    private LocalDateTime operatedAt;
}
