package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.InvFreezeRecord;
import com.upc.wms.vo.InvFreezeRecordVO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface InvFreezeRecordMapper extends BaseMapper<InvFreezeRecord> {

    @Select("SELECT " +
            "fr.freeze_id AS freezeId, fr.freeze_no AS freezeNo, fr.inventory_id AS inventoryId, " +
            "fr.freeze_type AS freezeType, fr.freeze_qty AS freezeQty, fr.reason AS reason, " +
            "fr.operated_by AS operatedBy, fr.operated_at AS operatedAt, " +
            "mi.item_code AS itemCode, mi.item_name AS itemName, " +
            "mb.batch_no AS batchNo, wl.location_code AS locationCode " +
            "FROM inv_freeze_record fr " +
            "LEFT JOIN inv_inventory inv ON inv.inventory_id = fr.inventory_id " +
            "LEFT JOIN md_item mi ON mi.item_id = inv.item_id " +
            "LEFT JOIN md_batch mb ON mb.batch_id = inv.batch_id " +
            "LEFT JOIN wh_location wl ON wl.location_id = inv.location_id " +
            "ORDER BY fr.freeze_id DESC")
    List<InvFreezeRecordVO> selectListWithDetail();
}
