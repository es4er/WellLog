<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import * as XLSX from 'xlsx'
import { useInventory } from '../composables/useInventory'
import { getAuthToken } from '../api'
import { menuToPath } from '../router/menuRoutes'

const router = useRouter()

const {
  doLogin, fetchInventoryListWithDetail, freezeInventory, unfreezeInventory,
  getTransactions, loading, createInventory, importInventory,
  fetchWarehouseList, fetchZoneList, fetchLocationList,
} = useInventory()

const searchQuery = ref('')
const pageSize = ref(20)
const currentPage = ref(1)
const selectedItems = ref([])

const showAdvancedFilter = ref(false)
const advancedFilter = reactive({
  itemCode: '', itemName: '', batchNo: '', locationCode: '', statuses: [],
})

const showFreezeDialog = ref(false)
const showUnfreezeDialog = ref(false)
const showMoveDialog = ref(false)
const showBatchAdjustDialog = ref(false)
const showTransactionDialog = ref(false)
const showExportConfirm = ref(false)
const exportSelectedCount = ref(0)
const showDeleteConfirm = ref(false)
const deleteTarget = ref(null)
const showCreateDialog = ref(false)
const showImportDialog = ref(false)

const freezeForm = reactive({ quantity: null, reason: '' })
const unfreezeForm = reactive({ quantity: 1, reason: '' })
const moveForm = reactive({ targetLocation: '', quantity: null })
const batchAdjustForm = reactive({ targetBatchNo: '', quantity: null, reason: '', remark: '' })
const createForm = reactive({ warehouseId: null, locationId: null, itemCode: '', batchNo: '', onhandQty: null })

const importFile = ref(null)
const importFileName = ref('')

const warehouseOptions = ref([])
const locationOptions = ref([])

const actionItem = ref(null)

const toastMessage = ref('')
let toastTimer = null

function showToast(msg) {
  toastMessage.value = msg
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toastMessage.value = '' }, 2500)
}

const inventoryData = ref([])



function mapApiData(data) {
  return (data || []).map(item => ({
    item_code: item.itemCode || item.item_code || '',
    item_name: item.itemName || item.item_name || '',
    batch_no: item.batchNo || item.batch_no || '',
    location_code: item.locationCode || item.location_code || '',
    onhand_qty: item.onhandQty ?? item.onhand_qty ?? 0,
    available_qty: item.availableQty ?? item.available_qty ?? 0,
    frozen_qty: item.frozenQty ?? item.frozen_qty ?? 0,
    inventory_status: item.inventoryStatus || item.inventory_status || 'AVAILABLE',
    remark: item.remark || '',
    inventoryId: item.inventoryId || item.inventory_id,
    itemId: item.itemId || item.item_id,
    batchId: item.batchId || item.batch_id,
  }))
}

async function fetchFromApi() {
  if (!getAuthToken()) {
    try { await doLogin('admin', 'admin123') } catch (e) { /* ignore */ }
  }
  try {
    const data = await fetchInventoryListWithDetail()
    inventoryData.value = mapApiData(data)
  } catch (e) {
    inventoryData.value = []
  }
}

onMounted(fetchFromApi)

const statusOptions = [
  { value: 'AVAILABLE', label: '可用' },
  { value: 'FROZEN', label: '冻结' },
  { value: 'PENDING_CHECK', label: '待检' },
  { value: 'DISABLED', label: '停用' },
]

const statusMap = {
  AVAILABLE: { label: '可用', cls: 'status-available' },
  FROZEN: { label: '冻结', cls: 'status-frozen' },
  PENDING_CHECK: { label: '待检', cls: 'status-pending' },
  DISABLED: { label: '停用', cls: 'status-disabled' },
}

const filteredData = computed(() => {
  let list = inventoryData.value
  const q = searchQuery.value.trim().toLowerCase()
  if (q) {
    list = list.filter(i => i.item_code.toLowerCase().includes(q) || i.item_name.toLowerCase().includes(q) || i.batch_no.toLowerCase().includes(q))
  }
  const af = advancedFilter
  if (af.itemCode) list = list.filter(i => i.item_code.toLowerCase().includes(af.itemCode.toLowerCase()))
  if (af.itemName) list = list.filter(i => i.item_name.toLowerCase().includes(af.itemName.toLowerCase()))
  if (af.batchNo) list = list.filter(i => i.batch_no.toLowerCase().includes(af.batchNo.toLowerCase()))
  if (af.locationCode) list = list.filter(i => i.location_code.toLowerCase().includes(af.locationCode.toLowerCase()))
  if (af.statuses.length) list = list.filter(i => af.statuses.includes(i.inventory_status))
  return list
})

const totalItems = computed(() => filteredData.value.length)
const totalPages = computed(() => Math.ceil(totalItems.value / pageSize.value))
const pagedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredData.value.slice(start, start + pageSize.value)
})

const stats = computed(() => {
  const d = inventoryData.value
  const uniqueItems = new Set(d.map(i => i.item_code))
  return {
    total: uniqueItems.size,
    available: d.filter(i => i.inventory_status === 'AVAILABLE').reduce((s, i) => s + i.available_qty, 0),
    frozen: d.filter(i => i.inventory_status === 'FROZEN').reduce((s, i) => s + i.frozen_qty, 0),
    pending: d.filter(i => i.inventory_status === 'PENDING_CHECK').length,
    disabled: d.filter(i => i.inventory_status === 'DISABLED').length,
  }
})

const moreMenuOpen = ref(null)

function toggleMore(index) {
  moreMenuOpen.value = moreMenuOpen.value === index ? null : index
}

function isItemSelected(item) {
  return selectedItems.value.some(
    s => s.item_code === item.item_code
      && s.batch_no === item.batch_no
      && s.location_code === item.location_code
  )
}

function toggleSelect(item) {
  const idx = selectedItems.value.findIndex(
    s => s.item_code === item.item_code
      && s.batch_no === item.batch_no
      && s.location_code === item.location_code
  )
  if (idx >= 0) selectedItems.value.splice(idx, 1)
  else selectedItems.value.push(item)
}

function selectAll() {
  if (selectedItems.value.length === pagedData.value.length) {
    selectedItems.value = []
  } else {
    selectedItems.value = pagedData.value.map(i => ({ ...i }))
  }
}

const canBatchAdjust = computed(() => selectedItems.value.length === 1)

function resolveSelectedInventory() {
  if (selectedItems.value.length !== 1) return null
  const sel = selectedItems.value[0]
  return inventoryData.value.find(
    i => i.item_code === sel.item_code
      && i.batch_no === sel.batch_no
      && i.location_code === (sel.location_code || i.location_code)
  ) || sel
}

function changePage(p) {
  if (p < 1 || p > totalPages.value) return
  currentPage.value = p
}

function changeSize(size) {
  pageSize.value = size
  currentPage.value = 1
}

function resetAdvancedFilter() {
  advancedFilter.itemCode = ''
  advancedFilter.itemName = ''
  advancedFilter.batchNo = ''
  advancedFilter.locationCode = ''
  advancedFilter.statuses = []
  currentPage.value = 1
}

function applyAdvancedFilter() {
  currentPage.value = 1
  showAdvancedFilter.value = false
  showToast('查询完成')
}



function openFreezeDialog(item) {
  actionItem.value = item
  freezeForm.quantity = null
  freezeForm.reason = ''
  showFreezeDialog.value = true
}

async function confirmFreeze() {
  if (!freezeForm.quantity || !freezeForm.reason) {
    showToast('请填写完整信息')
    return
  }
  if (actionItem.value) {
    const id = actionItem.value.inventoryId
    if (id) {
      try {
        await freezeInventory(id, freezeForm.quantity, freezeForm.reason, 1)
      } catch (e) {
        showToast('冻结失败: ' + (e.message || e))
        showFreezeDialog.value = false
        actionItem.value = null
        return
      }
    }
    actionItem.value.inventory_status = 'FROZEN'
    actionItem.value.frozen_qty = (actionItem.value.frozen_qty || 0) + freezeForm.quantity
    actionItem.value.available_qty = Math.max(0, (actionItem.value.onhand_qty || 0) - (actionItem.value.frozen_qty || 0))
    actionItem.value.remark = freezeForm.reason
  }
  showFreezeDialog.value = false
  actionItem.value = null
  showToast('冻结成功')
}

function openUnfreezeDialog(item) {
  actionItem.value = item
  unfreezeForm.quantity = 1
  unfreezeForm.reason = ''
  showUnfreezeDialog.value = true
}

async function confirmUnfreeze() {
  if (actionItem.value) {
    const id = actionItem.value.inventoryId
    const qty = unfreezeForm.quantity
    if (id && qty > 0) {
      try {
        await unfreezeInventory(id, qty, unfreezeForm.reason || '解冻', 1)
      } catch (e) {
        showToast('解冻失败: ' + (e.message || e))
        showUnfreezeDialog.value = false
        actionItem.value = null
        return
      }
    }
    actionItem.value.inventory_status = 'AVAILABLE'
    actionItem.value.available_qty = (actionItem.value.available_qty || 0) + qty
    actionItem.value.frozen_qty = Math.max(0, (actionItem.value.frozen_qty || 0) - qty)
  }
  showUnfreezeDialog.value = false
  actionItem.value = null
  showToast('解冻成功')
}

function openMoveDialog(item) {
  actionItem.value = item
  moveForm.targetLocation = ''
  moveForm.quantity = null
  showMoveDialog.value = true
}

function openBatchAdjustDialog(item) {
  if (!item) {
    showToast('请先勾选一条库存记录')
    return
  }
  if ((item.available_qty || 0) <= 0) {
    showToast('可用数量为 0，无法进行批次调整')
    return
  }
  actionItem.value = item
  batchAdjustForm.targetBatchNo = ''
  batchAdjustForm.quantity = null
  batchAdjustForm.reason = ''
  batchAdjustForm.remark = ''
  showBatchAdjustDialog.value = true
  moreMenuOpen.value = null
}

function openBatchAdjustFromToolbar() {
  const item = resolveSelectedInventory()
  if (!item) {
    showToast('请勾选一条库存后再进行批次调整')
    return
  }
  openBatchAdjustDialog(item)
}

function confirmBatchAdjust() {
  const src = actionItem.value
  if (!src) return
  const targetBatch = (batchAdjustForm.targetBatchNo || '').trim()
  const qty = Number(batchAdjustForm.quantity)
  const reason = (batchAdjustForm.reason || '').trim()

  if (!targetBatch) {
    showToast('请填写目标批次号')
    return
  }
  if (targetBatch === src.batch_no) {
    showToast('目标批次号不能与原批次号相同')
    return
  }
  if (!qty || qty <= 0) {
    showToast('调整数量必须大于 0')
    return
  }
  if (qty > src.available_qty) {
    showToast('调整数量不能超过可用数量')
    return
  }
  if (!reason) {
    showToast('请填写调整原因')
    return
  }

  src.onhand_qty -= qty
  src.available_qty -= qty

  const existing = inventoryData.value.find(
    i => i.item_code === src.item_code
      && i.batch_no === targetBatch
      && i.location_code === src.location_code
  )
  if (existing) {
    existing.onhand_qty += qty
    existing.available_qty += qty
  } else {
    inventoryData.value.unshift({
      item_code: src.item_code,
      item_name: src.item_name,
      batch_no: targetBatch,
      location_code: src.location_code,
      onhand_qty: qty,
      available_qty: qty,
      frozen_qty: 0,
      inventory_status: 'AVAILABLE',
      remark: batchAdjustForm.remark || reason,
      itemId: src.itemId,
    })
  }

  if (src.onhand_qty === 0 && src.frozen_qty === 0) {
    src.inventory_status = 'DISABLED'
  }

  showBatchAdjustDialog.value = false
  actionItem.value = null
  showToast('批次调整成功')
}

function openCreateReplenish(item) {
  moreMenuOpen.value = null
  router.push({
    path: menuToPath('补货管理'),
    query: {
      openCreate: '1',
      itemCode: item.item_code || '',
      batchNo: item.batch_no || '',
      locationCode: item.location_code || '',
      fromLocation: item.location_code || '',
      availableQty: String(item.available_qty ?? 0),
      suggestQty: String(item.available_qty ?? 0),
      reason: '库存控制创建补货',
      from: 'inventory-control',
    },
  })
}

function confirmMove() {
  if (!moveForm.targetLocation || !moveForm.quantity) {
    showToast('请填写完整信息')
    return
  }
  const qty = parseInt(moveForm.quantity)
  if (!qty || qty <= 0) { showToast('数量必须大于0'); return }
  const src = actionItem.value
  if (!src) return
  if (qty > src.available_qty) { showToast('移库数量不能超过可用数量'); return }

  // Reduce source
  src.onhand_qty -= qty
  src.available_qty -= qty

  // Find or create target record
  const existing = inventoryData.value.find(
    i => i.item_code === src.item_code && i.batch_no === src.batch_no && i.location_code === moveForm.targetLocation
  )
  if (existing) {
    existing.onhand_qty += qty
    existing.available_qty += qty
  } else {
    inventoryData.value.unshift({
      item_code: src.item_code,
      item_name: src.item_name,
      batch_no: src.batch_no,
      location_code: moveForm.targetLocation,
      onhand_qty: qty,
      available_qty: qty,
      frozen_qty: 0,
      inventory_status: 'AVAILABLE',
      remark: '',
    })
  }

  // If source becomes empty, mark as DISABLED
  if (src.onhand_qty === 0) {
    src.inventory_status = 'DISABLED'
  }

  currentPage.value = 1
  showMoveDialog.value = false
  actionItem.value = null
  moveForm.targetLocation = ''
  moveForm.quantity = null
  showToast('移库成功！')
}

const transactionItems = ref([])
async function openTransactionDialog(item) {
  actionItem.value = item
  transactionItems.value = []
  try {
    const tx = await getTransactions(item.itemId, item.batchId)
    transactionItems.value = tx || []
  } catch (e) {
    transactionItems.value = []
  }
  showTransactionDialog.value = true
}

function canDelete(status) {
  return status === 'DISABLED' || status === 'FROZEN'
}

function openDeleteDialog(item) {
  if (!canDelete(item.inventory_status)) {
    const tips = { AVAILABLE: '可用库存不可删除，请先冻结或停用', PENDING_CHECK: '待检库存不可删除，请先完成质检' }
    showToast(tips[item.inventory_status] || '该状态不可删除')
    return
  }
  deleteTarget.value = item
  showDeleteConfirm.value = true
}

function confirmDelete() {
  if (deleteTarget.value) {
    const idx = inventoryData.value.findIndex(
      i => i.item_code === deleteTarget.value.item_code && i.batch_no === deleteTarget.value.batch_no
    )
    if (idx >= 0) inventoryData.value.splice(idx, 1)
    selectedItems.value = selectedItems.value.filter(
      s => s.item_code !== deleteTarget.value.item_code || s.batch_no !== deleteTarget.value.batch_no
    )
  }
  showDeleteConfirm.value = false
  deleteTarget.value = null
  showToast('删除成功')
}

function getAvailableQty(row) {
  return Number(row.availableQty ?? row.available_qty ?? 0)
}

function getFrozenQty(row) {
  const directValue = row.frozenQty ?? row.freezeQty ?? row.frozen_qty
  if (directValue !== null && directValue !== undefined) {
    return Number(directValue)
  }
  const total = Number(row.onhandQty ?? row.totalQty ?? row.onhand_qty ?? 0)
  return Math.max(0, total - getAvailableQty(row))
}

function handleInventoryCommand(command, row) {
  switch (command) {
    case 'freeze':
      openFreezeDialog(row)
      break
    case 'unfreeze':
      openUnfreezeDialog(row)
      break
    case 'move':
      openMoveDialog(row)
      break
    case 'batchAdjust':
      openBatchAdjustDialog(row)
      break
    case 'createReplenish':
      openCreateReplenish(row)
      break
    case 'transaction':
      openTransactionDialog(row)
      break
    case 'delete':
      openDeleteDialog(row)
      break
  }
}

function formatDate() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}${m}${dd}`
}

function doExport(rows) {
  const header = [['物料编码', '物料名称', '批次号', '库位编码', '总库存', '可用数量', '冻结数量', '状态']]
  const data = rows.map(i => [
    i.item_code, i.item_name, i.batch_no, i.location_code,
    i.onhand_qty, i.available_qty, i.frozen_qty,
    statusMap[i.inventory_status]?.label || i.inventory_status,
  ])
  const ws = XLSX.utils.aoa_to_sheet([...header, ...data])
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '库存余额')
  XLSX.writeFile(wb, `库存余额_${formatDate()}.xlsx`)
  showToast('导出成功')
}

function handleExport() {
  const selected = inventoryData.value.filter(item =>
    selectedItems.value.some(s => s.item_code === item.item_code && s.batch_no === item.batch_no)
  )
  if (selected.length === 0 || selected.length === filteredData.value.length) {
    showToast(`将导出全部 ${filteredData.value.length} 条数据`)
    setTimeout(() => doExport(filteredData.value), 300)
  } else {
    exportSelectedCount.value = selected.length
    showExportConfirm.value = true
  }
}

function confirmExportSelected() {
  const selected = inventoryData.value.filter(item =>
    selectedItems.value.some(s => s.item_code === item.item_code && s.batch_no === item.batch_no)
  )
  showExportConfirm.value = false
  doExport(selected.length ? selected : filteredData.value)
}

async function loadWarehouseOptions() {
  try {
    const list = await fetchWarehouseList()
    warehouseOptions.value = (list || []).map(w => ({ id: w.warehouseId, name: w.warehouseName || w.warehouseCode }))
  } catch (e) { /* ignore */ }
}

async function onWarehouseChange() {
  createForm.locationId = null
  locationOptions.value = []
  if (!createForm.warehouseId) return
  try {
    const zones = await fetchZoneList(createForm.warehouseId)
    const allLocs = []
    for (const z of (zones || [])) {
      const locs = await fetchLocationList(z.zoneId)
      for (const l of (locs || [])) {
        allLocs.push({ id: l.locationId, code: l.locationCode, name: l.locationName || l.locationCode })
      }
    }
    locationOptions.value = allLocs
  } catch (e) { /* ignore */ }
}

function openCreateDialog() {
  createForm.warehouseId = null
  createForm.locationId = null
  createForm.itemCode = ''
  createForm.batchNo = ''
  createForm.onhandQty = null
  locationOptions.value = []
  showCreateDialog.value = true
  loadWarehouseOptions()
}

async function confirmCreate() {
  if (!createForm.warehouseId || !createForm.locationId || !createForm.itemCode || !createForm.batchNo || !createForm.onhandQty) {
    showToast('请填写所有必填项')
    return
  }
  try {
    await createInventory({
      warehouseId: createForm.warehouseId,
      locationId: createForm.locationId,
      itemCode: createForm.itemCode,
      batchNo: createForm.batchNo,
      onhandQty: createForm.onhandQty,
      operatedBy: 1,
    })
    showCreateDialog.value = false
    showToast('新增库存成功')
    await fetchFromApi()
  } catch (e) {
    showToast('新增失败: ' + (e.message || e))
  }
}

function openImportDialog() {
  importFile.value = null
  importFileName.value = ''
  showImportDialog.value = true
}

function handleImportFileChange(e) {
  const file = e.target.files[0]
  if (file) {
    importFile.value = file
    importFileName.value = file.name
  }
}

async function confirmImport() {
  if (!importFile.value) {
    showToast('请先选择文件')
    return
  }
  const reader = new FileReader()
  reader.onload = async (e) => {
    try {
      const data = new Uint8Array(e.target.result)
      const workbook = XLSX.read(data, { type: 'array' })
      const sheet = workbook.Sheets[workbook.SheetNames[0]]
      const rows = XLSX.utils.sheet_to_json(sheet, { header: 1 })
      if (rows.length < 2) {
        showToast('导入失败，模板为空或格式错误')
        return
      }
      const header = rows[0].map(h => (h || '').toString().trim())
      const colMap = {}
      header.forEach((h, i) => {
        const lower = h.toLowerCase()
        if (lower.includes('仓库') || lower.includes('warehouse')) colMap.warehouseId = i
        else if (lower.includes('库位') || lower.includes('location')) colMap.locationId = i
        else if (lower.includes('物料') || lower.includes('item')) colMap.itemCode = i
        else if (lower.includes('批次') || lower.includes('batch')) colMap.batchNo = i
        else if (lower.includes('数量') || lower.includes('qty') || lower.includes('quantity')) colMap.onhandQty = i
      })
      if (colMap.itemCode === undefined) {
        showToast('导入失败，未识别到物料编码列')
        return
      }
      const importRows = []
      for (let i = 1; i < rows.length; i++) {
        const row = rows[i]
        if (!row || !row[colMap.itemCode]) continue
        importRows.push({
          warehouseId: colMap.warehouseId !== undefined ? Number(row[colMap.warehouseId]) || null : null,
          locationId: colMap.locationId !== undefined ? Number(row[colMap.locationId]) || null : null,
          itemCode: (row[colMap.itemCode] || '').toString().trim(),
          batchNo: (colMap.batchNo !== undefined ? (row[colMap.batchNo] || '') : '').toString().trim(),
          onhandQty: colMap.onhandQty !== undefined ? Number(row[colMap.onhandQty]) || 0 : 0,
          operatedBy: 1,
        })
      }
      if (!importRows.length) {
        showToast('导入失败，未读取到有效数据')
        return
      }
      const count = await importInventory(importRows)
      showImportDialog.value = false
      showToast(`导入成功！共导入 ${count} 条数据`)
      await fetchFromApi()
    } catch (err) {
      showToast('导入失败: ' + (err.message || err))
    }
  }
  reader.readAsArrayBuffer(importFile.value)
}
</script>

<template>
  <div class="inv-page">
    <header class="inv-header">
      <div>
        <h1>库存控制</h1>
      </div>
      <div class="inv-strip">
        <div class="strip-item"><strong class="num">{{ stats.total }}</strong><span class="lbl">物料种数</span></div>
        <div class="strip-item"><strong class="num green">{{ stats.available }}</strong><span class="lbl">可用</span></div>
        <div class="strip-item"><strong class="num orange">{{ stats.frozen }}</strong><span class="lbl">冻结</span></div>
        <div class="strip-item"><strong class="num blue">{{ stats.pending }}</strong><span class="lbl">待检</span></div>
        <div class="strip-item"><strong class="num gray">{{ stats.disabled }}</strong><span class="lbl">停用</span></div>
      </div>
    </header>

    <div class="inv-toolbar">
      <div class="toolbar-left">
        <button class="tb-btn primary" @click="openCreateDialog">新增</button>
        <button class="tb-btn primary" @click="openImportDialog">导入</button>
        <button class="tb-btn primary" @click="handleExport">导出</button>
        <button class="tb-btn primary" @click="showAdvancedFilter = !showAdvancedFilter">高级查询</button>
        <button
          class="tb-btn primary"
          :disabled="!canBatchAdjust"
          :title="canBatchAdjust ? '对勾选库存进行批次调整' : '请先勾选一条库存'"
          @click="openBatchAdjustFromToolbar"
        >批次调整</button>
      </div>
      <div class="toolbar-right">
        <input v-model="searchQuery" placeholder="搜索物料编码 / 名称 / 批次号…" class="tb-search" />
      </div>
    </div>

    <transition name="fade">
      <div v-if="showAdvancedFilter" class="adv-filter">
        <div class="adv-filter-row">
          <div class="adv-field">
            <label>物料编码</label>
            <input v-model="advancedFilter.itemCode" placeholder="" class="adv-input" />
          </div>
          <div class="adv-field">
            <label>物料名称</label>
            <input v-model="advancedFilter.itemName" placeholder="" class="adv-input" />
          </div>
          <div class="adv-field">
            <label>批次号</label>
            <input v-model="advancedFilter.batchNo" placeholder="" class="adv-input" />
          </div>
          <div class="adv-field">
            <label>库位编码</label>
            <input v-model="advancedFilter.locationCode" placeholder="" class="adv-input" />
          </div>
          <div class="adv-field">
            <label>状态</label>
            <div class="multi-check">
              <label v-for="opt in statusOptions" :key="opt.value" class="check-item">
                <input type="checkbox" :value="opt.value" :checked="advancedFilter.statuses.includes(opt.value)" @change="e => { if (e.target.checked) advancedFilter.statuses.push(opt.value); else advancedFilter.statuses = advancedFilter.statuses.filter(v => v !== opt.value) }" />
                <span>{{ opt.label }}</span>
              </label>
            </div>
          </div>
          <div class="adv-actions">
            <button class="tb-btn primary" @click="applyAdvancedFilter">查询</button>
            <button class="tb-btn" @click="resetAdvancedFilter">重置</button>
          </div>
        </div>
      </div>
    </transition>

    <div class="inventory-table-wrap">
      <table class="inventory-main-table">
        <colgroup>
          <col style="width: 48px" />
          <col style="width: 150px" />
          <col style="width: 180px" />
          <col style="width: 165px" />
          <col style="width: 130px" />
          <col style="width: 100px" />
          <col style="width: 110px" />
          <col style="width: 100px" />
          <col style="width: 105px" />
          <col style="width: 120px" />
        </colgroup>
        <thead>
          <tr>
            <th></th>
            <th>物料编码</th>
            <th>物料名称</th>
            <th>批次号</th>
            <th>库位编码</th>
            <th class="number-cell">总库存</th>
            <th class="number-cell">可用数量</th>
            <th class="number-cell">冻结数量</th>
            <th>状态</th>
            <th class="inventory-action-column">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, idx) in pagedData" :key="item.item_code + item.batch_no + item.location_code" :class="{ selected: isItemSelected(item) }">
            <td><input type="checkbox" :checked="isItemSelected(item)" @change="toggleSelect(item)" /></td>
            <td><span class="code-text">{{ item.item_code }}</span></td>
            <td>{{ item.item_name }}</td>
            <td><span class="batch-text">{{ item.batch_no }}</span></td>
            <td><span class="loc-text">{{ item.location_code }}</span></td>
            <td class="number-cell">{{ item.onhand_qty }}</td>
            <td class="number-cell" :class="{ 'text-green': item.available_qty > 0, 'text-muted': item.available_qty === 0 }">{{ item.available_qty }}</td>
            <td class="number-cell" :class="{ 'text-orange': item.frozen_qty > 0 }">{{ item.frozen_qty }}</td>
            <td>
              <span :class="['status-tag', statusMap[item.inventory_status]?.cls]">
                <i class="status-dot"></i>
                {{ statusMap[item.inventory_status]?.label }}
              </span>
            </td>
            <td class="inventory-action-cell">
              <el-dropdown
                trigger="click"
                placement="bottom-end"
                :teleported="true"
                popper-class="inventory-more-dropdown"
                @command="command => handleInventoryCommand(command, item)"
              >
                <button
                  type="button"
                  class="inventory-action-btn"
                  @click.stop
                >
                  操作
                  <span>▼</span>
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-if="getAvailableQty(item) > 0"
                      command="freeze"
                    >冻结</el-dropdown-item>
                    <el-dropdown-item
                      v-if="getFrozenQty(item) > 0"
                      command="unfreeze"
                    >解冻</el-dropdown-item>
                    <el-dropdown-item command="move">移库</el-dropdown-item>
                    <el-dropdown-item command="batchAdjust">批次调整</el-dropdown-item>
                    <el-dropdown-item command="createReplenish">创建补货任务</el-dropdown-item>
                    <el-dropdown-item command="transaction">查看流水</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <span class="danger-menu-item">删除</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </td>
          </tr>
          <tr v-if="pagedData.length === 0">
            <td colspan="10" class="empty-cell">暂无库存数据 — 点击「新增」手动添加或「导入」批量导入库存</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="inv-pagination">
      <span class="page-info">共 {{ totalItems }} 条数据</span>
      <div class="page-size">
        <span>每页</span>
        <select v-model="pageSize" @change="changeSize(pageSize)">
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
          <option :value="100">100</option>
        </select>
        <span>条</span>
      </div>
      <div class="page-controls">
        <button :disabled="currentPage === 1" @click="changePage(currentPage - 1)">‹</button>
        <span class="page-num">{{ currentPage }} / {{ totalPages || 1 }} 页</span>
        <button :disabled="currentPage === totalPages || totalPages === 0" @click="changePage(currentPage + 1)">›</button>
      </div>
    </div>

    <div v-if="moreMenuOpen !== null" class="more-overlay" @click="moreMenuOpen = null"></div>

    <!-- Overlay bg -->

    <!-- Freeze Dialog -->
    <transition name="fade">
      <div v-if="showFreezeDialog" class="modal-overlay" @click.self="showFreezeDialog = false">
        <div class="modal-box">
          <div class="modal-header">
            <h3>冻结库存</h3>
            <button class="modal-close" @click="showFreezeDialog = false">✕</button>
          </div>
          <div class="modal-body">
            <div class="form-row">
              <label>物料编码</label>
              <span class="form-static">{{ actionItem?.item_code }}</span>
            </div>
            <div class="form-row">
              <label>冻结数量 <span class="req">*</span></label>
              <input v-model.number="freezeForm.quantity" type="number" min="1" class="form-input" />
            </div>
            <div class="form-row">
              <label>冻结原因 <span class="req">*</span></label>
              <input v-model="freezeForm.reason" class="form-input" placeholder="请输入冻结原因" />
            </div>
          </div>
          <div class="modal-foot">
            <button class="tb-btn" @click="showFreezeDialog = false">取消</button>
            <button class="tb-btn primary" @click="confirmFreeze">确认</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Unfreeze Dialog -->
    <el-dialog
      v-model="showUnfreezeDialog"
      title="库存解冻"
      width="480px"
    >
      <el-form label-width="90px">
        <el-form-item label="当前冻结">
          {{ getFrozenQty(actionItem || {}) }}
        </el-form-item>
        <el-form-item label="解冻数量">
          <el-input-number
            v-model="unfreezeForm.quantity"
            :min="1"
            :max="getFrozenQty(actionItem || {})"
          />
        </el-form-item>
        <el-form-item label="解冻原因">
          <el-input
            v-model="unfreezeForm.reason"
            type="textarea"
            placeholder="请输入解冻原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <button type="button" class="dialog-cancel-btn" @click="showUnfreezeDialog = false">取消</button>
        <button type="button" class="dialog-confirm-btn" @click="confirmUnfreeze">确认解冻</button>
      </template>
    </el-dialog>

    <!-- Move Dialog -->
    <transition name="fade">
      <div v-if="showMoveDialog" class="modal-overlay" @click.self="showMoveDialog = false">
        <div class="modal-box">
          <div class="modal-header">
            <h3>移库</h3>
            <button class="modal-close" @click="showMoveDialog = false">✕</button>
          </div>
          <div class="modal-body">
            <div class="form-row">
              <label>物料编码</label>
              <span class="form-static">{{ actionItem?.item_code }}</span>
            </div>
            <div class="form-row">
              <label>当前库位</label>
              <span class="form-static">{{ actionItem?.location_code }}</span>
            </div>
            <div class="form-row">
              <label>目标库位 <span class="req">*</span></label>
              <input v-model="moveForm.targetLocation" class="form-input" placeholder="请输入目标库位编码" />
            </div>
            <div class="form-row">
              <label>移库数量 <span class="req">*</span></label>
              <input v-model.number="moveForm.quantity" type="number" min="1" class="form-input" />
            </div>
          </div>
          <div class="modal-foot">
            <button class="tb-btn" @click="showMoveDialog = false">取消</button>
            <button class="tb-btn primary" @click="confirmMove">确认</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Batch Adjust Dialog -->
    <transition name="fade">
      <div v-if="showBatchAdjustDialog" class="modal-overlay" @click.self="showBatchAdjustDialog = false">
        <div class="modal-box">
          <div class="modal-header">
            <h3>批次调整</h3>
            <button class="modal-close" @click="showBatchAdjustDialog = false">✕</button>
          </div>
          <div class="modal-body">
            <div class="form-row">
              <label>物料编码</label>
              <span class="form-static">{{ actionItem?.item_code }}</span>
            </div>
            <div class="form-row">
              <label>物料名称</label>
              <span class="form-static">{{ actionItem?.item_name }}</span>
            </div>
            <div class="form-row">
              <label>原批次号</label>
              <span class="form-static">{{ actionItem?.batch_no }}</span>
            </div>
            <div class="form-row">
              <label>当前库位</label>
              <span class="form-static">{{ actionItem?.location_code }}</span>
            </div>
            <div class="form-row">
              <label>可用数量</label>
              <span class="form-static">{{ actionItem?.available_qty }}</span>
            </div>
            <div class="form-row">
              <label>目标批次号 <span class="req">*</span></label>
              <input v-model="batchAdjustForm.targetBatchNo" class="form-input" placeholder="请输入目标批次号" />
            </div>
            <div class="form-row">
              <label>调整数量 <span class="req">*</span></label>
              <input
                v-model.number="batchAdjustForm.quantity"
                type="number"
                min="1"
                :max="actionItem?.available_qty || undefined"
                class="form-input"
                placeholder="请输入调整数量"
              />
            </div>
            <div class="form-row">
              <label>调整原因 <span class="req">*</span></label>
              <input v-model="batchAdjustForm.reason" class="form-input" placeholder="请输入调整原因" />
            </div>
            <div class="form-row">
              <label>备注</label>
              <input v-model="batchAdjustForm.remark" class="form-input" placeholder="选填" />
            </div>
          </div>
          <div class="modal-foot">
            <button class="tb-btn" @click="showBatchAdjustDialog = false">取消</button>
            <button class="tb-btn primary" @click="confirmBatchAdjust">确认调整</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Transaction Dialog -->
    <transition name="fade">
      <div v-if="showTransactionDialog" class="modal-overlay" @click.self="showTransactionDialog = false">
        <div class="modal-box modal-lg">
          <div class="modal-header">
            <h3>历史流水 — {{ actionItem?.item_code }}</h3>
            <button class="modal-close" @click="showTransactionDialog = false">✕</button>
          </div>
          <div class="modal-body">
              <table class="inv-table" v-if="transactionItems.length">
                <thead>
                  <tr>
                    <th class="col-code">类型</th>
                    <th class="col-num">数量</th>
                    <th style="width:170px">时间</th>
                    <th class="col-name">操作人</th>
                    <th>备注</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="tx in transactionItems" :key="tx.transactionId || tx.id">
                    <td><span :class="['status-tag', (tx.transactionType === 'INBOUND' || tx.type === '入库') ? 'status-available' : (tx.transactionType === 'OUTBOUND' || tx.type === '出库') ? 'status-frozen' : 'status-pending']">{{ tx.transactionType === 'INBOUND' ? '入库' : tx.transactionType === 'OUTBOUND' ? '出库' : tx.transactionType || tx.type }}</span></td>
                    <td class="col-num" :class="{ 'text-green': (tx.quantityChange || tx.qty) > 0, 'text-orange': (tx.quantityChange || tx.qty) < 0 }">{{ (tx.quantityChange || tx.qty) > 0 ? '+' + (tx.quantityChange || tx.qty) : (tx.quantityChange || tx.qty) }}</td>
                    <td style="font-size:12px;color:#6c7d75">{{ tx.createdAt || tx.time || '' }}</td>
                    <td>{{ tx.operatedBy || tx.operator || '—' }}</td>
                    <td style="font-size:12px;color:#6c7d75">{{ tx.remark || tx.detail || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            <p v-else class="empty-cell" style="padding:20px 0!important">暂无流水记录</p>
          </div>
          <div class="modal-foot">
            <button class="tb-btn" @click="showTransactionDialog = false">关闭</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Export Confirm Dialog -->
    <transition name="fade">
      <div v-if="showExportConfirm" class="modal-overlay" @click.self="showExportConfirm = false">
        <div class="modal-box">
          <div class="modal-header">
            <h3>导出确认</h3>
            <button class="modal-close" @click="showExportConfirm = false">✕</button>
          </div>
          <div class="modal-body">
            <p class="confirm-text">将导出选中的 <strong>{{ exportSelectedCount }}</strong> 条数据，是否继续？</p>
          </div>
          <div class="modal-foot">
            <button class="tb-btn" @click="showExportConfirm = false">取消</button>
            <button class="tb-btn primary" @click="confirmExportSelected">确认</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Delete Confirm Dialog -->
    <transition name="fade">
      <div v-if="showDeleteConfirm" class="modal-overlay" @click.self="showDeleteConfirm = false">
        <div class="modal-box">
          <div class="modal-header">
            <h3>删除确认</h3>
            <button class="modal-close" @click="showDeleteConfirm = false">✕</button>
          </div>
          <div class="modal-body">
            <p class="confirm-text">确认删除物料 <strong>{{ deleteTarget?.item_code }} {{ deleteTarget?.item_name }}</strong> 的库存记录吗？</p>
          </div>
          <div class="modal-foot">
            <button class="tb-btn" @click="showDeleteConfirm = false">取消</button>
            <button class="tb-btn primary danger" @click="confirmDelete">确认删除</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Create Inventory Dialog -->
    <transition name="fade">
      <div v-if="showCreateDialog" class="modal-overlay" @click.self="showCreateDialog = false">
        <div class="modal-box modal-lg">
          <div class="modal-header">
            <h3>新增库存</h3>
            <button class="modal-close" @click="showCreateDialog = false">✕</button>
          </div>
          <div class="modal-body">
            <div class="form-row">
              <label>仓库 <span class="req">*</span></label>
              <select v-model="createForm.warehouseId" class="form-select" @change="onWarehouseChange">
                <option :value="null" disabled>请选择仓库</option>
                <option v-for="w in warehouseOptions" :key="w.id" :value="w.id">{{ w.name }}</option>
              </select>
            </div>
            <div class="form-row">
              <label>库位 <span class="req">*</span></label>
              <select v-model="createForm.locationId" class="form-select">
                <option :value="null" disabled>请选择库位</option>
                <option v-for="l in locationOptions" :key="l.id" :value="l.id">{{ l.code }}</option>
              </select>
            </div>
            <div class="form-row">
              <label>物料编码 <span class="req">*</span></label>
              <input v-model="createForm.itemCode" class="form-input" placeholder="请输入物料编码" />
            </div>
            <div class="form-row">
              <label>批次号 <span class="req">*</span></label>
              <input v-model="createForm.batchNo" class="form-input" placeholder="请输入批次号" />
            </div>
            <div class="form-row">
              <label>库存数量 <span class="req">*</span></label>
              <input v-model.number="createForm.onhandQty" type="number" min="1" class="form-input" />
            </div>
          </div>
          <div class="modal-foot">
            <button class="tb-btn" @click="showCreateDialog = false">取消</button>
            <button class="tb-btn primary" @click="confirmCreate">确认新增</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Import Inventory Dialog -->
    <transition name="fade">
      <div v-if="showImportDialog" class="modal-overlay" @click.self="showImportDialog = false">
        <div class="modal-box modal-lg">
          <div class="modal-header">
            <h3>导入库存</h3>
            <button class="modal-close" @click="showImportDialog = false">✕</button>
          </div>
          <div class="modal-body">
            <p class="confirm-text" style="color:#587766;">通过 Excel 文件批量导入库存数据。请按以下列顺序准备数据：</p>
            <p class="confirm-text">仓库ID、库位ID、物料编码、批次号、数量（列名支持中英文模糊匹配）。</p>
            <div class="form-row" style="margin-top:14px;">
              <label>选择文件 <span class="req">*</span></label>
              <input type="file" accept=".xlsx,.xls,.csv" class="file-input" @change="handleImportFileChange" />
            </div>
            <p class="confirm-text" style="color:#6c7d75;font-size:12px;">{{ importFileName || '未选择任何文件' }}</p>
          </div>
          <div class="modal-foot">
            <button class="tb-btn" @click="showImportDialog = false">取消</button>
            <button class="tb-btn primary" @click="confirmImport">确认导入</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Toast -->
    <transition name="fade">
      <div v-if="toastMessage" class="toast-notification">{{ toastMessage }}</div>
    </transition>

    <div v-if="showFreezeDialog || showUnfreezeDialog || showMoveDialog || showBatchAdjustDialog || showTransactionDialog || showExportConfirm || showDeleteConfirm || showCreateDialog || showImportDialog" class="modal-overlay-bg"></div>
  </div>
</template>

<style scoped>
.inv-page {
  width: min(1200px, 100%);
  margin: 0 auto;
  font-family: inherit;
}

.inv-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 6px;
}

.inv-header h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(26px, 3vw, 34px);
  font-weight: 600;
  color: #2a3a33;
}

.inv-subtitle {
  margin: 6px 0 0;
  color: #7d8983;
  font-size: 14px;
}

.inv-strip {
  display: flex;
  gap: 6px;
}

.strip-item {
  min-width: 64px;
  border: 1px solid #e2e8e4;
  border-radius: 10px;
  padding: 8px 14px;
  background: rgba(255,255,255,0.78);
  text-align: center;
}

.strip-item .num {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: #33473f;
  line-height: 1.2;
}

.strip-item .num.green { color: #3b7a5a; }
.strip-item .num.orange { color: #c98a2e; }
.strip-item .num.blue { color: #437a9e; }
.strip-item .num.gray { color: #8b958f; }

.strip-item .lbl {
  display: block;
  margin-top: 2px;
  color: #8b958f;
  font-size: 11px;
}

/* Toolbar */
.inv-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding: 12px 16px;
  background: rgba(255,255,255,0.85);
  border: 1px solid #e2eae5;
  border-radius: 10px;
}

.toolbar-left {
  display: flex;
  gap: 8px;
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

.tb-btn:hover {
  background: #eef4f0;
}

.tb-btn.primary {
  background: #587766;
  border-color: #587766;
  color: #fff;
}

.tb-btn.primary:hover {
  background: #4a6a59;
}

.tb-btn.primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.tb-btn.primary:disabled:hover {
  background: #587766;
}

.tb-btn.primary.danger {
  background: #c0392b;
  border-color: #c0392b;
}

.tb-btn.primary.danger:hover {
  background: #a93226;
}

.tb-search {
  border: 1px solid #dfe6e1;
  border-radius: 8px;
  padding: 7px 14px;
  font-size: 13px;
  color: #33473f;
  outline: none;
  background: #fff;
  width: 240px;
}

.tb-search::placeholder {
  color: #bccbc2;
}

/* Table */
.inventory-table-wrap {
  width: 100%;
  margin-top: 12px;
  overflow-x: auto;
  overflow-y: visible;
  border: 1px solid #e2eae5;
  border-radius: 10px;
  background: rgba(255,255,255,0.9);
}

.inventory-main-table {
  width: 100%;
  min-width: 1210px;
  table-layout: fixed;
  border-collapse: collapse;
}

.inventory-main-table th,
.inventory-main-table td {
  height: 52px;
  padding: 0 12px;
  box-sizing: border-box;
  vertical-align: middle;
}

.inventory-main-table thead {
  background: #f0f5f2;
}

.inventory-main-table th {
  font-size: 12px;
  font-weight: 600;
  color: #5f7268;
  text-align: left;
  white-space: nowrap;
  border-bottom: 1px solid #e2eae5;
}

.inventory-main-table td {
  font-size: 13px;
  color: #3f554b;
  border-bottom: 1px solid #ecf1ee;
}

.inventory-main-table tbody tr:hover {
  background: #f6f9f7;
}

.inventory-main-table tbody tr.selected {
  background: #e6f1ea;
}

.inventory-main-table .number-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.inventory-action-column,
.inventory-action-cell {
  width: 120px;
  min-width: 120px;
  text-align: center !important;
  overflow: visible !important;
}

.inventory-action-cell {
  padding-left: 10px !important;
  padding-right: 10px !important;
}

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

.loc-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #437a9e;
  font-weight: 500;
}

.text-green { color: #3b7a5a; font-weight: 600; }
.text-orange { color: #c98a2e; font-weight: 600; }
.text-muted { color: #bccbc2; }

/* Status tag */
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 500;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-available { background: #e0f0e6; color: #3b7a5a; }
.status-available .status-dot { background: #3b7a5a; }

.status-frozen { background: #fdf0e8; color: #c98a2e; }
.status-frozen .status-dot { background: #c98a2e; }

.status-pending { background: #d6e6f0; color: #437a9e; }
.status-pending .status-dot { background: #437a9e; }

.status-disabled { background: #eef4f0; color: #8b958f; }
.status-disabled .status-dot { background: #8b958f; }

/* Actions */
.inventory-action-btn {
  appearance: none;
  -webkit-appearance: none;
  height: 34px;
  min-width: 82px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid #c8d9d0;
  border-radius: 8px;
  color: #4f7061;
  background: #ffffff;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  white-space: nowrap;
  box-shadow: none;
  pointer-events: auto;
  transition:
    color 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease;
}

.inventory-action-btn:hover {
  color: #3f6554;
  border-color: #88a394;
  background: #f1f6f3;
}

.inventory-action-btn:focus-visible {
  outline: 2px solid rgba(85, 124, 105, 0.2);
  outline-offset: 2px;
}

.danger-menu-item {
  color: #c0392b;
}

.dialog-cancel-btn {
  appearance: none;
  height: 36px;
  padding: 0 18px;
  border: 1px solid #d5e1db;
  border-radius: 8px;
  background: #fff;
  color: #557466;
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
}

.dialog-cancel-btn:hover {
  border-color: #8da799;
  background: #f2f7f4;
}

.dialog-confirm-btn {
  appearance: none;
  height: 36px;
  padding: 0 18px;
  border: 0;
  border-radius: 8px;
  background: #557c69;
  color: #fff;
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.dialog-confirm-btn:hover {
  background: #486b5a;
}

/* Empty */
.empty-cell {
  text-align: center;
  padding: 40px 0 !important;
  color: #9caaa3;
  font-size: 14px;
}

/* Pagination */
.inv-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 12px;
  padding: 10px 16px;
  background: rgba(255,255,255,0.85);
  border: 1px solid #e2eae5;
  border-radius: 10px;
}

.page-info {
  color: #8b958f;
  font-size: 13px;
  margin-right: auto;
}

.page-size {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #8b958f;
  font-size: 13px;
}

.page-size select {
  border: 1px solid #dfe6e1;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  color: #46544f;
  background: #fff;
  outline: none;
}

.page-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

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

.page-controls button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-controls button:hover:not(:disabled) {
  background: #eef4f0;
}

.page-num {
  font-size: 13px;
  color: #46544f;
  min-width: 60px;
  text-align: center;
}

/* Advanced Filter */
.adv-filter {
  margin-top: 8px;
  padding: 16px;
  background: rgba(255,255,255,0.9);
  border: 1px solid #e2eae5;
  border-radius: 10px;
}

.adv-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
}

.adv-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 140px;
}

.adv-field label {
  font-size: 12px;
  color: #6c7d75;
  font-weight: 500;
}

.adv-input {
  border: 1px solid #dfe6e1;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 13px;
  color: #33473f;
  outline: none;
  background: #fff;
}

.adv-input:focus {
  border-color: #587766;
}

.adv-actions {
  display: flex;
  gap: 6px;
  align-items: flex-end;
  padding-bottom: 1px;
}

.multi-check {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 4px 0;
}

.check-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #46544f;
  cursor: pointer;
}

.check-item input {
  accent-color: #587766;
}

/* Modal */
.modal-overlay-bg {
  position: fixed;
  inset: 0;
  background: rgba(40, 55, 50, 0.35);
  z-index: 199;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.modal-box {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.18);
  width: 420px;
  max-width: 90vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-box.modal-lg {
  width: 600px;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 12px;
  border-bottom: 1px solid #ecf1ee;
}

.modal-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #2a3a33;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
}

.modal-close {
  border: 0;
  background: transparent;
  font-size: 16px;
  color: #8b958f;
  cursor: pointer;
  padding: 4px;
  line-height: 1;
}

.modal-close:hover {
  color: #46544f;
}

.modal-body {
  padding: 16px 20px;
  overflow-y: auto;
  flex: 1;
}

.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px 16px;
  border-top: 1px solid #ecf1ee;
}

/* Form */
.form-row {
  margin-bottom: 14px;
}

.form-row:last-child {
  margin-bottom: 0;
}

.form-row label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #46544f;
  margin-bottom: 5px;
}

.form-row .req {
  color: #d97a6b;
}

.form-input {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #dfe6e1;
  border-radius: 6px;
  padding: 7px 10px;
  font-size: 13px;
  color: #33473f;
  outline: none;
  background: #fff;
}

.form-input:focus {
  border-color: #587766;
}

.form-select {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #dfe6e1;
  border-radius: 6px;
  padding: 7px 10px;
  font-size: 13px;
  color: #33473f;
  outline: none;
  background: #fff;
  cursor: pointer;
}

.form-select:focus {
  border-color: #587766;
}

.form-static {
  display: block;
  padding: 7px 0;
  font-size: 13px;
  color: #33473f;
  font-weight: 500;
}

.confirm-text {
  font-size: 14px;
  color: #46544f;
  line-height: 1.6;
  margin: 8px 0;
}

.confirm-text strong {
  color: #2a3a33;
}

.download-link {
  color: #437a9e;
  font-size: 13px;
  text-decoration: underline;
  cursor: pointer;
}

.download-link:hover {
  color: #2a5f7a;
}

.file-upload {
  position: relative;
}

.file-input {
  font-size: 13px;
  color: #46544f;
}

.file-input::file-selector-button {
  border: 1px solid #cddbd3;
  border-radius: 6px;
  padding: 5px 12px;
  background: #f0f5f2;
  color: #567566;
  font-size: 12px;
  cursor: pointer;
  margin-right: 8px;
}

.file-input::file-selector-button:hover {
  background: #e2eae5;
}

/* Toast */
.toast-notification {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  background: #587766;
  color: #fff;
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  z-index: 999;
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
}

/* Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Scrollbar */
.modal-body::-webkit-scrollbar {
  width: 5px;
}

.modal-body::-webkit-scrollbar-track {
  background: transparent;
}

.modal-body::-webkit-scrollbar-thumb {
  background: #cddbd3;
  border-radius: 3px;
}

</style>

<style>
.inventory-more-dropdown {
  z-index: 5000 !important;
}

.inventory-more-dropdown.el-popper {
  position: absolute !important;
}

.inventory-more-dropdown .el-dropdown-menu {
  display: block !important;
  min-width: 112px;
  margin: 0;
  padding: 6px;
  position: static;
  border: 0;
  background: #ffffff;
}

.inventory-more-dropdown .el-dropdown-menu__item {
  width: 100%;
  height: 34px;
  padding: 0 12px;
  display: flex !important;
  align-items: center;
  justify-content: flex-start;
  box-sizing: border-box;
  border: 0;
  border-radius: 6px;
  color: #49665a;
  background: transparent;
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
}

.inventory-more-dropdown .el-dropdown-menu__item:hover,
.inventory-more-dropdown .el-dropdown-menu__item:focus {
  color: #365747;
  background: #eef5f1;
}
</style>
