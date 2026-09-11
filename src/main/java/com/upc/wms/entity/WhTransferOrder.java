package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wh_transfer_order")
public class WhTransferOrder {
    @TableId(type = IdType.AUTO)
    private Long transferId;
    private String transferNo;
    private Long warehouseId;
    private String transferReason;
    private String transferStatus;
    private Long operatedBy;
    private LocalDateTime operatedAt;
}
