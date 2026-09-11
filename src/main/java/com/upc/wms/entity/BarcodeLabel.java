package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("barcode_label")
public class BarcodeLabel {
    @TableId(type = IdType.AUTO)
    private Long barcodeId;
    private String barcodeValue;
    private String codeType;
    private String bindType;
    private Long bindId;
    private String status;
    private LocalDateTime createdAt;
}
