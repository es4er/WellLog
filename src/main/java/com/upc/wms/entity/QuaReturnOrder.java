package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qua_return_order")
public class QuaReturnOrder {
    @TableId(type = IdType.AUTO)
    private Long returnId;
    private String returnNo;
    private Long supplierId;
    private Long issueId;
    private String returnStatus;
    private LocalDateTime createdAt;
}
