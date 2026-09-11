package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("md_item")
public class MdItem {
    @TableId(type = IdType.AUTO)
    private Long itemId;
    private String itemCode;
    private String itemName;
    private Long categoryId;
    private Long uomId;
    private String specModel;
    private String itemType;
    private Integer batchControlFlag;
    private Integer serialControlFlag;
    private Integer shelfLifeDays;
    private String specialStorageReq;
    private String erpItemCode;
    private String mesItemCode;
    private String status;
}
