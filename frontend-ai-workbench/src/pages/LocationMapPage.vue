<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import * as XLSX from 'xlsx'
import { apiGet, apiPost } from '../api'

const zones = ref([])
const locations = ref([])
const inventory = ref([])
const loading = ref(true)
const WAREHOUSE_ID = 1

const ALLOWED_ZONE_CODES = [
  'ZONE-A', 'ZONE-B', 'ZONE-C', 'ZONE-D', 'ZONE-E',
  'ZONE-QC', 'ZONE-FROZEN', 'ZONE-PREP', 'ZONE-RAW', 'ZONE-FG',
]

async function fetchData() {
  loading.value = true
  try {
    const [z, locs, inv] = await Promise.all([
      apiGet('/warehouse/zone/list'),
      apiGet('/warehouse/location/list'),
      apiGet('/inventory/list-with-detail').catch(() => []),
    ])
    zones.value = (z || [])
      .filter(zone => ALLOWED_ZONE_CODES.includes(zone.zoneCode) && zone.status === 'ENABLED')
      .map(zone => ({
        zoneId: zone.zoneId,
        code: zone.zoneCode || '',
        name: zone.zoneName || '',
        rackCount: zone.rackCount || 4,
        shelfCount: zone.shelfCount || 8,
      }))

    const zoneIds = new Set(zones.value.map(z => z.zoneId))
    locations.value = (locs || [])
      .filter(loc => zoneIds.has(loc.zoneId))
      .map(loc => ({
        locationId: loc.locationId,
        locationCode: loc.locationCode || '',
        locationName: loc.locationName || '',
        locationStatus: loc.locationStatus || '',
        zoneId: loc.zoneId,
        capacityQty: loc.capacityQty || loc.capacity || 0,
      }))

    inventory.value = (inv || []).map(item => ({
      inventoryId: item.inventoryId,
      locationCode: item.locationCode || item.location_code || '',
      itemCode: item.itemCode || item.item_code || '',
      itemName: item.itemName || item.item_name || '',
      batchNo: item.batchNo || item.batch_no || '',
      onhandQty: item.onhandQty ?? item.onhand_qty ?? 0,
      availableQty: item.availableQty ?? item.available_qty ?? 0,
      reservedQty: item.reservedQty ?? item.reserved_qty ?? 0,
      frozenQty: item.frozenQty ?? item.frozen_qty ?? 0,
    }))
  } catch {
    zones.value = []
    locations.value = []
    inventory.value = []
  }
  loading.value = false
}

onMounted(fetchData)

const activeZone = ref('')

watch(zones, (z) => {
  if (z.length > 0 && !activeZone.value) {
    activeZone.value = 'ALL'
  }
}, { immediate: true })

watch(activeZone, () => { currentPage.value = 1; tableSearch.value = '' })

const statusFilter = ref('')
const searchQuery = ref('')
const locDialog = ref(null)
const highlightedLoc = ref(null)
const tableBodyRef = ref(null)

const showPutawayDialog = ref(false)
const putawayLoc = ref(null)
const putawayForm = reactive({ material_code: '', material_name: '', batch_no: '', qty: '' })

const showPickDialog = ref(false)
const pickLoc = ref(null)
const pickForm = reactive({ qty: '', reason: '' })
const pickReasons = ['生产领用', '调拨出库', '销售出库', '报废处理', '其他']

const toastMessage = ref('')
let toastTimer = null
function showToast(msg) {
  toastMessage.value = msg
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toastMessage.value = '' }, 2500)
}

function getZoneShort(code) {
  return (code || '').startsWith('ZONE-') ? code.slice(5) : code
}

const locByCode = computed(() => {
  const map = {}
  for (const loc of locations.value) map[loc.locationCode] = loc
  return map
})

const invByLocCode = computed(() => {
  const map = {}
  for (const inv of inventory.value) {
    if (!inv.locationCode) continue
    if ((inv.onhandQty ?? 0) <= 0) continue
    if (!map[inv.locationCode]) map[inv.locationCode] = []
    map[inv.locationCode].push(inv)
  }
  return map
})

function mapInvToItem(inv) {
  return {
    inventoryId: inv.inventoryId,
    itemCode: inv.itemCode || '',
    itemName: inv.itemName || '',
    batchNo: inv.batchNo || '',
    onhandQty: inv.onhandQty ?? 0,
    availableQty: inv.availableQty ?? 0,
  }
}

function buildCellData(code, z, loc, invItems) {
  const items = invItems.map(mapInvToItem)
  const first = items[0] || {}
  const totalStock = items.reduce((sum, it) => sum + it.onhandQty, 0)
  const totalAvailable = items.reduce((sum, it) => sum + it.availableQty, 0)
  return {
    zone: getZoneShort(z.code),
    zoneCode: z.code,
    rack: code.split('-')[1] || '',
    shelf: code.split('-')[2] || '',
    locationCode: code,
    items,
    itemCount: items.length,
    inventoryId: first.inventoryId || null,
    locationId: loc?.locationId || null,
    locationName: loc?.locationName || '',
    item: first.itemName || first.itemCode || '',
    itemCode: first.itemCode || '',
    batch: first.batchNo || '',
    stock: totalStock,
    availableQty: totalAvailable,
    cap: loc?.capacityQty || 0,
    status: computeStatus(loc, invItems),
    locationStatus: loc?.locationStatus || '',
  }
}

function computeStatus(locData, invItems) {
  if (locData && locData.locationStatus === 'FROZEN') return 'frozen'
  if (locData && (locData.locationStatus === 'DISABLED' || locData.locationStatus === 'MAINTAIN')) return 'disabled'
  if (locData && locData.locationStatus === 'RESERVED') return 'reserved'
  const totalOnhand = invItems.reduce((sum, inv) => sum + (inv.onhandQty ?? 0), 0)
  if (totalOnhand > 0) return 'occupied'
  return 'available'
}

const zoneGrids = computed(() => {
  const grids = {}
  for (const z of zones.value) {
    const prefix = getZoneShort(z.code)
    const rCount = z.rackCount || 4
    const sCount = z.shelfCount || 8
    const cells = []
    for (let r = 1; r <= rCount; r++) {
      for (let s = 1; s <= sCount; s++) {
        const rackStr = String(r).padStart(2, '0')
        const shelfStr = String(s).padStart(2, '0')
        const code = `${prefix}-${rackStr}-${shelfStr}`
        const loc = locByCode.value[code]
        const invItems = invByLocCode.value[code] || []
        cells.push({
          ...buildCellData(code, z, loc, invItems),
          rack: rackStr,
          shelf: shelfStr,
        })
      }
    }
    grids[z.code] = cells
  }
  return grids
})

const anomalyLocs = computed(() => {
  const allCodes = new Set()
  for (const cells of Object.values(zoneGrids.value)) {
    for (const c of cells) allCodes.add(c.locationCode)
  }
  for (const loc of locations.value) allCodes.add(loc.locationCode)

  return inventory.value.filter(inv => inv.locationCode && !allCodes.has(inv.locationCode)).map(inv => ({
    locationCode: inv.locationCode,
    item: inv.itemName || '',
    itemCode: inv.itemCode || '',
    batch: inv.batchNo || '',
    stock: inv.onhandQty ?? 0,
    availableQty: inv.availableQty ?? 0,
    status: 'occupied',
  }))
})

const visibleZones = computed(() => {
  if (activeZone.value === 'ALL') return zones.value
  return zones.value.filter(z => z.code === activeZone.value)
})

const currentCells = computed(() => {
  if (activeZone.value === 'ALL') return Object.values(zoneGrids.value).flat()
  return zoneGrids.value[activeZone.value] || []
})

function getZoneRacks(zoneCode) {
  return [...new Set((zoneGrids.value[zoneCode] || []).map(c => c.rack))].sort()
}

function getZoneShelves(zoneCode) {
  return [...new Set((zoneGrids.value[zoneCode] || []).map(c => c.shelf))].sort()
}

function getZoneGridCols(zoneCode) {
  const count = getZoneShelves(zoneCode).length
  if (count === 0) return '60px 120px'
  return `60px repeat(${count}, 120px)`
}

const racks = computed(() => getZoneRacks(activeZone.value))
const shelves = computed(() => getZoneShelves(activeZone.value))
const gridCols = computed(() => getZoneGridCols(activeZone.value))
const gridShelves = computed(() => shelves.value)

function getShelfLocs(rack) {
  return (zoneGrids.value[activeZone.value] || []).filter(c => c.rack === rack)
}

function getZoneShelfLocs(zoneCode, rack) {
  return (zoneGrids.value[zoneCode] || []).filter(c => c.rack === rack)
}

const zoneStats = computed(() => {
  const cells = currentCells.value
  return {
    total: cells.length,
    occupied: cells.filter(c => c.status === 'occupied').length,
    available: cells.filter(c => c.status === 'available').length,
    frozen: cells.filter(c => c.status === 'frozen').length,
    reserved: cells.filter(c => c.status === 'reserved').length,
    disabled: cells.filter(c => c.status === 'disabled').length,
  }
})

function formatDate() {
  const d = new Date()
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
}

function selectLoc(loc) {
  locDialog.value = loc
  highlightedLoc.value = loc.locationCode
  const allCells = currentCells.value
  const idx = allCells.findIndex(c => c.locationCode === loc.locationCode)
  if (idx >= 0) {
    currentPage.value = Math.floor(idx / pageSize.value) + 1
  }
  nextTick(() => scrollToHighlighted())
}

function scrollToHighlighted() {
  if (!tableBodyRef.value) return
  const row = tableBodyRef.value.querySelector('.loc-highlighted')
  if (row) row.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function locMatchesSearch(loc, q) {
  if (loc.locationCode.toLowerCase().includes(q)) return true
  for (const it of loc.items || []) {
    if (it.itemName && it.itemName.toLowerCase().includes(q)) return true
    if (it.itemCode && it.itemCode.toLowerCase().includes(q)) return true
    if (it.batchNo && it.batchNo.toLowerCase().includes(q)) return true
  }
  if (loc.item && loc.item.toLowerCase().includes(q)) return true
  if (loc.batch && loc.batch.toLowerCase().includes(q)) return true
  if (loc.itemCode && loc.itemCode.toLowerCase().includes(q)) return true
  return false
}

function isLocMuted(loc) {
  if (statusFilter.value && loc.status !== statusFilter.value) return true
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    if (!locMatchesSearch(loc, q)) return true
  }
  return false
}

const statusIcon = { occupied: '●', available: '○', frozen: '❄', reserved: '◆', disabled: '×' }

function frozenTip() {
  return '该库位已被冻结'
}

function setFilter(s) { statusFilter.value = statusFilter.value === s ? '' : s }
function clearFilter() { statusFilter.value = ''; searchQuery.value = '' }

function openPutaway(loc) {
  putawayLoc.value = loc
  putawayForm.material_code = ''
  putawayForm.material_name = ''
  putawayForm.batch_no = ''
  putawayForm.qty = ''
  showPutawayDialog.value = true
}

function onMatCodeChange() {
  putawayForm.material_name = putawayForm.material_code
}

async function confirmPutaway() {
  if (!putawayForm.material_code || !putawayForm.batch_no || !putawayForm.qty) {
    showToast('请填写完整信息')
    return
  }
  const qty = parseFloat(putawayForm.qty)
  if (!qty || qty <= 0) { showToast('数量必须大于0'); return }
  const loc = putawayLoc.value
  try {
    await apiPost('/inventory/putaway', {
      warehouseId: WAREHOUSE_ID,
      locationId: loc.locationId,
      itemCode: putawayForm.material_code,
      batchNo: putawayForm.batch_no,
      qty,
    })
    showPutawayDialog.value = false
    showToast(`上架成功！${loc.locationCode} → ${putawayForm.material_code} x${qty}`)
    await fetchData()
  } catch (e) {
    showToast(`上架失败：${e.message}`)
  }
}

function openPick(loc, invItem = null) {
  const target = invItem || (loc.items?.length === 1 ? loc.items[0] : null)
  if (!target?.inventoryId) {
    if ((loc.items?.length || 0) > 1) {
      selectLoc(loc)
      showToast('该库位有多种物料，请在明细中选择要下架的物料')
      return
    }
    showToast('库存记录缺失，无法下架')
    return
  }
  pickLoc.value = {
    ...loc,
    inventoryId: target.inventoryId,
    item: target.itemName || target.itemCode,
    itemCode: target.itemCode,
    batch: target.batchNo,
    stock: target.onhandQty,
    availableQty: target.availableQty,
  }
  pickForm.qty = ''
  pickForm.reason = '生产领用'
  showPickDialog.value = true
}

async function confirmPick() {
  if (!pickForm.qty) { showToast('请输入下架数量'); return }
  const qty = parseFloat(pickForm.qty)
  if (!qty || qty <= 0) { showToast('数量必须大于0'); return }
  const loc = pickLoc.value
  if (qty > (loc.availableQty ?? loc.stock)) { showToast('下架数量不能超过可用库存'); return }
  if (!loc.inventoryId) { showToast('库存记录缺失，无法下架'); return }
  try {
    await apiPost('/inventory/down-shelf', {
      inventoryId: loc.inventoryId,
      quantity: qty,
      reason: pickForm.reason || '生产领用',
    })
    showPickDialog.value = false
    showToast(`下架成功：${loc.locationCode} 下架 ${qty} 件`)
    await fetchData()
  } catch (e) {
    showToast(`下架失败：${e.message}`)
  }
}

function handleExport() {
  const rows = allLocList.value
  const zoneName = activeZone.value === 'ALL' ? '全部库区' : ((zones.value || []).find(z => z.code === activeZone.value)?.name || activeZone.value)
  const header = [['库位编码', '所属库区', '状态', '物料编码', '物料名称', '批次号', '占用数量']]
  const data = rows.map(l => [
    l.locationCode,
    (zones.value || []).find(z => z.code === l.zoneCode)?.name || l.zone,
    statusMap[l.status]?.label,
    l.itemCode || '',
    l.item || '',
    l.batch || '',
    l.stock || 0,
  ])
  const ws = XLSX.utils.aoa_to_sheet([...header, ...data])
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '库位清单')
  XLSX.writeFile(wb, `库位清单_${zoneName}_${formatDate()}.xlsx`)
  showToast('导出成功！')
}

const statusMap = {
  occupied: { label: '占用', cls: 'status-occupied' },
  available: { label: '空闲', cls: 'status-available' },
  frozen: { label: '冻结', cls: 'status-frozen' },
  reserved: { label: '预留', cls: 'status-reserved' },
  disabled: { label: '停用', cls: 'status-disabled' },
}

const pageSize = ref(10)
const currentPage = ref(1)
const tableSearch = ref('')

function flattenCellInventoryRows(cells) {
  const rows = []
  for (const cell of cells) {
    if ((cell.items || []).length) {
      for (const it of cell.items) {
        rows.push({
          locationCode: cell.locationCode,
          zoneCode: cell.zoneCode,
          zone: cell.zone,
          status: cell.status,
          locationId: cell.locationId,
          item: it.itemName,
          itemCode: it.itemCode,
          batch: it.batchNo,
          stock: it.onhandQty,
          availableQty: it.availableQty,
          inventoryId: it.inventoryId,
          itemCount: cell.itemCount,
          rowKey: `${cell.locationCode}-${it.inventoryId}`,
        })
      }
    } else {
      rows.push({
        ...cell,
        rowKey: cell.locationCode,
      })
    }
  }
  return rows
}

const allLocList = computed(() => {
  let list = flattenCellInventoryRows(currentCells.value)
  if (tableSearch.value.trim()) {
    const q = tableSearch.value.trim().toLowerCase()
    list = list.filter((row) =>
      row.locationCode.toLowerCase().includes(q) ||
      (row.item && row.item.toLowerCase().includes(q)) ||
      (row.batch && row.batch.toLowerCase().includes(q)) ||
      (row.itemCode && row.itemCode.toLowerCase().includes(q))
    )
  }
  return list
})

const totalLocItems = computed(() => allLocList.value.length)
const totalLocPages = computed(() => Math.ceil(totalLocItems.value / pageSize.value))
const pagedLocList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return allLocList.value.slice(start, start + pageSize.value)
})

function changePage(p) {
  if (p < 1 || p > totalLocPages.value) return
  currentPage.value = p
}

function changeSize(size) {
  pageSize.value = size
  currentPage.value = 1
}

</script>

<template>
  <div class="map-page">
    <header class="map-header">
      <div>
        <h1>库位地图</h1>
      </div>
      <div class="map-header-right">
        <div class="map-search-bar">
          <input v-model="searchQuery" placeholder="按物料 / 批次检索库位" class="search-input" />
          <button v-if="searchQuery || statusFilter" type="button" class="search-clear" @click="clearFilter">×</button>
        </div>
        <div class="map-legend">
          <span class="legend-item"><span class="dot occupied"></span>占用</span>
          <span class="legend-item"><span class="dot available"></span>空闲</span>
          <span class="legend-item"><span class="dot frozen"></span>冻结</span>
          <span class="legend-item"><span class="dot reserved"></span>预留</span>
          <span class="legend-item"><span class="dot disabled"></span>停用</span>
        </div>
        <button class="tb-btn export-btn" @click="handleExport">导出</button>
      </div>
    </header>

    <div class="map-tabs-row">
      <div class="map-tabs">
        <div v-if="zones.length === 0 && !loading" class="empty-zones">暂无库区数据，请先维护仓库库位</div>
        <button
          :class="['zone-tab', { active: activeZone === 'ALL' }]"
          @click="activeZone = 'ALL'; locDialog = null; highlightedLoc = null"
        >
          <strong>ALL</strong>
          <span>全部库区</span>
        </button>
        <button
          v-for="z in zones"
          :key="z.code"
          :class="['zone-tab', { active: activeZone === z.code }]"
          @click="activeZone = z.code; locDialog = null; highlightedLoc = null"
        >
          <strong>{{ z.code }}</strong>
          <span>{{ z.name }}</span>
        </button>
      </div>
    </div>
    <div class="map-strip">
      <div class="strip-item" :class="{ active: !statusFilter }" @click="clearFilter">
        <span class="strip-num">{{ zoneStats.total }}</span>
        <span class="strip-lbl">总库位数</span>
      </div>
      <div class="strip-item" :class="{ active: statusFilter === 'occupied' }" @click="setFilter('occupied')">
        <span class="strip-num" style="color:#4b7a5f">{{ zoneStats.occupied }}</span>
        <span class="strip-lbl">占用</span>
      </div>
      <div class="strip-item" :class="{ active: statusFilter === 'available' }" @click="setFilter('available')">
        <span class="strip-num" style="color:#8ba896">{{ zoneStats.available }}</span>
        <span class="strip-lbl">空闲</span>
      </div>
      <div class="strip-item" :class="{ active: statusFilter === 'frozen' }" @click="setFilter('frozen')">
        <span class="strip-num" style="color:#b4553f">{{ zoneStats.frozen }}</span>
        <span class="strip-lbl">冻结</span>
      </div>
      <div class="strip-item" :class="{ active: statusFilter === 'disabled' }" @click="setFilter('disabled')">
        <span class="strip-num" style="color:#9caaa3">{{ zoneStats.disabled }}</span>
        <span class="strip-lbl">停用</span>
      </div>
    </div>

    <div class="map-body">
      <div v-if="loading" class="loading-indicator">加载中...</div>
      <div v-else-if="zones.length === 0" class="empty-state">暂无库区数据，请先在系统设置中维护仓库库位</div>
      <template v-else>
        <template v-if="activeZone === 'ALL'">
          <div v-for="z in visibleZones" :key="z.code" class="zone-block">
            <div class="zone-block-head">
              <h3 class="zone-block-title">{{ z.name }}</h3>
              <span class="zone-block-meta">{{ getZoneRacks(z.code).length }} 排 × {{ getZoneShelves(z.code).length }} 列 = {{ (zoneGrids[z.code] || []).length }} 库位</span>
            </div>
            <div class="map-floor">
              <div class="floor-header" :style="{ gridTemplateColumns: getZoneGridCols(z.code) }">
                <span class="floor-corner"></span>
                <span v-for="s in getZoneShelves(z.code)" :key="s" class="floor-col-label">货架 {{ s }}</span>
              </div>
              <div v-for="rack in getZoneRacks(z.code)" :key="rack" class="floor-row" :style="{ gridTemplateColumns: getZoneGridCols(z.code) }">
                <span class="floor-row-label">排 {{ rack }}</span>
                <div
                  v-for="loc in getZoneShelfLocs(z.code, rack)"
                  :key="loc.locationCode"
                  :class="['floor-cell', `cell-${loc.status}`, { active: locDialog === loc, muted: isLocMuted(loc) }]"
                  @click="selectLoc(loc)"
                  :title="loc.status === 'frozen' ? frozenTip() : undefined"
                >
                  <span class="cell-code">{{ loc.locationCode }}</span>
                  <span class="cell-type">标准货架</span>
                  <span class="cell-status"><i class="stat-dot" :class="loc.status"></i>{{ statusMap[loc.status]?.label }}</span>
                  <div v-if="loc.status === 'occupied'" class="cell-items-stack">
                    <div
                      v-for="it in (loc.items || []).slice(0, 2)"
                      :key="it.inventoryId"
                      class="cell-item-row"
                    >
                      <span class="cell-item-name">{{ it.itemName || it.itemCode }}</span>
                      <span class="cell-item-qty">{{ it.onhandQty }}</span>
                    </div>
                    <button
                      v-if="loc.itemCount > 2"
                      type="button"
                      class="cell-more-btn"
                      @click.stop="selectLoc(loc)"
                    >
                      +{{ loc.itemCount - 2 }} 种 · 展开
                    </button>
                    <span v-else-if="loc.itemCount > 1" class="cell-multi-hint">共 {{ loc.itemCount }} 种 · 合计 {{ loc.stock }}</span>
                  </div>
                  <button v-if="loc.status === 'available'" type="button" class="cell-action putaway-btn" @click.stop="openPutaway(loc)">+ 上架</button>
                  <button
                    v-if="loc.status === 'occupied' && loc.itemCount <= 1"
                    type="button"
                    class="cell-action pick-btn"
                    @click.stop="openPick(loc)"
                  >下架</button>
                  <button
                    v-if="loc.status === 'occupied' && loc.itemCount > 1"
                    type="button"
                    class="cell-action pick-btn"
                    @click.stop="selectLoc(loc)"
                  >明细</button>
                </div>
              </div>
            </div>
          </div>
        </template>
        <div v-else class="map-floor">
          <div class="floor-header" :style="{ gridTemplateColumns: gridCols }">
            <span class="floor-corner"></span>
            <span v-for="s in gridShelves" :key="s" class="floor-col-label">货架 {{ s }}</span>
          </div>
          <div v-for="rack in racks" :key="rack" class="floor-row" :style="{ gridTemplateColumns: gridCols }">
            <span class="floor-row-label">排 {{ rack }}</span>
            <div
              v-for="loc in getShelfLocs(rack)"
              :key="loc.locationCode"
              :class="['floor-cell', `cell-${loc.status}`, { active: locDialog === loc, muted: isLocMuted(loc) }]"
              @click="selectLoc(loc)"
              :title="loc.status === 'frozen' ? frozenTip() : undefined"
            >
              <span class="cell-code">{{ loc.locationCode }}</span>
              <span class="cell-type">标准货架</span>
              <span class="cell-status"><i class="stat-dot" :class="loc.status"></i>{{ statusMap[loc.status]?.label }}</span>
              <div v-if="loc.status === 'occupied'" class="cell-items-stack">
                <div
                  v-for="it in (loc.items || []).slice(0, 2)"
                  :key="it.inventoryId"
                  class="cell-item-row"
                >
                  <span class="cell-item-name">{{ it.itemName || it.itemCode }}</span>
                  <span class="cell-item-qty">{{ it.onhandQty }}</span>
                </div>
                <button
                  v-if="loc.itemCount > 2"
                  type="button"
                  class="cell-more-btn"
                  @click.stop="selectLoc(loc)"
                >
                  +{{ loc.itemCount - 2 }} 种 · 展开
                </button>
                <span v-else-if="loc.itemCount > 1" class="cell-multi-hint">共 {{ loc.itemCount }} 种 · 合计 {{ loc.stock }}</span>
              </div>
              <button v-if="loc.status === 'available'" type="button" class="cell-action putaway-btn" @click.stop="openPutaway(loc)">+ 上架</button>
              <button
                v-if="loc.status === 'occupied' && loc.itemCount <= 1"
                type="button"
                class="cell-action pick-btn"
                @click.stop="openPick(loc)"
              >下架</button>
              <button
                v-if="loc.status === 'occupied' && loc.itemCount > 1"
                type="button"
                class="cell-action pick-btn"
                @click.stop="selectLoc(loc)"
              >明细</button>
            </div>
          </div>
        </div>
        <div v-if="anomalyLocs.length > 0" class="anomaly-section">
          <div class="anomaly-head">
            <h3>异常库位</h3>
            <span class="anomaly-badge">{{ anomalyLocs.length }} 条</span>
          </div>
          <p class="anomaly-desc">以下库位存在于库存记录中，但不在库位主数据中，可能是数据异常或已删除的库位。</p>
          <div class="anomaly-grid">
            <div v-for="loc in anomalyLocs" :key="loc.locationCode" class="anomaly-card">
              <span class="anomaly-code">{{ loc.locationCode }}</span>
              <span class="anomaly-item">{{ loc.item || '—' }}</span>
              <span class="anomaly-qty">{{ loc.stock }} 件</span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <div v-if="locDialog" class="overlay" @click="locDialog = null; highlightedLoc = null"></div>
    <div v-if="locDialog" class="dialog">
      <div class="dialog-head">
        <span>库位明细 · {{ locDialog.locationCode }}</span>
        <button class="dialog-close" @click="locDialog = null; highlightedLoc = null">×</button>
      </div>
      <div class="dialog-body">
        <div class="dl-row"><span class="dl-lbl">库位编码</span><span class="dl-val">{{ locDialog.locationCode }}</span></div>
        <div class="dl-row"><span class="dl-lbl">所属库区</span><span class="dl-val">{{ (zones || []).find(z => z.code === locDialog.zoneCode)?.name || locDialog.zone }}</span></div>
        <div class="dl-row">
          <span class="dl-lbl">库位状态</span>
          <span :class="['info-badge', locDialog.status]">{{ statusMap[locDialog.status]?.label }}</span>
        </div>
        <template v-if="locDialog.status === 'occupied' || (locDialog.status === 'frozen' && (locDialog.items?.length || locDialog.item))">
          <div class="dl-divider"></div>
          <div class="dl-subtitle">
            存放物料
            <span v-if="locDialog.itemCount > 1" class="dl-subtitle-meta">共 {{ locDialog.itemCount }} 种 · 合计 {{ locDialog.stock }}</span>
          </div>
          <table class="dl-table">
            <thead>
              <tr>
                <th>物料编码</th>
                <th>物料名称</th>
                <th>批次号</th>
                <th>数量</th>
                <th v-if="locDialog.status === 'occupied'" class="dl-actions-col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="it in (locDialog.items || [])" :key="it.inventoryId">
                <td><span class="code-text">{{ it.itemCode || '—' }}</span></td>
                <td>{{ it.itemName || '—' }}</td>
                <td><span class="batch-text">{{ it.batchNo || '—' }}</span></td>
                <td class="dl-qty">{{ it.onhandQty || 0 }}</td>
                <td v-if="locDialog.status === 'occupied'">
                  <button type="button" class="dl-pick-btn" @click="openPick(locDialog, it)">下架</button>
                </td>
              </tr>
              <tr v-if="!(locDialog.items || []).length">
                <td :colspan="locDialog.status === 'occupied' ? 5 : 4" class="dl-empty-cell">暂无库存记录</td>
              </tr>
            </tbody>
          </table>
          <div v-if="locDialog.status === 'frozen'" class="dl-warn">该库位库存已被冻结，不可出入库</div>
        </template>
        <template v-if="locDialog.status === 'available' || locDialog.status === 'disabled' || locDialog.status === 'reserved'">
          <div class="dl-empty">该库位暂无存放物料</div>
        </template>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn primary" @click="locDialog = null; highlightedLoc = null">关闭</button>
      </div>
    </div>

    <div v-if="showPutawayDialog" class="overlay" @click="showPutawayDialog = false"></div>
    <div v-if="showPutawayDialog" class="dialog op-dialog">
      <div class="dialog-head">
        <span>上架 · {{ putawayLoc?.locationCode }}</span>
        <button class="dialog-close" @click="showPutawayDialog = false">×</button>
      </div>
      <div class="dialog-body">
        <div class="dl-row"><span class="dl-lbl">目标库位</span><span class="dl-val">{{ putawayLoc?.locationCode }}</span></div>
        <div class="dl-row"><span class="dl-lbl">物料编码</span>
          <input v-model="putawayForm.material_code" class="dl-input" placeholder="输入物料编码" @input="onMatCodeChange" />
        </div>
        <div class="dl-row"><span class="dl-lbl">物料名称</span><span class="dl-val">{{ putawayForm.material_name || '—' }}</span></div>
        <div class="dl-row"><span class="dl-lbl">批次号</span>
          <input v-model="putawayForm.batch_no" class="dl-input" placeholder="输入批次号" />
        </div>
        <div class="dl-row"><span class="dl-lbl">上架数量</span><input v-model.number="putawayForm.qty" type="number" class="dl-input" placeholder="请输入数量" min="1" /></div>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn" @click="showPutawayDialog = false">取消</button>
        <button class="tb-btn primary" @click="confirmPutaway">确认上架</button>
      </div>
    </div>

    <div v-if="showPickDialog" class="overlay" @click="showPickDialog = false"></div>
    <div v-if="showPickDialog" class="dialog op-dialog">
      <div class="dialog-head">
        <span>下架 · {{ pickLoc?.locationCode }}</span>
        <button class="dialog-close" @click="showPickDialog = false">×</button>
      </div>
      <div class="dialog-body">
        <div class="dl-row"><span class="dl-lbl">库位编码</span><span class="dl-val">{{ pickLoc?.locationCode }}</span></div>
        <div class="dl-row"><span class="dl-lbl">物料名称</span><span class="dl-val">{{ pickLoc?.item || '—' }}</span></div>
        <div class="dl-row"><span class="dl-lbl">批次号</span><span class="dl-val">{{ pickLoc?.batch || '—' }}</span></div>
        <div class="dl-row"><span class="dl-lbl">当前库存</span><span class="dl-val">{{ pickLoc?.stock || 0 }}</span></div>
        <div class="dl-row"><span class="dl-lbl">可用库存</span><span class="dl-val">{{ pickLoc?.availableQty ?? pickLoc?.stock ?? 0 }}</span></div>
        <div class="dl-row"><span class="dl-lbl">下架数量</span><input v-model.number="pickForm.qty" type="number" class="dl-input" placeholder="请输入数量" min="1" :max="pickLoc?.availableQty ?? pickLoc?.stock" /></div>
        <div class="dl-row"><span class="dl-lbl">下架原因</span>
          <select v-model="pickForm.reason" class="dl-input">
            <option value="">— 请选择原因 —</option>
            <option v-for="r in pickReasons" :key="r" :value="r">{{ r }}</option>
          </select>
        </div>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn" @click="showPickDialog = false">取消</button>
        <button class="tb-btn primary danger" @click="confirmPick">确认下架</button>
      </div>
    </div>

    <transition name="toast-fade">
      <div v-if="toastMessage" class="toast">{{ toastMessage }}</div>
    </transition>

    <section class="loc-section">
      <div class="loc-section-head">
        <h2>库位清单</h2>
        <div class="loc-toolbar">
          <input v-model="tableSearch" placeholder="搜索库位编码 / 物料名称 / 批次号" class="loc-search" />
        </div>
      </div>
      <div class="table-wrap">
        <table class="loc-table">
          <thead>
            <tr>
              <th class="lc-code">库位编码</th>
              <th class="lc-zone">所属库区</th>
              <th class="lc-status">状态</th>
              <th class="lc-item">物料编码</th>
              <th class="lc-item-name">物料名称</th>
              <th class="lc-batch">批次号</th>
              <th class="lc-qty">占用数量</th>
            </tr>
          </thead>
          <tbody ref="tableBodyRef">
            <tr
              v-for="row in pagedLocList"
              :key="row.rowKey"
              :class="{ 'loc-highlighted': highlightedLoc === row.locationCode }"
            >
              <td class="lc-code"><span class="code-text">{{ row.locationCode }}</span></td>
              <td class="lc-zone">{{ (zones || []).find(z => z.code === row.zoneCode)?.name || row.zone }}</td>
              <td class="lc-status">
                <span :class="['status-tag', statusMap[row.status]?.cls]">
                  <i class="status-dot"></i>
                  {{ statusMap[row.status]?.label }}
                </span>
              </td>
              <td class="lc-item">{{ row.itemCode || '—' }}</td>
              <td class="lc-item-name">{{ row.item || '—' }}</td>
              <td class="lc-batch"><span class="batch-text">{{ row.batch || '—' }}</span></td>
              <td class="lc-qty">{{ row.stock || 0 }}</td>
            </tr>
            <tr v-if="pagedLocList.length === 0">
              <td colspan="7" class="empty-cell">暂无库位数据 · 请先在后台维护仓库库位信息</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="table-pagination">
        <span class="page-info">共 {{ totalLocItems }} 条数据</span>
        <div class="page-size">
          <span>每页</span>
          <select v-model="pageSize" @change="changeSize(pageSize)">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
        </div>
        <div class="page-controls">
          <button :disabled="currentPage === 1" @click="changePage(currentPage - 1)">‹</button>
          <span class="page-num">{{ currentPage }} / {{ totalLocPages || 1 }}</span>
          <button :disabled="currentPage === totalLocPages || totalLocPages === 0" @click="changePage(currentPage + 1)">›</button>
        </div>
      </div>
    </section>
  </div>
</template>


<style scoped>
.map-page {
  width: 100%;
  max-width: 1600px;
  margin: 0 auto;
}

.map-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.map-header h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(22px, 2.8vw, 30px);
  font-weight: 600;
  color: #2a3a33;
}

.map-desc { margin: 6px 0 0; color: #7d8983; font-size: 14px; }

.map-header-right { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }

.map-search-bar { display: flex; align-items: center; gap: 6px; }

.search-input {
  border: 1px solid #e2eae5;
  border-radius: 8px;
  padding: 7px 12px;
  font-size: 13px;
  color: #33473f;
  outline: none;
  background: rgba(255,255,255,0.8);
  max-width: 300px;
}
.search-input::placeholder { color: #bccbc2; }

.search-clear { border: 0; background: transparent; color: #8b958f; cursor: pointer; font-size: 18px; padding: 2px 4px; line-height: 1; }

.map-legend {
  display: flex;
  gap: 12px;
  padding: 8px 14px;
  background: rgba(255,255,255,0.8);
  border: 1px solid #e2eae5;
  border-radius: 8px;
}

.export-btn {
  flex-shrink: 0;
  padding: 8px 18px;
  background: #3f6a55;
  border: 1px solid #3f6a55;
  color: #fff;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: background .12s;
  white-space: nowrap;
}
.export-btn:hover { background: #345544; }

.legend-item { display: flex; align-items: center; gap: 5px; font-size: 12px; color: #6c7d75; }

.dot { width: 10px; height: 10px; border-radius: 3px; }
.dot.occupied { background: #4b7a5f; }
.dot.available { background: #cddbd3; }
.dot.frozen { background: #b4553f; }
.dot.reserved { background: #d79a3a; }
.dot.disabled { background: #9caaa3; }

.map-tabs-row { margin-top: 6px; }

.map-tabs {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: #cddbd3 transparent;
  padding-bottom: 4px;
}
.map-tabs::-webkit-scrollbar { height: 4px; }
.map-tabs::-webkit-scrollbar-thumb { background: #cddbd3; border-radius: 2px; }
.map-tabs::-webkit-scrollbar-track { background: transparent; }

.zone-tab {
  flex: 0 0 150px;
  border: 1px solid #e2eae5;
  border-radius: 10px;
  padding: 10px 12px;
  background: rgba(255,255,255,0.7);
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
  display: grid;
  gap: 2px;
}
.zone-tab strong { font-size: 15px; color: #31433c; }
.zone-tab span { font-size: 12px; color: #7d8983; }
.zone-tab:hover { background: rgba(255,255,255,0.95); border-color: #cddbd3; }
.zone-tab.active { background: #fff; border-color: #8ba896; box-shadow: 0 2px 8px rgba(55,78,67,0.06); }
.zone-tab.active strong { color: #2a3a33; }

.map-strip {
  display: flex;
  gap: 4px;
  margin-top: 10px;
  background: rgba(255,255,255,0.8);
  border: 1px solid #e2eae5;
  border-radius: 10px;
  padding: 4px;
}
.strip-item { flex: 1; padding: 8px; border-radius: 6px; text-align: center; min-width: 0; cursor: pointer; transition: background .15s; }
.strip-item:hover { background: #f0f5f2; }
.strip-item.active { background: #e6f1ea; box-shadow: inset 0 0 0 1.5px #8ba896; }
.strip-num { display: block; font-size: 17px; font-weight: 700; color: #33473f; }
.strip-lbl { display: block; margin-top: 1px; color: #8b958f; font-size: 11px; }

.map-body {
  margin-top: 10px;
}

/* Zone block in ALL view */
.zone-block {
  margin-bottom: 16px;
}

.zone-block-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 8px;
  padding: 10px 14px;
  background: rgba(255,255,255,0.85);
  border: 1px solid #e2eae5;
  border-radius: 10px 10px 0 0;
}

.zone-block-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #2a3a33;
}

.zone-block-meta {
  font-size: 12px;
  color: #8b958f;
}

.zone-block .map-floor {
  border-radius: 0 0 10px 10px;
}

.map-floor {
  background: rgba(255,255,255,0.85);
  border: 1px solid #e2eae5;
  border-radius: 12px;
  padding: 14px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.floor-header { display: grid; gap: 6px; margin-bottom: 8px; padding: 0 4px; }
.floor-corner { height: 24px; }
.floor-col-label { text-align: center; font-size: 11px; color: #8b958f; font-weight: 600; display: flex; align-items: flex-end; justify-content: center; padding-bottom: 4px; }

.floor-row { display: grid; gap: 6px; padding: 0 4px; margin-bottom: 6px; }
.floor-row:last-child { margin-bottom: 0; }
.floor-row-label { display: flex; align-items: center; font-size: 12px; color: #6c7d75; font-weight: 600; padding-left: 4px; }

.floor-cell {
  width: 120px;
  min-width: 120px;
  height: 110px;
  border-radius: 8px;
  padding: 8px 8px 10px;
  cursor: pointer;
  transition: all 0.12s;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  border-left: 4px solid #8ba896;
  background: #f8fbf9;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
  box-sizing: border-box;
  overflow: hidden;
}
.floor-cell:hover { box-shadow: 0 2px 10px rgba(55,78,67,0.1); transform: translateY(-1px); }
.floor-cell.active { box-shadow: 0 0 0 2px #587766; }

.floor-cell.cell-occupied { border-left-color: #4b7a5f; background: #f8fbf9; }
.floor-cell.cell-available { border-left-color: #8ba896; background: #f8fbf9; }
.floor-cell.cell-frozen { border-left-color: #b4553f; background: #f8fbf9; }
.floor-cell.cell-reserved { border-left-color: #d79a3a; background: #f8fbf9; }
.floor-cell.cell-disabled { border-left-color: #9caaa3; background: #f8fbf9; }
.floor-cell.muted { opacity: .35; pointer-events: none; }

.cell-code { font-size: 12px; font-weight: 600; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; color: #2a3a33; line-height: 1.3; }
.cell-type { font-size: 10px; color: #8b958f; line-height: 1.2; }

.cell-status { display: flex; align-items: center; gap: 4px; font-size: 10px; font-weight: 500; line-height: 1.2; margin: 2px 0; }
.cell-status .stat-dot { width: 6px; height: 6px; border-radius: 50%; display: inline-block; flex-shrink: 0; }
.cell-status .stat-dot.occupied { background: #4b7a5f; }
.cell-status .stat-dot.available { background: #8ba896; }
.cell-status .stat-dot.frozen { background: #b4553f; }
.cell-status .stat-dot.reserved { background: #d79a3a; }
.cell-status .stat-dot.disabled { background: #9caaa3; }

.cell-item-row { display: flex; align-items: center; justify-content: space-between; gap: 4px; margin-top: 2px; padding: 2px 6px; background: #ecf3ef; border-radius: 4px; }
.cell-items-stack { display: flex; flex-direction: column; gap: 2px; margin-top: 2px; }
.cell-more-btn {
  margin-top: 2px;
  padding: 2px 6px;
  border: none;
  border-radius: 4px;
  background: #dfece4;
  color: #3f6a55;
  font-size: 10px;
  cursor: pointer;
  text-align: left;
}
.cell-more-btn:hover { background: #d0e4d9; }
.cell-multi-hint { font-size: 10px; color: #587766; padding: 0 4px; }
.dl-subtitle-meta { margin-left: 8px; font-size: 12px; font-weight: 400; color: #8b958f; }
.dl-actions-col { width: 72px; }
.dl-pick-btn {
  padding: 2px 8px;
  border: 1px solid #c5d9cc;
  border-radius: 4px;
  background: #fff;
  color: #3f6a55;
  font-size: 12px;
  cursor: pointer;
}
.dl-pick-btn:hover { background: #f0f7f3; }
.dl-empty-cell { text-align: center; color: #9caaa3; padding: 12px; }
.lc-item-name { min-width: 120px; color: #3f554b; }
.cell-item-name { font-size: 10px; color: #3f554b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; min-width: 0; }
.cell-item-qty { font-size: 11px; font-weight: 600; color: #3f6a55; flex-shrink: 0; }
.cell-item-qty::after { content: '件'; font-weight: 400; font-size: 9px; color: #8b958f; margin-left: 1px; }

.cell-action {
  margin-top: 3px;
  border: 0;
  border-radius: 4px;
  padding: 3px 0;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: background .12s;
  width: 100%;
  text-align: center;
}
.putaway-btn { background: #3E8E41; color: #fff; }
.putaway-btn:hover { background: #357a38; }
.pick-btn { background: #3E8E41; color: #fff; }
.pick-btn:hover { background: #357a38; }

.info-badge { border-radius: 999px; padding: 2px 10px; font-size: 12px; font-weight: 600; display: inline-block; }
.info-badge.occupied { background: #e2f0e7; color: #4b7a5f; }
.info-badge.available { background: #eef4f0; color: #5f7268; }
.info-badge.frozen { background: #f6e0d9; color: #b4553f; }
.info-badge.reserved { background: #f5edd7; color: #a97e2c; }
.info-badge.disabled { background: #f0f5f2; color: #8b958f; }

/* Overlay & Dialog */
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.3);
  z-index: 200;
}

.dialog {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 300;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.2);
  width: 520px;
  max-width: 90vw;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
}

.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #ecf1ee;
  font-weight: 600;
  font-size: 15px;
  color: #2a3a33;
}

.dialog-close {
  border: 0;
  background: transparent;
  font-size: 18px;
  color: #8b958f;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.dialog-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.dialog-foot {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding: 14px 20px;
  border-top: 1px solid #ecf1ee;
}

.tb-btn {
  border: 1px solid #cddbd3;
  border-radius: 8px;
  padding: 7px 16px;
  background: #fff;
  color: #567566;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.12s;
  white-space: nowrap;
}

.tb-btn:hover { background: #eef4f0; }

.tb-btn.primary {
  background: #587766;
  border-color: #587766;
  color: #fff;
}

.tb-btn.primary:hover { background: #4a6a59; }

.dl-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
}

.dl-lbl {
  color: #7d8983;
  min-width: 72px;
  flex-shrink: 0;
}

.dl-val {
  color: #2c3b34;
  font-weight: 500;
}

.dl-divider {
  height: 1px;
  background: #e2eae5;
  margin: 14px 0;
}

.dl-subtitle {
  font-weight: 600;
  font-size: 14px;
  color: #2c3b34;
  margin-bottom: 10px;
}

.dl-table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid #e2eae5;
  border-radius: 8px;
  overflow: hidden;
}

.dl-table th {
  padding: 8px 12px;
  background: #f0f5f2;
  font-size: 12px;
  font-weight: 600;
  color: #5f7268;
  text-align: left;
  border-bottom: 1px solid #e2eae5;
}

.dl-table td {
  padding: 8px 12px;
  font-size: 13px;
  color: #3f554b;
  border-bottom: 1px solid #ecf1ee;
}

.dl-qty {
  font-weight: 600;
  text-align: right;
}

.dl-warn {
  margin-top: 12px;
  padding: 8px 12px;
  background: #fdf8ee;
  border-radius: 6px;
  font-size: 12px;
  color: #a97e2c;
}

.dl-empty {
  padding: 24px 0;
  text-align: center;
  color: #9caaa3;
  font-size: 14px;
}

/* Table highlight */
.loc-highlighted {
  background: #E8F0F8 !important;
}

/* Location List Table */
.loc-section {
  margin-top: 20px;
}

.loc-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.loc-section-head h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #2a3a33;
}

.loc-toolbar { display: flex; gap: 8px; }

.loc-search {
  border: 1px solid #dfe6e1;
  border-radius: 8px;
  padding: 7px 14px;
  font-size: 13px;
  color: #33473f;
  outline: none;
  background: #fff;
  width: 260px;
}

.loc-search::placeholder { color: #bccbc2; }

.table-wrap {
  border: 1px solid #e2eae5;
  border-radius: 10px;
  overflow: hidden;
  background: rgba(255,255,255,0.9);
}

.loc-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.loc-table thead { background: #f0f5f2; }

.loc-table th {
  padding: 11px 10px;
  font-size: 12px;
  font-weight: 600;
  color: #5f7268;
  text-align: left;
  white-space: nowrap;
  border-bottom: 1px solid #e2eae5;
}

.loc-table td {
  padding: 11px 10px;
  font-size: 13px;
  color: #3f554b;
  border-bottom: 1px solid #ecf1ee;
}

.loc-table tbody tr:hover { background: #f6f9f7; }

.lc-code { width: 140px; }
.lc-zone { width: 140px; }
.lc-status { width: 80px; }
.lc-item { width: 140px; }
.lc-batch { width: 120px; }
.lc-qty { width: 80px; text-align: right; }

.code-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #31433c;
  font-weight: 600;
}

.batch-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #6c7d75;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 500;
}

.status-dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; }

.status-occupied { background: #e2f0e7; color: #4b7a5f; }
.status-occupied .status-dot { background: #4b7a5f; }

.status-available { background: #eef4f0; color: #5f7268; }
.status-available .status-dot { background: #5f7268; }

.status-frozen { background: #f6e0d9; color: #b4553f; }
.status-frozen .status-dot { background: #b4553f; }

.status-reserved { background: #f5edd7; color: #a97e2c; }
.status-reserved .status-dot { background: #a97e2c; }

.status-disabled { background: #f0f5f2; color: #8b958f; }
.status-disabled .status-dot { background: #8b958f; }

.empty-cell {
  text-align: center;
  padding: 30px 0 !important;
  color: #9caaa3;
  font-size: 14px;
}

.loading-indicator, .empty-state, .empty-zones {
  padding: 40px 0;
  text-align: center;
  color: #9caaa3;
  font-size: 14px;
}

.empty-zones {
  flex: 1;
  padding: 20px;
  border: 1px dashed #e2eae5;
  border-radius: 8px;
}

.table-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 10px;
  padding: 10px 16px;
  background: rgba(255,255,255,0.85);
  border: 1px solid #e2eae5;
  border-radius: 10px;
}

.page-info { color: #8b958f; font-size: 13px; margin-right: auto; }

.page-size { display: flex; align-items: center; gap: 6px; color: #8b958f; font-size: 13px; }

.page-size select {
  border: 1px solid #dfe6e1;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  color: #46544f;
  background: #fff;
  outline: none;
}

.page-controls { display: flex; align-items: center; gap: 8px; }

.page-controls button {
  border: 1px solid #cddbd3;
  border-radius: 6px;
  padding: 4px 10px;
  background: #fff;
  color: #567566;
  font-size: 16px;
  cursor: pointer;
  line-height: 1;
}

.page-controls button:disabled { opacity: 0.4; cursor: not-allowed; }
.page-controls button:hover:not(:disabled) { background: #eef4f0; }

.page-num { font-size: 13px; color: #46544f; min-width: 60px; text-align: center; }

.op-dialog { width: 480px; }

.dl-input {
  flex: 1;
  border: 1px solid #dfe6e1;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 13px;
  color: #2c3b34;
  background: #fff;
  outline: none;
  min-width: 0;
}
.dl-input::placeholder { color: #bccbc2; }
.dl-input:focus { border-color: #8ba896; }

.dl-row .dl-input.select { appearance: auto; }

.tb-btn.primary.danger { background: #c0392b; border-color: #c0392b; }
.tb-btn.primary.danger:hover { background: #a93226; }

/* Anomaly locations */
.anomaly-section {
  margin-top: 16px;
  background: rgba(255,255,255,0.85);
  border: 1px solid #f0d0c8;
  border-radius: 10px;
  padding: 14px;
}

.anomaly-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.anomaly-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #b4553f;
}

.anomaly-badge {
  background: #f6e0d9;
  color: #b4553f;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
}

.anomaly-desc {
  margin: 6px 0 10px;
  font-size: 12px;
  color: #8b958f;
}

.anomaly-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.anomaly-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #fdf5f1;
  border: 1px solid #f0d0c8;
  border-radius: 6px;
  font-size: 12px;
}

.anomaly-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-weight: 600;
  color: #b4553f;
}

.anomaly-item {
  color: #6c7d75;
}

.anomaly-qty {
  font-weight: 600;
  color: #b4553f;
}

/* Toast */
.toast {
  position: fixed;
  bottom: 40px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 999;
  padding: 10px 24px;
  background: #2a3a33;
  color: #fff;
  border-radius: 8px;
  font-size: 13px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.25);
  pointer-events: none;
  white-space: nowrap;
}

.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: opacity 0.25s, transform 0.25s;
}
.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(10px);
}

.map-floor::-webkit-scrollbar { height: 6px; }
.map-floor::-webkit-scrollbar-track { background: #f0f5f2; border-radius: 3px; }
.map-floor::-webkit-scrollbar-thumb { background: #cddbd3; border-radius: 3px; }
.map-floor::-webkit-scrollbar-thumb:hover { background: #b0bfb5; }
</style>