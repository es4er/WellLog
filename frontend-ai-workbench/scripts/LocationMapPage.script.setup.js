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
