/** 库位地图展示的库区编码（与 LocationMapPage 保持一致） */
export const LOCATION_MAP_ZONE_CODES = [
  'ZONE-A', 'ZONE-B', 'ZONE-C', 'ZONE-D', 'ZONE-E',
  'ZONE-QC', 'ZONE-FROZEN', 'ZONE-PREP', 'ZONE-RAW', 'ZONE-FG',
]

export function filterLocationMapZones(zones) {
  return (zones || [])
    .filter((zone) => LOCATION_MAP_ZONE_CODES.includes(zone.zoneCode) && zone.status === 'ENABLED')
    .sort(
      (a, b) =>
        LOCATION_MAP_ZONE_CODES.indexOf(a.zoneCode) - LOCATION_MAP_ZONE_CODES.indexOf(b.zoneCode)
    )
}

/** 收货单仓库下拉：优先用库位地图库区，无库区时回退 wh_warehouse */
export function buildReceiptWarehouseOptions(zones, warehouses) {
  const enabledZones = filterLocationMapZones(zones)
  const warehouseMap = Object.fromEntries((warehouses || []).map((w) => [w.warehouseId, w]))

  if (enabledZones.length) {
    return enabledZones.map((zone) => {
      const wh = warehouseMap[zone.warehouseId]
      const whLabel = wh?.warehouseName || wh?.warehouseCode || `仓库#${zone.warehouseId}`
      return {
        key: `zone-${zone.zoneId}`,
        zoneId: zone.zoneId,
        warehouseId: zone.warehouseId,
        zoneCode: zone.zoneCode,
        zoneName: zone.zoneName,
        label: `${zone.zoneName}（${zone.zoneCode}） · ${whLabel}`,
      }
    })
  }

  return (warehouses || [])
    .filter((w) => w.status !== 'DISABLED')
    .map((w) => ({
      key: `wh-${w.warehouseId}`,
      zoneId: null,
      warehouseId: w.warehouseId,
      zoneCode: null,
      zoneName: null,
      label: `${w.warehouseName}（${w.warehouseCode}）`,
    }))
}
