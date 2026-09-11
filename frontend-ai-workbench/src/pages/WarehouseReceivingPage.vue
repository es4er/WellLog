<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { apiGet, apiPost } from '../api'
import { useSession } from '../composables/useSession'
import { buildReceiptWarehouseOptions } from '../data/warehouseZoneConstants'

const { userId } = useSession()

const receipts = ref([])
const suppliers = ref([])
const items = ref([])
const warehouses = ref([])
const zones = ref([])
const batches = ref([])
const locations = ref([])
const loading = ref(false)
const saving = ref(false)
const puttingAway = ref(false)
const submittingInspection = ref(false)
const pageMessage = ref('')
const pageError = ref('')

const selectedReceiptId = ref('new')
const receiptKeyword = ref('')
const receiptStatusFilter = ref('')
const activeDetail = ref(null)
const lineFilter = ref('')
const selectedLineIds = ref(new Set())
const activeLineKey = ref(null)
const scanDialogOpen = ref(false)
const scanCode = ref('')
const createDialogOpen = ref(false)
const editInfoOpen = ref(false)
const locTab = ref('recommend') // recommend | manual | staging
const panelCollapsed = ref(false)
const addFormError = ref('')
const mainCardRef = ref(null)
const panelHeightPx = ref(null)
let mainResizeObserver = null

const panelStyle = computed(() => {
  if (!panelHeightPx.value || panelCollapsed.value) return {}
  return {
    height: panelHeightPx.value,
    minHeight: panelHeightPx.value,
    maxHeight: panelHeightPx.value,
  }
})

function isStackedLayout() {
  return window.matchMedia('(max-width: 1200px)').matches
}

const draft = reactive({
  erpPoNo: '',
  supplierId: null,
  warehouseId: null,
  receiptTargetKey: '',
  deliveryNo: '',
  deliveryPerson: '',
  remark: '',
  lines: []
})

/** 添加收货单弹窗表单 */
const addForm = reactive({
  erpPoNo: '',
  supplierId: null,
  warehouseId: null,
  receiptTargetKey: '',
  remark: '',
  lines: []
})

/** 行级上架分配：key → { locationId, putawayQty } */
const lineAlloc = reactive({})

const panelForm = reactive({
  locationId: null,
  putawayQty: 0
})

const flowSteps = [
  { title: '仓管收货', desc: '核对送货信息' },
  { title: '质检确认', desc: '质量检验结果' },
  { title: '分配库位', desc: '推荐上架位置' },
  { title: '上架确认', desc: '完成上架操作' },
  { title: '收货完成', desc: '入库完成' }
]

/** 页面默认展示的收货单号 */
const DEFAULT_RECEIPT_NO = 'RC20260713195939132833'

const PUTAWAY_READY = ['INSPECTED', 'INSPECTION_PASSED', 'QUALITY_PASS', 'QUALIFIED']
const STORED_STATUSES = ['STORED', 'AVAILABLE', 'COMPLETED']

const LINE_FILTERS = [
  { key: '', label: '全部' },
  { key: 'PENDING_INSPECTION', label: '待质检' },
  { key: 'PENDING_ALLOC', label: '待分配' },
  { key: 'PENDING_PUTAWAY', label: '待上架' },
  { key: 'FAILED', label: '不合格' },
  { key: 'STORED', label: '已上架' }
]

const RECEIPT_FILTERS = [
  { key: '', label: '全部' },
  { key: 'PENDING_INSPECTION', label: '待质检' },
  { key: 'INSPECTING', label: '质检中' },
  { key: 'PENDING_PUTAWAY', label: '待上架' },
  { key: 'INSPECTION_FAILED', label: '质检失败' },
  { key: 'STORED', label: '已完成' }
]

const FAILED_LINE_STATUSES = ['UNQUALIFIED', 'INSPECTION_FAILED', 'QUALITY_FAIL', 'QUARANTINED']
const FAILED_RECEIPT_STATUSES = ['INSPECTION_FAILED', 'QUALITY_FAIL']

const supplierMap = computed(() => Object.fromEntries(suppliers.value.map((s) => [s.supplierId, s])))
const warehouseMap = computed(() => Object.fromEntries(warehouses.value.map((w) => [w.warehouseId, w])))
const receiptWarehouseOptions = computed(() => buildReceiptWarehouseOptions(zones.value, warehouses.value))
const itemMap = computed(() => Object.fromEntries(items.value.map((i) => [i.itemId, i])))
const batchMap = computed(() => Object.fromEntries(batches.value.map((b) => [b.batchId, b])))
const locationMap = computed(() => Object.fromEntries(locations.value.map((l) => [l.locationId, l])))

const isNewMode = computed(() => selectedReceiptId.value === 'new')
const activeOrder = computed(() => (isNewMode.value ? null : activeDetail.value?.order ?? null))

function resolveLineStatus(lineStatus, orderStatus) {
  if (lineStatus === 'INSPECTING') return 'INSPECTING'
  if (STORED_STATUSES.includes(orderStatus) || lineStatus === 'STORED') return 'STORED'
  if (FAILED_LINE_STATUSES.includes(lineStatus)) return 'FAILED'
  if (PUTAWAY_READY.includes(orderStatus) || ['INSPECTED', 'QUALITY_PASS', 'QUALIFIED'].includes(lineStatus)) {
    return 'PENDING_PUTAWAY'
  }
  if (lineStatus === 'PENDING_RECEIPT') return 'PENDING_RECEIPT'
  return 'PENDING_INSPECTION'
}

const QC_DONE_LINE_STATUSES = [
  'INSPECTED', 'QUALIFIED', 'INSPECTION_PASSED', 'QUALITY_PASS',
  'UNQUALIFIED', 'INSPECTION_FAILED', 'QUALITY_FAIL', 'QUARANTINED'
]
const PUTAWAY_LINE_STATUSES = ['INSPECTED', 'QUALIFIED', 'INSPECTION_PASSED', 'QUALITY_PASS']

function deriveReceiptStatus(orderStatus, rawLines) {
  if (!rawLines?.length) return orderStatus
  if (STORED_STATUSES.includes(orderStatus)) return orderStatus
  if (FAILED_RECEIPT_STATUSES.includes(orderStatus)) return orderStatus

  const statuses = rawLines.map((l) => l.lineStatus || 'PENDING_INSPECTION')
  const allPending = statuses.every((s) => s === 'PENDING_INSPECTION')
  const anyInspecting = statuses.some((s) => s === 'INSPECTING')
  const anyPendingInspection = statuses.some((s) => s === 'PENDING_INSPECTION')
  const anyInspected = statuses.some((s) => PUTAWAY_LINE_STATUSES.includes(s))
  const anyFailed = statuses.some((s) => FAILED_LINE_STATUSES.includes(s))
  const allQcDone = statuses.every((s) => QC_DONE_LINE_STATUSES.includes(s) || STORED_STATUSES.includes(s))

  if (allQcDone && anyFailed && !anyInspected) return 'INSPECTION_FAILED'
  if (allQcDone && anyFailed && anyInspected) return 'QUALITY_FAIL'
  if (allQcDone && anyInspected) return 'INSPECTED'
  if (anyInspecting || (anyInspected && anyPendingInspection) || (anyFailed && anyPendingInspection)) {
    return 'INSPECTING'
  }
  if (allPending) return 'PENDING_INSPECTION'
  if (anyFailed && !anyInspected && !anyPendingInspection) return 'INSPECTION_FAILED'
  return orderStatus
}

const effectiveReceiptStatus = computed(() =>
  deriveReceiptStatus(activeOrder.value?.receiptStatus, activeDetail.value?.lines)
)

function qcLabel(status) {
  if (status === 'INSPECTING') return { text: '检测中', cls: 'info' }
  if (status === 'STORED' || status === 'PENDING_PUTAWAY') return { text: '合格', cls: 'ok' }
  if (status === 'FAILED') return { text: '不合格', cls: 'fail' }
  if (status === 'PENDING_RECEIPT') return { text: '待收货', cls: 'muted' }
  return { text: '待检', cls: 'pending' }
}

function priorityOf(itemId) {
  const n = Number(itemId) || 0
  if (n % 3 === 0) return { text: '高', cls: 'high' }
  if (n % 3 === 1) return { text: '中', cls: 'mid' }
  return { text: '低', cls: 'low' }
}

function materialImageUrl(itemCode) {
  const code = String(itemCode ?? '').trim()
  if (!code) return ''
  return `${import.meta.env.BASE_URL}materials/${encodeURIComponent(code)}.png`
}

function hideBrokenMaterialImage(event) {
  const image = event.currentTarget

  if (image.dataset.fallback === 'true') {
    return
  }

  image.dataset.fallback = 'true'
  image.src = `${import.meta.env.BASE_URL}materials/material-default.png`
  image.style.display = 'block'
}

function showMaterialImage(event) {
  event.currentTarget.style.display = 'block'
}

function recommendLocations(itemId) {
  const list = locations.value.slice(0, 6)
  if (!list.length) return []
  return list.map((loc, i) => {
    const seed = (Number(itemId) || 1) + i * 7
    const match = 92 - ((seed * 11) % 35)
    const remain = 80 - ((seed * 3) % 55)
    return {
      ...loc,
      match,
      remain,
      usedPct: Math.min(95, 100 - remain)
    }
  }).sort((a, b) => b.match - a.match)
}

const enrichedLines = computed(() => {
  if (isNewMode.value) {
    return draft.lines.map((line, index) => {
      const item = itemMap.value[line.itemId]
      const key = line._key || `draft-${index}`
      const alloc = lineAlloc[key] || {}
      const recs = recommendLocations(line.itemId)
      return {
        ...line,
        key,
        itemName: item?.itemName || `物料 #${line.itemId}`,
        itemCode: item?.itemCode || '',
        specModel: item?.specModel || '-',
        unit: '件',
        purchaseQty: Number(line.purchaseQty ?? 0),
        receivedQty: Number(line.receivedQty ?? 0),
        putawayQty: alloc.putawayQty ?? Number(line.currentQty ?? 0),
        batchNo: line.batchNo || '-',
        expireDate: line.expireDate || '2026-12-31',
        lineStatus: 'PENDING_RECEIPT',
        uiStatus: 'PENDING_RECEIPT',
        qc: qcLabel('PENDING_RECEIPT'),
        priority: priorityOf(line.itemId),
        recommend: recs[0] || null,
        locationId: alloc.locationId ?? null
      }
    })
  }

  const lines = activeDetail.value?.lines ?? []
  const orderStatus = effectiveReceiptStatus.value || activeOrder.value?.receiptStatus || 'PENDING_INSPECTION'
  return lines.map((line) => {
    const item = itemMap.value[line.itemId]
    const batch = batchMap.value[line.batchId]
    const lineStatus = resolveLineStatus(line.lineStatus, orderStatus)
    const key = String(line.receiptLineId)
    const alloc = lineAlloc[key] || {}
    const recs = recommendLocations(line.itemId)
    let uiStatus = lineStatus
    if (lineStatus === 'PENDING_PUTAWAY' && !alloc.locationId) uiStatus = 'PENDING_ALLOC'
    else if (lineStatus === 'PENDING_PUTAWAY' && alloc.locationId) uiStatus = 'PENDING_PUTAWAY'
    return {
      ...line,
      key,
      rawLineStatus: line.lineStatus,
      itemName: item?.itemName || `物料 #${line.itemId}`,
      itemCode: item?.itemCode || '',
      specModel: item?.specModel || '-',
      unit: '件',
      purchaseQty: Number(line.receivedQty ?? 0),
      receivedQty: Number(line.receivedQty ?? 0),
      putawayQty: alloc.putawayQty ?? Number(line.receivedQty ?? 0),
      batchNo: batch?.batchNo || '-',
      expireDate: batch?.expireDate || '2026-12-31',
      lineStatus,
      uiStatus,
      qc: qcLabel(lineStatus),
      priority: priorityOf(line.itemId),
      recommend: recs[0] || null,
      locationId: alloc.locationId ?? null
    }
  })
})

const lineStats = computed(() => {
  const rows = enrichedLines.value
  return {
    all: rows.length,
    pendingInspection: rows.filter((r) => r.uiStatus === 'PENDING_INSPECTION').length,
    pendingAlloc: rows.filter((r) => r.uiStatus === 'PENDING_ALLOC').length,
    pendingPutaway: rows.filter((r) => r.uiStatus === 'PENDING_PUTAWAY').length,
    stored: rows.filter((r) => r.uiStatus === 'STORED').length,
    qcOk: rows.filter((r) => r.qc.cls === 'ok').length,
    qcPending: rows.filter((r) => r.qc.cls === 'pending' || r.qc.cls === 'muted').length,
    qcFail: rows.filter((r) => r.qc.cls === 'fail').length
  }
})

const filteredLines = computed(() => {
  if (!lineFilter.value) return enrichedLines.value
  return enrichedLines.value.filter((line) => line.uiStatus === lineFilter.value)
})

const filterCounts = computed(() => ({
  '': lineStats.value.all,
  PENDING_INSPECTION: lineStats.value.pendingInspection,
  PENDING_ALLOC: lineStats.value.pendingAlloc,
  PENDING_PUTAWAY: lineStats.value.pendingPutaway,
  FAILED: lineStats.value.qcFail,
  STORED: lineStats.value.stored
}))

const hasQcFailure = computed(() => !isNewMode.value && lineStats.value.qcFail > 0)

const activeStep = computed(() => {
  const status = effectiveReceiptStatus.value
  if (!status) return 0
  if (STORED_STATUSES.includes(status)) return 4
  if (FAILED_RECEIPT_STATUSES.includes(status)) return 1
  if (PUTAWAY_READY.includes(status)) {
    const failCount = enrichedLines.value.filter((l) => l.uiStatus === 'FAILED').length
    if (failCount > 0) {
      const passCount = enrichedLines.value.filter((l) =>
        ['PENDING_PUTAWAY', 'PENDING_ALLOC'].includes(l.uiStatus)
      ).length
      if (!passCount) return 1
    }
    const putawayLines = enrichedLines.value.filter((l) => l.uiStatus === 'PENDING_PUTAWAY')
    const allAllocated = putawayLines.length > 0 && putawayLines.every((l) => l.locationId)
    return allAllocated ? 3 : 2
  }
  if (status === 'PENDING_INSPECTION') return 1
  if (status === 'INSPECTING') return 1
  return 0
})

const orderStatusLabel = computed(() => {
  if (isNewMode.value) return { text: '草稿', cls: 'warn' }
  const s = effectiveReceiptStatus.value
  if (STORED_STATUSES.includes(s)) return { text: '已完成', cls: 'ok' }
  if (s === 'INSPECTION_FAILED') return { text: '质检失败', cls: 'fail' }
  if (s === 'QUALITY_FAIL') return { text: '部分质检失败', cls: 'fail' }
  if (PUTAWAY_READY.includes(s)) {
    const failCount = enrichedLines.value.filter((l) => l.uiStatus === 'FAILED').length
    if (failCount > 0) {
      const passCount = enrichedLines.value.filter((l) =>
        ['PENDING_PUTAWAY', 'PENDING_ALLOC'].includes(l.uiStatus)
      ).length
      if (!passCount) return { text: '质检失败', cls: 'fail' }
      return { text: '部分质检失败', cls: 'fail' }
    }
    const putawayLines = enrichedLines.value.filter((l) => l.uiStatus === 'PENDING_PUTAWAY')
    const needAlloc = putawayLines.some((l) => !l.locationId)
    if (needAlloc) return { text: '质检完待上架', cls: 'ok' }
    return { text: '待上架确认', cls: 'info' }
  }
  if (s === 'INSPECTING') {
    const hasPartialPutaway = enrichedLines.value.some((l) => l.uiStatus === 'PENDING_PUTAWAY')
    if (hasPartialPutaway) return { text: '部分质检完待上架', cls: 'info' }
    return { text: '质检中', cls: 'info' }
  }
  if (s === 'PENDING_INSPECTION') return { text: '待质检', cls: 'warn' }
  return { text: statusText(s), cls: 'warn' }
})

const activeLine = computed(() => {
  if (!activeLineKey.value) return filteredLines.value[0] || enrichedLines.value[0] || null
  return enrichedLines.value.find((l) => l.key === activeLineKey.value) || null
})

const recommendedList = computed(() => {
  if (!activeLine.value) return []
  return recommendLocations(activeLine.value.itemId)
})

const totals = computed(() => {
  const rows = filteredLines.value
  return {
    purchase: rows.reduce((s, r) => s + Number(r.purchaseQty || 0), 0),
    received: rows.reduce((s, r) => s + Number(r.receivedQty || 0), 0),
    putaway: rows.reduce((s, r) => s + Number(r.putawayQty || 0), 0)
  }
})

const canConfirmPutaway = computed(() => {
  if (isNewMode.value) return false
  const putawayLines = enrichedLines.value.filter((l) => l.uiStatus === 'PENDING_PUTAWAY')
  if (!putawayLines.length) return false
  return putawayLines.every((l) => l.locationId)
})

const canBatchAlloc = computed(() => {
  return selectedLineIds.value.size > 0 && canConfirmPutaway.value
})

const selectedPendingLines = computed(() =>
  enrichedLines.value.filter(
    (line) => selectedLineIds.value.has(line.key) && line.rawLineStatus === 'PENDING_INSPECTION'
  )
)

const canSubmitInspection = computed(() => {
  if (isNewMode.value || !activeOrder.value) return false
  const pending = enrichedLines.value.filter((line) => line.rawLineStatus === 'PENDING_INSPECTION')
  if (!pending.length) return false
  if (!selectedLineIds.value.size) return true
  return selectedPendingLines.value.length === selectedLineIds.value.size
})

function isLineSubmittable(line) {
  if (isNewMode.value) return true
  return (
    activeOrder.value?.receiptStatus === 'PENDING_INSPECTION' &&
    line.rawLineStatus === 'PENDING_INSPECTION'
  )
}

function isLineSelectable(line) {
  if (isNewMode.value) return true
  if (line.uiStatus === 'FAILED') return false
  const orderStatus = effectiveReceiptStatus.value
  if (FAILED_RECEIPT_STATUSES.includes(orderStatus)) return false
  if (orderStatus === 'PENDING_INSPECTION') {
    return line.rawLineStatus === 'PENDING_INSPECTION'
  }
  if (canPutaway(orderStatus) || line.uiStatus === 'PENDING_PUTAWAY') return true
  return false
}

function statusText(status) {
  const map = {
    PENDING_INSPECTION: '待质检',
    INSPECTING: '质检中',
    INSPECTED: '质检完成待上架',
    INSPECTION_PASSED: '质检通过待上架',
    QUALITY_PASS: '质检通过待上架',
    QUALIFIED: '质检合格待上架',
    INSPECTION_FAILED: '质检失败',
    QUALITY_FAIL: '部分质检失败',
    UNQUALIFIED: '质检不合格',
    QUARANTINED: '隔离待退（禁止上架）',
    STORED: '已上架',
    AVAILABLE: '库存可用',
    COMPLETED: '已上架'
  }
  return map[status] || status || '待质检'
}

function receiptUiStatus(status) {
  if (STORED_STATUSES.includes(status)) return 'STORED'
  if (FAILED_RECEIPT_STATUSES.includes(status)) return 'INSPECTION_FAILED'
  if (PUTAWAY_READY.includes(status)) return 'PENDING_PUTAWAY'
  if (status === 'INSPECTING') return 'INSPECTING'
  if (status === 'PENDING_INSPECTION') return 'PENDING_INSPECTION'
  return status || 'PENDING_INSPECTION'
}

function receiptSearchText(row) {
  return [
    row.receiptNo,
    row.receiptId,
    row.erpPoNo,
    supplierLabel(row.supplierId),
    warehouseLabel(row.warehouseId),
    statusText(row.receiptStatus),
    row.remark
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
}

const receiptFilterCounts = computed(() => {
  const counts = { '': receipts.value.length }
  for (const filter of RECEIPT_FILTERS) {
    if (!filter.key) continue
    counts[filter.key] = receipts.value.filter(
      (row) => receiptUiStatus(row.receiptStatus) === filter.key
    ).length
  }
  return counts
})

const filteredReceipts = computed(() => {
  const keyword = receiptKeyword.value.trim().toLowerCase()
  const statusKey = receiptStatusFilter.value
  let list = receipts.value

  if (statusKey) {
    list = list.filter((row) => receiptUiStatus(row.receiptStatus) === statusKey)
  }
  if (keyword) {
    list = list.filter((row) => receiptSearchText(row).includes(keyword))
  }

  if (!isNewMode.value) {
    const selected = receipts.value.find((row) => row.receiptId === selectedReceiptId.value)
    if (selected && !list.some((row) => row.receiptId === selected.receiptId)) {
      list = [selected, ...list]
    }
  }

  return list
})

const receiptFilterActive = computed(
  () => Boolean(receiptKeyword.value.trim() || receiptStatusFilter.value)
)

function canPutaway(status) {
  return PUTAWAY_READY.includes(status)
}

function formatDate(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function supplierLabel(id) {
  const s = supplierMap.value[id]
  return s ? s.supplierName : id ? `供应商 #${id}` : '-'
}

function warehouseLabel(id) {
  const w = warehouseMap.value[id]
  return w ? w.warehouseName : id ? `仓库 #${id}` : '-'
}

function resolveReceiptTarget(targetKey) {
  if (!targetKey) return null
  return receiptWarehouseOptions.value.find((opt) => opt.key === targetKey) ?? null
}

function defaultReceiptTargetKey() {
  return receiptWarehouseOptions.value[0]?.key ?? ''
}

function applyReceiptTarget(form, targetKey) {
  const target = resolveReceiptTarget(targetKey)
  form.receiptTargetKey = targetKey || ''
  form.warehouseId = target?.warehouseId ?? null
}

function buildReceiptRemark(userRemark, target) {
  const parts = []
  if (target?.zoneCode) {
    parts.push(`[收货库区:${target.zoneCode} ${target.zoneName || ''}]`.trim())
  }
  const trimmed = userRemark?.trim()
  if (trimmed) parts.push(trimmed)
  return parts.length ? parts.join(' ') : null
}

function locationLabel(id) {
  const loc = locationMap.value[id]
  if (!loc) return '-'
  return loc.locationCode + (loc.locationName ? ` · ${loc.locationName}` : '')
}

function makeBatchNo() {
  const d = new Date()
  const stamp = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
  return `B${stamp}${String(Date.now()).slice(-3)}`
}

function makePoNo() {
  const d = new Date()
  const stamp = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
  return `PO-${stamp}001`
}

function resetDraft() {
  draft.erpPoNo = makePoNo()
  draft.supplierId = suppliers.value[0]?.supplierId ?? null
  applyReceiptTarget(draft, defaultReceiptTargetKey())
  draft.deliveryNo = `DN-${Date.now().toString().slice(-6)}`
  draft.deliveryPerson = ''
  draft.remark = ''
  draft.lines = []
}

function addDraftLine(itemId = null) {
  const targetId = itemId ?? items.value[0]?.itemId
  if (!targetId) return
  const key = `line-${Date.now()}-${draft.lines.length}`
  draft.lines.push({
    _key: key,
    itemId: targetId,
    batchNo: makeBatchNo(),
    purchaseQty: 100,
    receivedQty: 0,
    currentQty: 0,
    lineStatus: 'PENDING_RECEIPT'
  })
  ensureAlloc(key, targetId, 0)
}

function ensureAlloc(key, itemId, qty) {
  if (!lineAlloc[key]) {
    const rec = recommendLocations(itemId)[0]
    lineAlloc[key] = {
      locationId: rec?.locationId ?? null,
      putawayQty: qty
    }
  }
}

async function loadMasterData() {
  const [supplierList, itemList, warehouseList, zoneList, batchList] = await Promise.all([
    apiGet('/master/supplier/list'),
    apiGet('/master/item/list'),
    apiGet('/warehouse/list'),
    apiGet('/warehouse/zone/list'),
    apiGet('/master/batch/list').catch(() => [])
  ])
  suppliers.value = Array.isArray(supplierList) ? supplierList : []
  items.value = Array.isArray(itemList) ? itemList.filter((i) => i.status !== 'DISABLED') : []
  warehouses.value = Array.isArray(warehouseList) ? warehouseList.filter((w) => w.status !== 'DISABLED') : []
  zones.value = Array.isArray(zoneList) ? zoneList : []
  batches.value = Array.isArray(batchList) ? batchList : []
  resetDraft()
}

async function loadLocations(warehouseId, zoneId = null) {
  if (!warehouseId) {
    locations.value = []
    return
  }
  try {
    const list = await apiGet(`/warehouse/location/available?warehouseId=${warehouseId}`)
    const rows = Array.isArray(list) ? list : []
    locations.value = zoneId ? rows.filter((loc) => loc.zoneId === zoneId) : rows
  } catch {
    locations.value = []
  }
}

async function loadReceipts() {
  loading.value = true
  pageError.value = ''
  try {
    const all = await apiGet('/receipt/list')
    receipts.value = Array.isArray(all) ? all : []
    if (!receipts.value.length) {
      selectedReceiptId.value = 'new'
      activeDetail.value = null
      pageMessage.value = ''
      return
    }
    if (selectedReceiptId.value === 'new' || !receipts.value.some((r) => r.receiptId === selectedReceiptId.value)) {
      const preferred = receipts.value.find((r) => (r.receiptNo || '').trim() === DEFAULT_RECEIPT_NO)
      selectedReceiptId.value = (preferred || receipts.value[0]).receiptId
    }
    await loadActiveDetail()
  } catch (error) {
    receipts.value = []
    pageError.value = error.message || '加载收货单失败'
  } finally {
    loading.value = false
  }
}

async function loadActiveDetail() {
  if (isNewMode.value) {
    activeDetail.value = null
    selectedLineIds.value = new Set()
    activeLineKey.value = null
    return
  }
  const id = Number(selectedReceiptId.value)
  if (!id) return
  try {
    activeDetail.value = await apiGet(`/receipt/detail?receiptId=${id}`)
    const order = activeDetail.value?.order
    const lines = activeDetail.value?.lines ?? []
    if (order?.receiptId) {
      const derivedStatus = deriveReceiptStatus(order.receiptStatus, lines)
      const idx = receipts.value.findIndex((r) => r.receiptId === order.receiptId)
      if (idx >= 0) {
        receipts.value[idx] = { ...receipts.value[idx], ...order, receiptStatus: derivedStatus }
      }
    }
    await loadLocations(order?.warehouseId)
    lines.forEach((line) => {
      const key = String(line.receiptLineId)
      ensureAlloc(key, line.itemId, Number(line.receivedQty ?? 0))
    })
    if (order?.receiptStatus === 'PENDING_INSPECTION' || order?.receiptStatus === 'INSPECTING') {
      const pendingKeys = lines
        .filter((line) => line.lineStatus === 'PENDING_INSPECTION')
        .map((line) => String(line.receiptLineId))
      selectedLineIds.value = new Set(pendingKeys)
    } else {
      selectedLineIds.value = new Set()
    }
    activeLineKey.value = lines[0] ? String(lines[0].receiptLineId) : null
    syncPanelFromLine()
    await nextTick()
    syncPanelHeight()
  } catch (error) {
    pageError.value = error.message || '加载收货单详情失败'
    activeDetail.value = null
  }
}

function syncPanelFromLine() {
  const line = activeLine.value
  if (!line) {
    panelForm.locationId = null
    panelForm.putawayQty = 0
    return
  }
  const alloc = lineAlloc[line.key] || {}
  panelForm.locationId = alloc.locationId ?? line.recommend?.locationId ?? locations.value[0]?.locationId ?? null
  panelForm.putawayQty = alloc.putawayQty ?? line.receivedQty ?? 0
}

function selectLine(line) {
  activeLineKey.value = line.key
  syncPanelFromLine()
}

function toggleLine(key) {
  const next = new Set(selectedLineIds.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  selectedLineIds.value = next
}

function toggleAll(checked) {
  if (!checked) {
    selectedLineIds.value = new Set()
    return
  }
  selectedLineIds.value = new Set(filteredLines.value.map((l) => l.key))
}

function setLineLocation(key, locationId) {
  if (!lineAlloc[key]) lineAlloc[key] = { locationId: null, putawayQty: 0 }
  lineAlloc[key].locationId = locationId
  if (activeLineKey.value === key) panelForm.locationId = locationId
}

function setLinePutawayQty(key, qty) {
  if (!lineAlloc[key]) lineAlloc[key] = { locationId: null, putawayQty: 0 }
  lineAlloc[key].putawayQty = qty
  if (activeLineKey.value === key) panelForm.putawayQty = qty
}

function pickRecommend(loc) {
  if (!activeLine.value) return
  panelForm.locationId = loc.locationId
  setLineLocation(activeLine.value.key, loc.locationId)
}

function applyAllocToLine() {
  if (!activeLine.value) return
  setLineLocation(activeLine.value.key, panelForm.locationId)
  setLinePutawayQty(activeLine.value.key, panelForm.putawayQty)
  pageMessage.value = `已为「${activeLine.value.itemName}」分配库位 ${locationLabel(panelForm.locationId)}`
  pageError.value = ''
}

function batchAllocate() {
  if (!selectedLineIds.value.size) return
  const firstLoc = locations.value[0]?.locationId
  if (!firstLoc) {
    pageError.value = '暂无可用库位'
    return
  }
  selectedLineIds.value.forEach((key) => {
    const line = enrichedLines.value.find((l) => l.key === key)
    if (!line) return
    const rec = line.recommend?.locationId ?? firstLoc
    setLineLocation(key, rec)
    setLinePutawayQty(key, line.receivedQty)
  })
  pageMessage.value = `已批量分配 ${selectedLineIds.value.size} 行库位`
}

function adjustPanelQty(delta) {
  const max = activeLine.value?.receivedQty ?? 0
  const next = Math.max(0, Math.min(max, Number(panelForm.putawayQty || 0) + delta))
  panelForm.putawayQty = next
  if (activeLine.value) setLinePutawayQty(activeLine.value.key, next)
}

function setPanelMax() {
  const max = activeLine.value?.receivedQty ?? 0
  panelForm.putawayQty = max
  if (activeLine.value) setLinePutawayQty(activeLine.value.key, max)
}

function saveDraftLocal() {
  localStorage.setItem(
    'wms-receiving-draft',
    JSON.stringify({ draft: { ...draft, lines: draft.lines.map((l) => ({ ...l })) }, savedAt: new Date().toISOString() })
  )
  pageMessage.value = '草稿已保存到本地'
  pageError.value = ''
}

function restoreDraftLocal() {
  try {
    const raw = localStorage.getItem('wms-receiving-draft')
    if (!raw) return
    const parsed = JSON.parse(raw)
    if (!parsed?.draft) return
    Object.assign(draft, parsed.draft)
    if (!Array.isArray(draft.lines)) draft.lines = []
  } catch {
    /* ignore */
  }
}

async function submitReceiptInspection(receiptId, receiptLineIds = null) {
  const body = {
    receiptId: Number(receiptId),
    submittedBy: userId.value ?? 1
  }
  if (Array.isArray(receiptLineIds) && receiptLineIds.length) {
    body.receiptLineIds = receiptLineIds.map(Number)
  }
  return apiPost('/receipt/submit-inspection', body)
}

async function submitSelectedForInspection() {
  const order = activeOrder.value
  if (!order || !canSubmitInspection.value) {
    pageError.value = '没有可提交质检的物料行'
    return
  }
  submittingInspection.value = true
  pageError.value = ''
  pageMessage.value = ''
  try {
    const pendingLines = enrichedLines.value.filter((line) => line.rawLineStatus === 'PENDING_INSPECTION')
    const targetLines = selectedPendingLines.value.length
      ? selectedPendingLines.value
      : pendingLines
    const result = await submitReceiptInspection(
      order.receiptId,
      targetLines.map((line) => line.receiptLineId)
    )
    pageMessage.value =
      result.message ||
      `已提交 ${result.submittedLineCount} 行待检物料，质检单号 ${result.inspectionNo || ''}`
    selectedLineIds.value = new Set()
    await loadReceipts()
    await loadActiveDetail()
  } catch (error) {
    pageError.value = error.message || '提交质检失败'
  } finally {
    submittingInspection.value = false
  }
}

async function submitReceipt() {
  const target = resolveReceiptTarget(draft.receiptTargetKey)
  if (!draft.supplierId || !target?.warehouseId) {
    pageError.value = '请选择供应商和仓库/库区'
    return
  }
  const lines = draft.lines
    .map((line) => {
      const qty = Number(lineAlloc[line._key]?.putawayQty ?? line.currentQty ?? line.receivedQty ?? 0)
      return {
        itemId: Number(line.itemId),
        batchNo: (line.batchNo || makeBatchNo()).trim(),
        receivedQty: qty > 0 ? qty : Number(line.purchaseQty || 0)
      }
    })
    .filter((l) => l.receivedQty > 0)
  if (!lines.length) {
    pageError.value = '请至少填写一行收货数量（上架数）'
    return
  }

  saving.value = true
  pageError.value = ''
  pageMessage.value = ''
  const now = new Date()
  const arrivedAt = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
  try {
    const created = await apiPost('/receipt/create', {
      supplierId: Number(draft.supplierId),
      warehouseId: Number(target.warehouseId),
      sourceSystemId: 1,
      erpPoNo: (draft.erpPoNo || makePoNo()).trim(),
      arrivedAt,
      receivedBy: userId.value ?? 1,
      remark: buildReceiptRemark(draft.remark, target),
      lines
    })
    const qcResult = await submitReceiptInspection(created.receiptId)
    pageMessage.value =
      qcResult.message || `收货单 ${created.receiptNo || created.receiptId} 已创建并提交质检`
    localStorage.removeItem('wms-receiving-draft')
    selectedReceiptId.value = created.receiptId
    resetDraft()
    await loadReceipts()
  } catch (error) {
    pageError.value = error.message || '创建收货单失败'
  } finally {
    saving.value = false
  }
}

async function confirmPutaway() {
  const order = activeOrder.value
  if (!order) return
  const putawayLines = enrichedLines.value.filter((l) => l.uiStatus === 'PENDING_PUTAWAY')
  if (!putawayLines.length) {
    pageError.value = '没有可上架的质检合格物料'
    return
  }
  if (!canConfirmPutaway.value) {
    pageError.value = '请先为质检合格物料分配目标库位'
    return
  }
  const missing = putawayLines.filter((l) => !l.locationId)
  if (missing.length) {
    pageError.value = `还有 ${missing.length} 行未分配目标库位`
    return
  }

  puttingAway.value = true
  pageError.value = ''
  pageMessage.value = ''
  try {
    const inbound = await apiPost(
      `/inbound/create?receiptId=${order.receiptId}&warehouseId=${order.warehouseId}&operatedBy=${userId.value ?? 1}`,
      {}
    )
    const confirmBody = {
      inboundId: inbound.inboundId,
      warehouseId: order.warehouseId,
      operatedBy: userId.value ?? 1,
      lines: putawayLines.map((line) => ({
        itemId: line.itemId,
        batchId: line.batchId,
        locationId: Number(line.locationId),
        inboundQty: Number(line.putawayQty || line.receivedQty)
      }))
    }
    const completed = await apiPost('/inbound/confirm', confirmBody)
    pageMessage.value = `入库单 ${completed.inboundNo || completed.inboundId} 已确认上架`
    await loadReceipts()
  } catch (error) {
    pageError.value = error.message || '确认上架失败'
  } finally {
    puttingAway.value = false
  }
}

function confirmScanReceive() {
  const code = scanCode.value.trim()
  if (!code) {
    pageError.value = '请输入物料编码'
    return
  }
  const item = items.value.find((row) => row.itemCode === code || String(row.itemId) === code)
  if (!item) {
    pageError.value = `未找到物料：${code}`
    return
  }
  if (!isNewMode.value) {
    selectedReceiptId.value = 'new'
  }
  const exist = draft.lines.find((line) => line.itemId === item.itemId)
  if (exist) {
    exist.receivedQty = Number(exist.receivedQty || 0) + 1
    exist.currentQty = Number(exist.currentQty || 0) + 1
    setLinePutawayQty(exist._key, Number(exist.currentQty))
  } else {
    addDraftLine(item.itemId)
    const last = draft.lines[draft.lines.length - 1]
    last.receivedQty = 1
    last.currentQty = 1
    last.purchaseQty = 100
    setLinePutawayQty(last._key, 1)
  }
  scanDialogOpen.value = false
  pageMessage.value = `已扫码收货：${item.itemName}`
  pageError.value = ''
}

function startNewReceipt() {
  selectedReceiptId.value = 'new'
  restoreDraftLocal()
  if (!draft.receiptTargetKey) applyReceiptTarget(draft, defaultReceiptTargetKey())
  if (!draft.lines.length && items.value.length) addDraftLine()
  const target = resolveReceiptTarget(draft.receiptTargetKey)
  loadLocations(draft.warehouseId, target?.zoneId ?? null)
}

function resetAddForm() {
  addForm.erpPoNo = makePoNo()
  addForm.supplierId = suppliers.value[0]?.supplierId ?? null
  applyReceiptTarget(addForm, defaultReceiptTargetKey())
  addForm.remark = ''
  addForm.lines = [
    {
      _key: `add-${Date.now()}`,
      itemId: items.value[0]?.itemId ?? null,
      batchNo: makeBatchNo(),
      receivedQty: 100
    }
  ]
  addFormError.value = ''
}

function openAddReceiptDialog() {
  resetAddForm()
  createDialogOpen.value = true
}

function addFormLine() {
  addForm.lines.push({
    _key: `add-${Date.now()}-${addForm.lines.length}`,
    itemId: items.value[0]?.itemId ?? null,
    batchNo: makeBatchNo(),
    receivedQty: 1
  })
}

function removeFormLine(key) {
  if (addForm.lines.length <= 1) {
    addFormError.value = '至少保留一行物料'
    return
  }
  addForm.lines = addForm.lines.filter((line) => line._key !== key)
}

async function submitAddReceipt() {
  addFormError.value = ''
  const target = resolveReceiptTarget(addForm.receiptTargetKey)
  if (!addForm.supplierId || !target?.warehouseId) {
    addFormError.value = '请选择供应商和仓库/库区'
    return
  }
  if (!addForm.erpPoNo?.trim()) {
    addFormError.value = '请填写采购单号'
    return
  }
  const lines = addForm.lines
    .filter((line) => line.itemId && Number(line.receivedQty) > 0)
    .map((line) => ({
      itemId: Number(line.itemId),
      batchNo: (line.batchNo || makeBatchNo()).trim(),
      receivedQty: Number(line.receivedQty)
    }))
  if (!lines.length) {
    addFormError.value = '请至少填写一行有效物料和收货数量'
    return
  }

  saving.value = true
  pageError.value = ''
  pageMessage.value = ''
  const now = new Date()
  const arrivedAt = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
  try {
    const created = await apiPost('/receipt/create', {
      supplierId: Number(addForm.supplierId),
      warehouseId: Number(target.warehouseId),
      sourceSystemId: 1,
      erpPoNo: addForm.erpPoNo.trim(),
      arrivedAt,
      receivedBy: userId.value ?? 1,
      remark: buildReceiptRemark(addForm.remark, target),
      lines
    })
    const qcResult = await submitReceiptInspection(created.receiptId)
    createDialogOpen.value = false
    pageMessage.value =
      qcResult.message || `收货单 ${created.receiptNo || created.receiptId} 已写入数据库，并提交质检`
    selectedReceiptId.value = created.receiptId
    await loadReceipts()
    await loadActiveDetail()
  } catch (error) {
    addFormError.value = error.message || '创建收货单失败'
    pageError.value = error.message || '创建收货单失败'
  } finally {
    saving.value = false
  }
}

function confirmInfoDialog() {
  editInfoOpen.value = false
  if (isNewMode.value) {
    const target = resolveReceiptTarget(draft.receiptTargetKey)
    loadLocations(draft.warehouseId, target?.zoneId ?? null)
  }
}

watch(selectedReceiptId, async (id) => {
  pageError.value = ''
  if (id === 'new') {
    activeDetail.value = null
    restoreDraftLocal()
    if (!draft.receiptTargetKey) applyReceiptTarget(draft, defaultReceiptTargetKey())
    if (!draft.lines.length && items.value.length) addDraftLine()
    const target = resolveReceiptTarget(draft.receiptTargetKey)
    await loadLocations(draft.warehouseId, target?.zoneId ?? null)
    activeLineKey.value = draft.lines[0]?._key || null
    syncPanelFromLine()
  } else {
    await loadActiveDetail()
  }
})

watch(
  () => draft.receiptTargetKey,
  (key) => {
    applyReceiptTarget(draft, key)
    if (isNewMode.value) {
      const target = resolveReceiptTarget(key)
      loadLocations(target?.warehouseId, target?.zoneId ?? null)
    }
  }
)

watch(activeLineKey, () => syncPanelFromLine())

function syncPanelHeight() {
  if (panelCollapsed.value || isStackedLayout()) {
    panelHeightPx.value = null
    return
  }
  const el = mainCardRef.value
  if (!el) return
  requestAnimationFrame(() => {
    const h = Math.round(el.getBoundingClientRect().height)
    if (h > 0) panelHeightPx.value = `${h}px`
  })
}

function setupPanelHeightSync() {
  mainResizeObserver?.disconnect()
  mainResizeObserver = null
  if (!mainCardRef.value || isStackedLayout()) return
  mainResizeObserver = new ResizeObserver(() => syncPanelHeight())
  mainResizeObserver.observe(mainCardRef.value)
  syncPanelHeight()
}

watch([filteredLines, lineFilter, panelCollapsed, selectedReceiptId, loading, locTab, activeLineKey], async () => {
  await nextTick()
  syncPanelHeight()
})

watch(panelCollapsed, async (collapsed) => {
  if (!collapsed) {
    await nextTick()
    setupPanelHeightSync()
  } else {
    panelHeightPx.value = null
  }
})

onMounted(async () => {
  pageError.value = ''
  try {
    await loadMasterData()
  } catch (error) {
    pageError.value = error.message || '加载主数据失败'
  }
  await loadReceipts()
  if (isNewMode.value && !draft.lines.length && items.value.length) {
    addDraftLine()
    activeLineKey.value = draft.lines[0]?._key
    const target = resolveReceiptTarget(draft.receiptTargetKey)
    await loadLocations(draft.warehouseId, target?.zoneId ?? null)
    syncPanelFromLine()
  }
  await nextTick()
  setupPanelHeightSync()
  setTimeout(syncPanelHeight, 150)
  setTimeout(syncPanelHeight, 600)
  window.addEventListener('resize', () => {
    if (isStackedLayout()) {
      panelHeightPx.value = null
      mainResizeObserver?.disconnect()
      mainResizeObserver = null
      return
    }
    setupPanelHeightSync()
  })
})

onUnmounted(() => {
  mainResizeObserver?.disconnect()
  window.removeEventListener('resize', syncPanelHeight)
})
</script>

<template>
  <section class="rcv">
    <!-- 顶栏 -->
    <header class="rcv-header">
      <div class="rcv-header-left">
        <h1>收货上架</h1>
      </div>
      <div class="inv-strip">
        <div class="strip-item"><strong class="num">{{ lineStats.all }}</strong><span class="lbl">全部</span></div>
        <div class="strip-item"><strong class="num blue">{{ lineStats.pendingInspection }}</strong><span class="lbl">待质检</span></div>
        <div class="strip-item"><strong class="num orange">{{ lineStats.pendingAlloc }}</strong><span class="lbl">待分配</span></div>
        <div class="strip-item"><strong class="num green">{{ lineStats.pendingPutaway }}</strong><span class="lbl">待上架</span></div>
        <div class="strip-item"><strong class="num gray">{{ lineStats.stored }}</strong><span class="lbl">已上架</span></div>
      </div>
    </header>

    <div class="rcv-toolbar">
      <div class="rcv-toolbar-main">
        <label class="rcv-select">
          <span>当前收货单</span>
          <select v-model="selectedReceiptId">
            <option value="new">+ 新建收货单</option>
            <option v-for="row in filteredReceipts" :key="row.receiptId" :value="row.receiptId">
              {{ row.receiptNo || row.receiptId }} · {{ statusText(row.receiptStatus) }}
            </option>
          </select>
        </label>
        <label class="rcv-search">
          <span aria-hidden="true">⌕</span>
          <input
            v-model="receiptKeyword"
            type="search"
            placeholder="筛选单号、采购单、供应商、仓库"
          />
        </label>
        <div class="rcv-receipt-filters">
          <button
            v-for="f in RECEIPT_FILTERS"
            :key="f.key || 'all'"
            type="button"
            :class="{ active: receiptStatusFilter === f.key }"
            @click="receiptStatusFilter = f.key"
          >
            {{ f.label }}
            <em>{{ receiptFilterCounts[f.key] ?? 0 }}</em>
          </button>
        </div>
        <span v-if="receiptFilterActive" class="rcv-filter-hint">
          匹配 {{ filteredReceipts.length }} / {{ receipts.length }} 单
        </span>
      </div>
      <div class="toolbar-btns">
        <button type="button" class="tb-btn primary" @click="openAddReceiptDialog">+ 添加收货单</button>
      </div>
    </div>

    <p v-if="pageMessage" class="data-hint ok-hint">{{ pageMessage }}</p>
    <p v-if="pageError" class="data-hint danger">{{ pageError }}</p>
    <p v-else-if="loading" class="data-hint">正在加载收货单...</p>

    <!-- 步骤条 -->
    <nav class="rcv-steps" aria-label="收货流程">
      <div
        v-for="(step, i) in flowSteps"
        :key="step.title"
        class="rcv-step"
        :class="{ active: i === activeStep, done: i < activeStep }"
      >
        <span class="dot">{{ i + 1 }}</span>
        <div class="txt">
          <strong>{{ step.title }}</strong>
          <small>{{ step.desc }}</small>
        </div>
        <i v-if="i < flowSteps.length - 1" class="rail" />
      </div>
    </nav>

    <!-- 摘要条 -->
    <div class="rcv-summary">
      <div class="sum-item">
        <span>收货单号</span>
        <strong>{{ isNewMode ? '待生成' : (activeOrder?.receiptNo || '-') }}</strong>
      </div>
      <div class="sum-item">
        <span>采购单号</span>
        <strong v-if="!isNewMode">{{ activeOrder?.erpPoNo || '-' }}</strong>
        <input v-else v-model="draft.erpPoNo" class="sum-input" />
      </div>
      <div class="sum-item">
        <span>供应商</span>
        <strong v-if="!isNewMode">{{ supplierLabel(activeOrder?.supplierId) }}</strong>
        <select v-else v-model.number="draft.supplierId" class="sum-input">
          <option v-for="s in suppliers" :key="s.supplierId" :value="s.supplierId">{{ s.supplierName }}</option>
        </select>
      </div>
      <div class="sum-item">
        <span>仓库</span>
        <strong v-if="!isNewMode">{{ warehouseLabel(activeOrder?.warehouseId) }}</strong>
        <select v-else v-model="draft.receiptTargetKey" class="sum-input">
          <option v-for="opt in receiptWarehouseOptions" :key="opt.key" :value="opt.key">{{ opt.label }}</option>
        </select>
      </div>
      <div class="sum-item">
        <span>到货时间</span>
        <strong>{{ isNewMode ? formatDate(new Date().toISOString()) : formatDate(activeOrder?.arrivedAt) }}</strong>
      </div>
      <div class="sum-item status">
        <span>状态</span>
        <i :class="['tag', orderStatusLabel.cls]">{{ orderStatusLabel.text }}</i>
      </div>
    </div>

    <p v-if="hasQcFailure" class="data-hint danger">
      本收货单有 {{ lineStats.qcFail }} 项物料质检不合格，质检员已登记质量异常，相关批次禁止上架。请等待质检员完成异常处置。
    </p>

    <!-- 主体 -->
    <div class="rcv-body" :class="{ 'panel-collapsed': panelCollapsed }">
      <!-- 中栏表格 -->
      <main ref="mainCardRef" class="rcv-main">
        <div class="table-toolbar">
          <div class="tabs">
            <button
              v-for="f in LINE_FILTERS"
              :key="f.key || 'all'"
              type="button"
              :class="{ active: lineFilter === f.key }"
              @click="lineFilter = f.key"
            >
              {{ f.label }}
              <em>{{ filterCounts[f.key] ?? 0 }}</em>
            </button>
          </div>
          <div class="toolbar-acts">
            <button v-if="isNewMode" type="button" class="btn ghost sm" @click="addDraftLine()">+ 添加物料</button>
            <button type="button" class="btn ghost sm">筛选</button>
          </div>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th class="c-check">
                  <input
                    type="checkbox"
                    :checked="filteredLines.length > 0 && filteredLines.every((l) => selectedLineIds.has(l.key))"
                    @change="toggleAll($event.target.checked)"
                  />
                </th>
                <th>物料信息</th>
                <th>批次/效期</th>
                <th>单位</th>
                <th>采购数</th>
                <th>已收数</th>
                <th>质检状态</th>
                <th>推荐库位</th>
                <th>目标库位</th>
                <th>上架数</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="line in filteredLines"
                :key="line.key"
                :class="{ selected: activeLineKey === line.key, 'row-failed': line.uiStatus === 'FAILED' }"
                @click="selectLine(line)"
              >
                <td class="c-check" @click.stop>
                  <input
                    type="checkbox"
                    :checked="selectedLineIds.has(line.key)"
                    :disabled="!isLineSelectable(line)"
                    @change="toggleLine(line.key)"
                  />
                </td>
                <td>
                  <div class="item-cell">
                    <span class="material-thumb">
                      <i :class="['prio', line.priority.cls]">
                        {{ line.priority.text }}
                      </i>

                      <img
                        v-if="line.itemCode"
                        :key="line.itemCode"
                        :src="materialImageUrl(line.itemCode)"
                        :alt="line.itemName || line.itemCode"
                        class="material-image"
                        loading="lazy"
                        @load="showMaterialImage"
                        @error="hideBrokenMaterialImage"
                      />
                    </span>

                    <div class="material-text">
                      <strong>{{ line.itemName }}</strong>
                      <small>{{ line.itemCode }}</small>
                    </div>
                  </div>
                </td>
                <td>
                  <div class="batch-cell">
                    <strong>{{ line.batchNo }}</strong>
                    <small>{{ line.expireDate }}</small>
                  </div>
                </td>
                <td>{{ line.unit }}</td>
                <td>{{ line.purchaseQty }}</td>
                <td>{{ line.receivedQty }}</td>
                <td><i :class="['qc-tag', line.qc.cls]">{{ line.qc.text }}</i></td>
                <td>
                  <div v-if="line.recommend" class="rec-cell">
                    <strong>{{ line.recommend.locationCode }}</strong>
                    <small>余量 {{ line.recommend.remain }}%</small>
                  </div>
                  <span v-else class="muted">-</span>
                </td>
                <td @click.stop>
                  <select
                    class="cell-select"
                    :value="line.locationId ?? ''"
                    :disabled="line.uiStatus === 'FAILED'"
                    @change="setLineLocation(line.key, Number($event.target.value) || null)"
                  >
                    <option value="">请选择</option>
                    <option v-for="loc in locations" :key="loc.locationId" :value="loc.locationId">
                      {{ loc.locationCode }}
                    </option>
                  </select>
                </td>
                <td @click.stop>
                  <input
                    class="cell-qty"
                    type="number"
                    min="0"
                    step="any"
                    :value="line.putawayQty"
                    :disabled="line.uiStatus === 'FAILED'"
                    @input="setLinePutawayQty(line.key, Number($event.target.value))"
                  />
                </td>
                <td>
                  <button v-if="line.uiStatus === 'FAILED'" type="button" class="link muted" disabled>禁止上架</button>
                  <button v-else type="button" class="link" @click.stop="selectLine(line)">详情</button>
                </td>
              </tr>
              <tr v-if="!filteredLines.length">
                <td colspan="11" class="empty">暂无物料明细</td>
              </tr>
            </tbody>
            <tfoot v-if="filteredLines.length">
              <tr>
                <td colspan="4">合计</td>
                <td>{{ totals.purchase }}</td>
                <td>{{ totals.received }}</td>
                <td colspan="3"></td>
                <td>{{ totals.putaway }}</td>
                <td></td>
              </tr>
            </tfoot>
          </table>
        </div>

        <div class="qc-footer">
          <span>质检结果汇总</span>
          <span class="qc-pill ok"><i class="ball ok" />合格 {{ lineStats.qcOk }}</span>
          <span class="qc-pill pending"><i class="ball pending" />待检 {{ lineStats.qcPending }}</span>
          <span class="qc-pill fail"><i class="ball fail" />不合格 {{ lineStats.qcFail }}</span>
        </div>
      </main>

      <!-- 右栏分配面板 -->
      <aside
        v-show="!panelCollapsed"
        class="rcv-panel"
        :style="panelStyle"
      >
        <header class="panel-head">
          <h3>分配库位</h3>
          <button type="button" class="icon-btn" title="收起" @click="panelCollapsed = true">‹</button>
        </header>

        <div class="panel-scroll">
          <div v-if="activeLine" class="panel-item">
            <i :class="['prio', activeLine.priority.cls]">{{ activeLine.priority.text }}</i>
            <div>
              <strong>{{ activeLine.itemName }}</strong>
              <small>{{ activeLine.itemCode }} · 已收 {{ activeLine.receivedQty }} {{ activeLine.unit }}</small>
            </div>
          </div>
          <div v-else class="panel-empty">请选择一行物料</div>

          <div class="panel-tabs">
            <button type="button" :class="{ active: locTab === 'recommend' }" @click="locTab = 'recommend'">推荐库位</button>
            <button type="button" :class="{ active: locTab === 'manual' }" @click="locTab = 'manual'">手动选择</button>
            <button type="button" :class="{ active: locTab === 'staging' }" @click="locTab = 'staging'">暂存区</button>
          </div>

          <div v-if="locTab === 'recommend'" class="rec-list">
            <button
              v-for="loc in recommendedList"
              :key="loc.locationId"
              type="button"
              class="rec-card"
              :class="{ active: panelForm.locationId === loc.locationId }"
              @click="pickRecommend(loc)"
            >
              <div class="rec-top">
                <strong>{{ loc.locationCode }}</strong>
                <span>余量 {{ loc.remain }}%</span>
              </div>
              <div class="rec-bar"><i :style="{ width: loc.match + '%' }" /></div>
              <small>{{ loc.match }}% 匹配</small>
            </button>
            <p v-if="!recommendedList.length" class="panel-empty">暂无可用库位，请先维护仓库库位</p>
          </div>

          <div v-else-if="locTab === 'manual'" class="manual-box">
            <label>
              <span>选择库位</span>
              <select v-model.number="panelForm.locationId">
                <option :value="null">请选择</option>
                <option v-for="loc in locations" :key="loc.locationId" :value="loc.locationId">
                  {{ loc.locationCode }}{{ loc.locationName ? ` · ${loc.locationName}` : '' }}
                </option>
              </select>
            </label>
          </div>

          <div v-else class="manual-box">
            <p class="panel-empty">暂存区功能：先将物料放入暂存库位，稍后再正式上架。</p>
            <label>
              <span>暂存库位</span>
              <select v-model.number="panelForm.locationId">
                <option :value="null">请选择</option>
                <option v-for="loc in locations" :key="loc.locationId" :value="loc.locationId">
                  {{ loc.locationCode }}
                </option>
              </select>
            </label>
          </div>

          <label class="panel-field">
            <span>目标库位</span>
            <select v-model.number="panelForm.locationId" @change="activeLine && setLineLocation(activeLine.key, panelForm.locationId)">
              <option :value="null">请选择</option>
              <option v-for="loc in locations" :key="loc.locationId" :value="loc.locationId">
                {{ loc.locationCode }}{{ loc.locationName ? ` · ${loc.locationName}` : '' }}
              </option>
            </select>
          </label>

          <label class="panel-field">
            <span>上架数量 <em>最大 {{ activeLine?.receivedQty ?? 0 }}</em></span>
            <div class="stepper">
              <button type="button" @click="adjustPanelQty(-1)">−</button>
              <input
                v-model.number="panelForm.putawayQty"
                type="number"
                min="0"
                @change="activeLine && setLinePutawayQty(activeLine.key, panelForm.putawayQty)"
              />
              <button type="button" @click="adjustPanelQty(1)">+</button>
              <button type="button" class="max-btn" @click="setPanelMax">最大</button>
            </div>
          </label>

          <div class="panel-acts">
            <button type="button" class="btn ghost block" :disabled="!activeLine" @click="applyAllocToLine">分配库位</button>
            <button
              type="button"
              class="btn primary block"
              :disabled="!canConfirmPutaway || puttingAway"
              @click="confirmPutaway"
            >
              {{ puttingAway ? '上架中...' : '确认上架' }}
            </button>
          </div>
        </div>
      </aside>

      <button v-if="panelCollapsed" type="button" class="panel-expand" @click="panelCollapsed = false">分配库位 ›</button>
    </div>

    <!-- 底栏 -->
    <footer class="rcv-footer">
      <div class="foot-left">
        <button
          v-if="!isNewMode && lineStats.pendingInspection > 0"
          type="button"
          class="btn outline"
          :disabled="submittingInspection || !canSubmitInspection"
          @click="submitSelectedForInspection"
        >
          {{ submittingInspection ? '提交中...' : '提交质检' }}
        </button>
        <button
          v-else-if="isNewMode"
          type="button"
          class="btn outline"
          :disabled="saving"
          @click="submitReceipt"
        >
          {{ saving ? '提交中...' : '提交质检' }}
        </button>
      </div>
      <div class="foot-right">
        <button type="button" class="btn ghost" :disabled="!canBatchAlloc" @click="batchAllocate">批量分配库位</button>
        <button
          type="button"
          class="btn primary lg"
          :disabled="puttingAway || (!canConfirmPutaway && !isNewMode)"
          @click="isNewMode ? submitReceipt() : confirmPutaway()"
        >
          {{ isNewMode ? (saving ? '提交中...' : '确认收货完成') : (puttingAway ? '处理中...' : '确认收货完成') }}
        </button>
      </div>
    </footer>

    <!-- 扫码 -->
    <div v-if="scanDialogOpen" class="mask" @click.self="scanDialogOpen = false">
      <div class="dialog">
        <header><h3>扫码收货</h3><p>输入或扫描物料编码</p></header>
        <input v-model="scanCode" class="dlg-input" type="text" placeholder="物料编码 / 条码" @keyup.enter="confirmScanReceive" />
        <footer>
          <button type="button" class="btn ghost" @click="scanDialogOpen = false">取消</button>
          <button type="button" class="btn primary" @click="confirmScanReceive">确认</button>
        </footer>
      </div>
    </div>

    <!-- 添加收货单 -->
    <div v-if="createDialogOpen" class="mask" @click.self="createDialogOpen = false">
      <div class="dialog add-dialog" role="dialog" aria-modal="true">
        <header>
          <h3>添加收货单</h3>
          <p>填写收货信息与物料明细，确认后写入数据库并进入待质检</p>
        </header>

        <p v-if="addFormError" class="dlg-error">{{ addFormError }}</p>

        <div class="dlg-grid">
          <label>
            <span>采购单号 <em>*</em></span>
            <input v-model="addForm.erpPoNo" type="text" placeholder="如 PO-20260713-001" />
          </label>
          <label>
            <span>供应商 <em>*</em></span>
            <select v-model.number="addForm.supplierId">
              <option :value="null" disabled>请选择供应商</option>
              <option v-for="s in suppliers" :key="s.supplierId" :value="s.supplierId">
                {{ s.supplierName }}（{{ s.supplierCode }}）
              </option>
            </select>
          </label>
          <label>
            <span>收货仓库/库区 <em>*</em></span>
            <select v-model="addForm.receiptTargetKey">
              <option value="" disabled>请选择仓库/库区</option>
              <option v-for="opt in receiptWarehouseOptions" :key="opt.key" :value="opt.key">
                {{ opt.label }}
              </option>
            </select>
          </label>
          <label>
            <span>备注</span>
            <input v-model="addForm.remark" type="text" placeholder="选填" />
          </label>
        </div>

        <div class="add-lines">
          <div class="add-lines-head">
            <strong>物料明细</strong>
            <button type="button" class="btn ghost sm" @click="addFormLine">+ 添加行</button>
          </div>
          <div class="add-line-row head">
            <span>物料</span>
            <span>批次号</span>
            <span>收货数量</span>
            <span></span>
          </div>
          <div v-for="line in addForm.lines" :key="line._key" class="add-line-row">
            <select v-model.number="line.itemId">
              <option :value="null" disabled>请选择物料</option>
              <option v-for="item in items" :key="item.itemId" :value="item.itemId">
                {{ item.itemName }}（{{ item.itemCode }}）
              </option>
            </select>
            <input v-model="line.batchNo" type="text" placeholder="批次号" />
            <input v-model.number="line.receivedQty" type="number" min="0.001" step="any" />
            <button type="button" class="link" @click="removeFormLine(line._key)">删除</button>
          </div>
        </div>

        <footer>
          <button type="button" class="btn ghost sm" :disabled="saving" @click="createDialogOpen = false">取消</button>
          <button type="button" class="btn primary sm" :disabled="saving" @click="submitAddReceipt">
            {{ saving ? '提交中...' : '确认生成收货单' }}
          </button>
        </footer>
      </div>
    </div>

    <!-- 编辑收货信息（本地草稿字段） -->
    <div v-if="editInfoOpen" class="mask" @click.self="editInfoOpen = false">
      <div class="dialog wide">
        <header>
          <h3>编辑收货信息</h3>
          <p>完善送货与收货基础信息</p>
        </header>
        <div class="dlg-grid">
          <label><span>采购单号</span><input v-model="draft.erpPoNo" type="text" /></label>
          <label><span>送货单号</span><input v-model="draft.deliveryNo" type="text" /></label>
          <label>
            <span>供应商</span>
            <select v-model.number="draft.supplierId">
              <option v-for="s in suppliers" :key="s.supplierId" :value="s.supplierId">{{ s.supplierName }}</option>
            </select>
          </label>
          <label>
            <span>收货仓库/库区</span>
            <select v-model="draft.receiptTargetKey">
              <option v-for="opt in receiptWarehouseOptions" :key="opt.key" :value="opt.key">{{ opt.label }}</option>
            </select>
          </label>
          <label><span>送货人</span><input v-model="draft.deliveryPerson" type="text" /></label>
          <label><span>备注</span><input v-model="draft.remark" type="text" /></label>
        </div>
        <footer>
          <button type="button" class="btn ghost" @click="editInfoOpen = false">取消</button>
          <button type="button" class="btn primary" @click="confirmInfoDialog">确定</button>
        </footer>
      </div>
    </div>
  </section>
</template>

<style scoped>
.rcv {
  --green: #587766;
  --green-soft: #eef4f0;
  --border: #e2eae5;
  --text: #33473f;
  --muted: #7d8983;
  --radius: 8px;
  width: min(1680px, 100%);
  margin: 6px auto 24px;
  padding: 0 28px 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-family: inherit;
}

.rcv-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 6px;
}

.rcv-header h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(24px, 2.6vw, 32px);
  font-weight: 600;
  color: #2a3a33;
}

.rcv-header p {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--muted);
}

/* stat strip (aligned with 库存控制 / 补货管理) */
.inv-strip {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.strip-item {
  min-width: 64px;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 8px 14px;
  background: rgba(255, 255, 255, 0.78);
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

/* toolbar row (receipt select + actions) */
.rcv-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid var(--border);
  border-radius: 10px;
  flex-wrap: wrap;
}

.rcv-toolbar-main {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

.rcv-search {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: min(280px, 100%);
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid #cddbd3;
  border-radius: var(--radius);
  background: #fff;
}

.rcv-search:focus-within {
  border-color: #587766;
}

.rcv-search span {
  color: #7a8982;
  font-size: 13px;
}

.rcv-search input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 13px;
}

.rcv-receipt-filters {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.rcv-receipt-filters button {
  border: 1px solid #dfe6e1;
  border-radius: 999px;
  padding: 5px 10px;
  background: #fff;
  color: #567566;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  transition: all 0.12s;
}

.rcv-receipt-filters button em {
  font-style: normal;
  font-size: 11px;
  color: #8a9a92;
}

.rcv-receipt-filters button:hover {
  background: #eef4f0;
}

.rcv-receipt-filters button.active {
  background: #e8f2ec;
  border-color: #9ab8a8;
  color: #2f5a45;
}

.rcv-receipt-filters button.active em {
  color: #4a6a59;
}

.rcv-filter-hint {
  font-size: 12px;
  color: var(--muted);
  white-space: nowrap;
  align-self: center;
}

.toolbar-btns {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tb-btn {
  border: 1px solid #cddbd3;
  border-radius: 8px;
  padding: 7px 16px;
  background: #fff;
  color: #567566;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.12s;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tb-btn:hover { background: #eef4f0; }

.tb-btn.primary {
  background: var(--green);
  border-color: var(--green);
  color: #fff;
}

.tb-btn.primary:hover { background: #4a6a59; }

.rcv-select {
  display: grid;
  gap: 4px;
}

.rcv-select span {
  font-size: 11px;
  color: var(--muted);
}

.rcv-select select,
.sum-input,
.cell-select,
.cell-qty,
.dlg-input,
.dlg-grid input,
.dlg-grid select,
.panel-field select,
.manual-box select {
  border: 1px solid #cddbd3;
  border-radius: var(--radius);
  padding: 8px 10px;
  font: inherit;
  color: var(--text);
  background: #fff;
}

.rcv-select select {
  min-width: 220px;
}

/* steps */
.rcv-steps {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 0;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
}

.rcv-step {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.rcv-step .dot {
  width: 26px;
  height: 26px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  background: #e8edea;
  color: var(--muted);
  flex-shrink: 0;
}

.rcv-step .txt {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.rcv-step strong {
  font-size: 13px;
  color: #56655f;
}

.rcv-step small {
  font-size: 11px;
  color: #9aa8a1;
}

.rcv-step .rail {
  flex: 1;
  height: 2px;
  background: #dde5e0;
  margin: 0 6px;
  min-width: 12px;
}

.rcv-step.active .dot,
.rcv-step.done .dot {
  background: var(--green);
  color: #fff;
}

.rcv-step.active strong {
  color: var(--text);
}

.rcv-step.done .rail {
  background: #9eb8a8;
}

/* summary */
.rcv-summary {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
}

.sum-item {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.sum-item span {
  font-size: 11px;
  color: var(--muted);
}

.sum-item strong {
  font-size: 13px;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-style: normal;
  width: fit-content;
}

.tag.warn { background: #fdf0e8; color: #c98a2e; }
.tag.ok { background: #e0f0e6; color: #3b7a5a; }
.tag.info { background: #d6e6f0; color: #437a9e; }
.tag.fail { background: #fdecea; color: #c0392b; }

/* body — 左表右面板等高对齐 */
.rcv-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 12px;
  align-items: start;
}

.rcv-body.panel-collapsed {
  grid-template-columns: minmax(0, 1fr) 36px;
}

.rcv-side {
  display: grid;
  grid-template-rows: auto auto 1fr;
  gap: 12px;
  min-height: 0;
  height: 100%;
  align-self: stretch;
}

.side-card {
  border: 1px solid var(--border);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  padding: 12px 14px;
  min-width: 0;
}

.side-card.side-click {
  display: flex;
  flex-direction: column;
}

.side-card header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.side-card h3 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: 15px;
  font-weight: 600;
  color: #2a3a33;
}

.side-card dl {
  margin: 0;
  display: grid;
  gap: 8px;
}

.side-card dl div {
  display: grid;
  gap: 2px;
}

.side-card dt {
  font-size: 11px;
  color: var(--muted);
}

.side-card dd {
  margin: 0;
  font-size: 13px;
  color: var(--text);
  font-weight: 600;
}

.side-hint {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
}

.qc-summary {
  margin-top: 10px;
  display: grid;
  gap: 6px;
}

.qc-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #56655f;
}

.qc-row strong {
  margin-left: auto;
  color: var(--text);
}

.ball {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  display: inline-block;
}

.ball.ok { background: #3b7a5a; }
.ball.pending { background: #437a9e; }
.ball.fail { background: #c0392b; }

/* main table */
.rcv-main {
  border: 1px solid var(--border);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  min-width: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  align-self: start;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid #eef3f0;
  flex-wrap: wrap;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tabs button {
  border: 0;
  background: transparent;
  color: var(--muted);
  padding: 6px 10px;
  border-radius: var(--radius);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}

.tabs button em {
  font-style: normal;
  margin-left: 4px;
  color: #9aa8a1;
}

.tabs button.active {
  background: var(--green-soft);
  color: var(--green);
  font-weight: 600;
}

.toolbar-acts {
  display: flex;
  gap: 6px;
}

.table-wrap {
  overflow: auto;
  max-height: min(520px, 58vh);
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

th, td {
  padding: 16px 12px;
  border-bottom: 1px solid #ecf1ee;
  text-align: left;
  vertical-align: middle;
  white-space: nowrap;
}

th {
  background: #f0f5f2;
  color: #5f7268;
  font-size: 12px;
  font-weight: 600;
  position: sticky;
  top: 0;
  z-index: 1;
}

td {
  color: #3f554b;
}

tbody tr {
  cursor: pointer;
  height: 64px;
}

tbody tr:hover {
  background: #f6f9f7;
}

tbody tr.selected {
  background: #e6f1ea;
}

tbody tr.row-failed {
  background: #fff7f6;
}

tbody tr.row-failed:hover {
  background: #fdecea;
}

.c-check { width: 36px; }

.item-cell, .batch-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
}

.item-cell strong, .batch-cell strong {
  display: block;
  color: var(--text);
  font-size: 13px;
  line-height: 1.35;
}

.item-cell small, .batch-cell small, .rec-cell small {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.4;
  margin-top: 2px;
  display: block;
}

.prio {
  width: 26px;
  height: 26px;
  border-radius: var(--radius);
  display: grid;
  place-items: center;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
  flex-shrink: 0;
}

.prio.high { background: #e0f0e6; color: #3b7a5a; }
.prio.mid { background: #fdf0e8; color: #c98a2e; }
.prio.low { background: #eef4f0; color: #8b958f; }

.material-thumb {
  position: relative;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  overflow: hidden;
  border: 1px solid #e2e8e4;
  border-radius: 6px;
  background: #f7faf8;
}

.material-thumb .prio {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border-radius: 0;
  box-sizing: border-box;
}

.material-image {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  padding: 2px;
  background: #fff;
  box-sizing: border-box;
}

.material-text {
  min-width: 0;
}

.material-text strong,
.material-text small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qc-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-style: normal;
  font-size: 11px;
}

.qc-tag.ok { background: #e0f0e6; color: #3b7a5a; }
.qc-tag.pending { background: #d6e6f0; color: #437a9e; }
.qc-tag.fail { background: #fdecea; color: #c0392b; }
.qc-tag.muted { background: #eef4f0; color: #8b958f; }

.rec-cell {
  display: grid;
  gap: 1px;
}

.cell-select { min-width: 100px; padding: 5px 6px; }
.cell-qty { width: 72px; padding: 5px 6px; }

.muted { color: var(--muted); }
.empty { text-align: center; color: var(--muted); padding: 28px !important; }

tfoot td {
  background: #f0f5f2;
  font-weight: 600;
  color: var(--text);
}

.qc-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid #eef3f0;
  font-size: 12px;
  color: var(--muted);
}

.qc-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #56655f;
}

/* right panel */
.rcv-panel {
  border: 1px solid var(--border);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 0;
  align-self: start;
  box-sizing: border-box;
  overflow: hidden;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #ecf1ee;
  margin-bottom: 8px;
}

.panel-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 4px;
  margin-right: -2px;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}

.panel-scroll::-webkit-scrollbar {
  width: 6px;
}

.panel-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.panel-scroll::-webkit-scrollbar-thumb {
  background: #b8cbc2;
  border-radius: 3px;
}

.panel-scroll::-webkit-scrollbar-thumb:hover {
  background: #9eb5aa;
}

.panel-head h3 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: 15px;
  font-weight: 600;
  color: #2a3a33;
}

.icon-btn {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: var(--radius);
  width: 28px;
  height: 28px;
  cursor: pointer;
  color: var(--muted);
}

.panel-item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px;
  border-radius: var(--radius);
  background: var(--green-soft);
}

.panel-item strong {
  display: block;
  font-size: 13px;
  color: var(--text);
}

.panel-item small {
  font-size: 11px;
  color: var(--muted);
}

.panel-empty {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
  text-align: center;
  padding: 6px 0;
}

.panel-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  background: #f5f8f6;
  padding: 4px;
  border-radius: var(--radius);
}

.panel-tabs button {
  border: 0;
  background: transparent;
  border-radius: var(--radius);
  padding: 5px 4px;
  font-size: 11px;
  color: var(--muted);
  cursor: pointer;
}

.panel-tabs button.active {
  background: #fff;
  color: var(--green);
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.rec-list {
  display: grid;
  gap: 6px;
}

.rec-card {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  display: grid;
  gap: 4px;
}

.rec-card.active {
  border-color: var(--green);
  background: var(--green-soft);
}

.rec-top {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #56655f;
}

.rec-bar {
  height: 6px;
  border-radius: 0;
  background: #e8edea;
  overflow: hidden;
}

.rec-bar i {
  display: block;
  height: 100%;
  background: var(--green);
  border-radius: 0;
}

.rec-card small {
  color: var(--muted);
  font-size: 11px;
}

.panel-field {
  display: grid;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
}

.panel-field em {
  font-style: normal;
  float: right;
  color: #9aa8a1;
}

.stepper {
  display: flex;
  align-items: center;
  gap: 6px;
}

.stepper button {
  width: 32px;
  height: 32px;
  border: 1px solid #cddbd3;
  border-radius: var(--radius);
  background: #fff;
  cursor: pointer;
  font-size: 16px;
  color: var(--text);
}

.stepper input {
  flex: 1;
  border: 1px solid #cddbd3;
  border-radius: var(--radius);
  padding: 7px 8px;
  text-align: center;
  font: inherit;
}

.stepper .max-btn {
  width: auto;
  padding: 0 10px;
  font-size: 12px;
}

.panel-acts {
  display: grid;
  gap: 6px;
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid #ecf1ee;
}

.panel-expand {
  writing-mode: vertical-rl;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #fff;
  color: var(--green);
  padding: 12px 6px;
  cursor: pointer;
  height: 100%;
  min-height: 120px;
  align-self: stretch;
  font: inherit;
  font-size: 12px;
}

/* footer */
.rcv-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 4px 4px;
  flex-wrap: wrap;
  border-top: 1px solid #eef3f0;
  margin-top: 4px;
}

.foot-left, .foot-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

/* buttons */
.btn {
  border: 1px solid #cddbd3;
  border-radius: var(--radius);
  padding: 9px 14px;
  background: #fff;
  color: var(--text);
  font: inherit;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.btn.sm { padding: 6px 10px; font-size: 12px; }
.btn.block { width: 100%; justify-content: center; }
.btn.lg { min-width: 160px; padding: 11px 20px; font-weight: 600; }
.btn.primary { background: var(--green); border-color: var(--green); color: #fff; }
.btn.outline { border-color: var(--green); color: var(--green); background: #fff; }
.btn.ghost { background: #f5f8f6; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.link {
  border: 0;
  background: transparent;
  color: var(--green);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  padding: 0;
}

/* dialogs */
.mask {
  position: fixed;
  inset: 0;
  background: rgba(40, 55, 50, 0.35);
  display: grid;
  place-items: center;
  z-index: 50;
  padding: 20px;
}

.dialog {
  width: min(420px, 100%);
  border-radius: 12px;
  background: #fff;
  padding: 18px 20px;
  display: grid;
  gap: 14px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
}

.dialog.wide { width: min(560px, 100%); }

.dialog.add-dialog {
  width: min(720px, 100%);
}

.dialog header h3 {
  margin: 0 0 4px;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-weight: 600;
  color: #2a3a33;
}
.dialog header p { margin: 0; font-size: 12px; color: var(--muted); }
.dialog footer { display: flex; justify-content: flex-end; gap: 8px; }

.dlg-error {
  margin: 0;
  padding: 8px 10px;
  border-radius: var(--radius);
  background: #fdecea;
  color: #c0392b;
  font-size: 13px;
}

.dlg-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.dlg-grid label {
  display: grid;
  gap: 4px;
  font-size: 12px;
  color: var(--muted);
}

.dlg-grid label em {
  color: #d97a6b;
  font-style: normal;
}

.add-lines {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  font-size: 12px;
}

.add-lines-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f0f5f2;
  border-bottom: 1px solid #ecf1ee;
}

.add-lines-head strong {
  font-size: 12px;
  color: var(--text);
}

.add-line-row {
  display: grid;
  grid-template-columns: 1.6fr 1fr 0.7fr 48px;
  gap: 8px;
  align-items: center;
  padding: 6px 12px;
  border-top: 1px solid #eef3f0;
}

.add-line-row.head {
  border-top: none;
  background: #fafcfb;
  font-size: 11px;
  color: var(--muted);
  font-weight: 600;
}

.add-line-row select,
.add-line-row input {
  width: 100%;
  border: 1px solid #cddbd3;
  border-radius: var(--radius);
  padding: 6px 8px;
  font-size: 12px;
  color: var(--text);
  background: #fff;
}

.add-line-row .link {
  font-size: 11px;
}

.manual-box {
  display: grid;
  gap: 8px;
}

.manual-box label {
  display: grid;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
}

@media (max-width: 1200px) {
  .rcv-body {
    grid-template-columns: 1fr;
    align-items: start;
  }

  .rcv-side {
    grid-template-rows: auto;
    height: auto;
  }

  .rcv-main,
  .rcv-panel {
    height: auto;
  }

  .rcv-panel {
    max-height: none;
  }

  .rcv-summary {
    grid-template-columns: repeat(3, 1fr);
  }

  .rcv-steps {
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }

  .rcv-step .rail { display: none; }
}

@media (max-width: 720px) {
  .rcv-header { flex-direction: column; }
  .rcv-summary { grid-template-columns: 1fr 1fr; }
  .dlg-grid { grid-template-columns: 1fr; }
  .add-line-row,
  .add-line-row.head {
    grid-template-columns: 1fr;
  }
  .rcv-footer { flex-direction: column; align-items: stretch; }
}
</style>
