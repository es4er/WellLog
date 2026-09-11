package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("qua_return_line")
public class QuaReturnLine {
    @TableId(type = IdType.AUTO)
    private Long returnLineId;
    private Long returnId;
    private Long itemId;
    private Long batchId;
    private BigDecimal returnQty;
}
