package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.WhLocation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface WhLocationMapper extends BaseMapper<WhLocation> {

    @Select("SELECT l.* FROM wh_location l JOIN wh_zone z ON l.zone_id=z.zone_id " +
            "WHERE z.warehouse_id=#{warehouseId} AND l.location_status='AVAILABLE'")
    List<WhLocation> selectAvailableLocations(@Param("warehouseId") Long warehouseId);

    @Select("SELECT * FROM wh_location WHERE location_code=#{locationCode}")
    WhLocation selectByCode(@Param("locationCode") String locationCode);

    @Update("UPDATE wh_location SET location_status=#{status} WHERE location_id=#{locationId}")
    int updateStatus(@Param("locationId") Long locationId, @Param("status") String status);
}
