package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("md_batch")
public class MdBatch {
    @TableId(type = IdType.AUTO)
    private Long batchId;
    private Long itemId;
    private String batchNo;
    private Long supplierId;
    private LocalDate manufactureDate;
    private LocalDate expireDate;
    private String qualityStatus;
    private String traceCode;
}
