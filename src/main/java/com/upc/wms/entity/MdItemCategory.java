package com.upc.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("md_item_category")
public class MdItemCategory {
    @TableId(type = IdType.AUTO)
    private Long categoryId;
    private Long parentId;
    private String categoryCode;
    private String categoryName;
}
