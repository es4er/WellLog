package com.upc.wms.service;

import com.upc.wms.entity.WhLocation;
import com.upc.wms.entity.WhWarehouse;
import com.upc.wms.entity.WhZone;

import java.util.List;

/**
 * 仓库、库区、库位服务接口。
 */
public interface WarehouseService {

    List<WhWarehouse> listWarehouses();

    WhWarehouse addWarehouse(WhWarehouse warehouse);

    WhWarehouse updateWarehouse(WhWarehouse warehouse);

    List<WhZone> listZones(Long warehouseId);

    WhZone addZone(WhZone zone);

    List<WhLocation> listLocations(Long zoneId);

    WhLocation getLocationDetail(Long locationId);

    List<WhLocation> listAvailableLocations(Long warehouseId);

    WhLocation addLocation(WhLocation location);

    void updateLocationStatus(Long locationId, String status);
}
