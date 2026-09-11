package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("md_supplier")
public class MdSupplier {
    @TableId(type = IdType.AUTO)
    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private String contactName;
    private String contactPhone;
    private String erpSupplierCode;
    private String status;
}
