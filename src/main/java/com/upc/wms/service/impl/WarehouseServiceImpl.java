package com.upc.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.upc.wms.entity.WhLocation;
import com.upc.wms.entity.WhWarehouse;
import com.upc.wms.entity.WhZone;
import com.upc.wms.mapper.WhLocationMapper;
import com.upc.wms.mapper.WhWarehouseMapper;
import com.upc.wms.mapper.WhZoneMapper;
import com.upc.wms.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 仓库物理结构管理服务实现。
 * 管理 WMS 的三层物理层级：仓库（Warehouse） → 库区（Zone） → 库位（Location）。
 * <ul>
 *   <li>一个仓库包含多个库区，一个库区包含多个库位</li>
 *   <li>库位是最小存储单元，库存记录挂在库位上</li>
 *   <li>库位状态（AVAILABLE/OCCUPIED）决定能否上架</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    /** 仓库 Mapper */
    private final WhWarehouseMapper whWarehouseMapper;
    /** 库区 Mapper */
    private final WhZoneMapper whZoneMapper;
    /** 库位 Mapper */
    private final WhLocationMapper whLocationMapper;

    // ======================== 仓库 ========================

    @Override
    public List<WhWarehouse> listWarehouses() {
        return whWarehouseMapper.selectList(null); // selectList(null) 查询全部
    }

    @Override
    public WhWarehouse addWarehouse(WhWarehouse warehouse) {
        whWarehouseMapper.insert(warehouse);
        return warehouse;
    }

    @Override
    public WhWarehouse updateWarehouse(WhWarehouse warehouse) {
        whWarehouseMapper.updateById(warehouse);
        return warehouse;
    }

    // ======================== 库区 ========================

    /**
     * 查询库区列表，可按 warehouseId 过滤。
     * 使用 LambdaQueryWrapper 构建动态条件查询，
     * 条件写法用方法引用（WhZone::getWarehouseId）而非字符串，避免字段名拼写错误。
     */
    @Override
    public List<WhZone> listZones(Long warehouseId) {
        LambdaQueryWrapper<WhZone> wrapper = new LambdaQueryWrapper<>();
        if (warehouseId != null) {
            // 如果指定了仓库，则只查该仓库下的库区
            wrapper.eq(WhZone::getWarehouseId, warehouseId);
        }
        return whZoneMapper.selectList(wrapper);
    }

    @Override
    public WhZone addZone(WhZone zone) {
        whZoneMapper.insert(zone);
        return zone;
    }

    // ======================== 库位 ========================

    /**
     * 查询库位列表，可按 zoneId 过滤。
     * 库位是库存记录的直接载体，每个库存记录必须绑定一个库位。
     */
    @Override
    public List<WhLocation> listLocations(Long zoneId) {
        LambdaQueryWrapper<WhLocation> wrapper = new LambdaQueryWrapper<>();
        if (zoneId != null) {
            wrapper.eq(WhLocation::getZoneId, zoneId);
        }
        return whLocationMapper.selectList(wrapper);
    }

    @Override
    public WhLocation getLocationDetail(Long locationId) {
        return whLocationMapper.selectById(locationId);
    }

    /**
     * 查询可用的库位列表。
     * 用于上架操作时选择目标库位——只有状态为 AVAILABLE 的库位才能被推荐。
     */
    @Override
    public List<WhLocation> listAvailableLocations(Long warehouseId) {
        return whLocationMapper.selectAvailableLocations(warehouseId);
    }

    /**
     * 新增库位。
     * 默认状态为 AVAILABLE（可用），创建后即可被上架操作使用。
     */
    @Override
    public WhLocation addLocation(WhLocation location) {
        if (location.getLocationStatus() == null) {
            location.setLocationStatus("AVAILABLE"); // 新建库位默认可用
        }
        whLocationMapper.insert(location);
        return location;
    }

    /**
     * 更新库位状态。
     * 状态流转：AVAILABLE → OCCUPIED → AVAILABLE（上架后占用，下架后恢复）。
     * 直接在 Mapper 层用 SQL 更新，比 "先查后改" 少一次数据库交互。
     */
    @Override
    public void updateLocationStatus(Long locationId, String status) {
        whLocationMapper.updateStatus(locationId, status); // 一次SQL更新，高效
    }
}
