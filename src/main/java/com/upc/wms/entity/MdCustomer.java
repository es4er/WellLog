package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("md_customer")
public class MdCustomer {
    @TableId(type = IdType.AUTO)
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String contactName;
    private String contactPhone;
    private String erpCustomerCode;
    private String status;
}
