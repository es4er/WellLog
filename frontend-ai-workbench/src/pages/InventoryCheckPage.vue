<script setup>
import { ref, computed, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import * as XLSX from 'xlsx'
import { useInventory } from '../composables/useInventory'
import { getAuthToken, apiGet } from '../api'

const { doLogin, createStocktake, getStocktakeList, getStocktakeDetail, submitCountResult, confirmStocktakeDifference, dispatchStocktake, createAdjustment, loading } = useInventory()

const isApiMode = ref(false)

const checkOrders = ref([])

function mapStocktakeOrder(o) {
  return {
    check_no: o.stocktakeNo || o.check_no,
    check_range: o.stocktakeScope || o.check_range || '',
    check_type: o.stocktakeType || o.check_type || 'CYCLE',
    check_status: o.stocktakeStatus || o.check_status || 'DRAFT',
    created_by: o.createdBy || o.created_by || '系统',
    created_at: o.createdAt ? o.createdAt.replace('T', ' ').slice(0, 16) : (o.created_at || ''),
    executor: o.executor || null,
    executor_at: o.executorAt ? o.executorAt.replace('T', ' ').slice(0, 16) : (o.executor_at || null),
    reviewer: o.reviewer || null,
    reviewed_at: o.reviewedAt ? o.reviewedAt.replace('T', ' ').slice(0, 16) : (o.reviewed_at || null),
    notes: o.notes || '',
    items: (o.stocktakeLines || o.items || []).map(line => ({
      item_code: line.itemCode || line.item_code || '',
      item_name: line.itemName || line.item_name || '',
      batch_no: line.batchNo || line.batch_no || '',
      location_code: line.locationCode || line.location_code || '',
      sys_qty: line.bookQty ?? line.systemQty ?? line.sys_qty ?? 0,
      actual_qty: line.countedQty ?? line.actualQty ?? line.actual_qty ?? null,
      diff_qty: line.differenceQty ?? line.diff_qty ?? null,
      remark: line.remark || '',
    })),
    diff_total: o.differenceQtyTotal ?? o.differenceCount ?? o.diffTotal ?? o.diff_total ?? 0,
    diff_item_count: o.differenceItemCount ?? o.differenceCount ?? 0,
    adjustmentId: o.adjustmentId || null,
    adjustment_no: o.adjustmentNo || o.adjustment_no || null,
    adjustmentStatus: o.adjustmentStatus || null,
    evidence: o.evidence || o.evidence || [],
    stocktakeId: o.stocktakeId || o.id,
    freeze_inventory: o.freezeInventory || o.freeze_inventory || false,
  }
}

async function fetchFromApi() {
  if (!getAuthToken()) {
    try { await doLogin('admin', 'admin123') } catch (e) { return }
  }
  try {
    const data = await getStocktakeList()
    isApiMode.value = true
    checkOrders.value = (data || []).map(mapStocktakeOrder)
  } catch (e) {
    console.warn('Stocktake API unavailable')
    checkOrders.value = []
  }
}

onMounted(fetchFromApi)

const zones = ref([])

async function fetchZones() {
  try {
    const z = await apiGet('/warehouse/zone/list')
    zones.value = (z || []).map(zone => ({
      zoneId: zone.zoneId,
      code: zone.zoneCode || '',
      name: zone.zoneName || '',
    }))
  } catch { zones.value = [] }
}

onMounted(fetchZones)

const searchQuery = ref('')
const pageSize = ref(10)
const currentPage = ref(1)
const statusFilter = ref('')
const moreMenuOpen = ref(null)
const moreToolbarOpen = ref(false)
const detailVisible = ref(null)
const router = useRouter()
function goToAdjustmentPage() {
  router.push('/inventory-adjustment')
}

const detailTab = ref('items')
const detailTabs = [
  { key: 'info', label: '基本信息' },
  { key: 'items', label: '盘点明细' },
  { key: 'diff', label: '差异结果' },
  { key: 'adj', label: '关联调整单' }
]

const showAdvancedFilter = ref(false)
const advancedFilter = reactive({
  check_no: '', check_range: '', check_statuses: [],
  created_at_from: '', created_at_to: '',
})

const showAddDialog = ref(false)
const showImportDialog = ref(false)
const showCheckDialog = ref(false)
const showDiffConfirm = ref(false)
const showGenerateAdjDialog = ref(false)
const showEvidenceDialog = ref(false)

const addForm = reactive({ check_range: '', check_type: 'CYCLE', executor: '', plan_date: '', notes: '', freeze_inventory: false })
const checkTarget = ref(null)
const checkForm = reactive({ items: [] })
const diffTarget = ref(null)
const genAdjTarget = ref(null)
const actionItem = ref(null)

const importFile = ref(null)
const fileInputRef = ref(null)
const importFileName = ref('')

const toastMessage = ref('')
let toastTimer = null

function showToast(msg) {
  toastMessage.value = msg
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toastMessage.value = '' }, 2500)
}

function formatDate() {
  const d = new Date()
  return `${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`
}

const statusMap = {
  DRAFT: { label: '草稿', cls: 'status-draft', bg: '#F4F4F5', color: '#8C8C8C' },
  PENDING_CHECK: { label: '待盘点', cls: 'status-pending', bg: '#FFF3E0', color: '#D4880F' },
  COUNTING: { label: '盘点中', cls: 'status-checking', bg: '#E3F2FD', color: '#1976D2' },
  DIFFERENCE_PENDING: { label: '待确认差异', cls: 'status-diff', bg: '#FCE4EC', color: '#C62828' },
  DIFFERENCE_CONFIRMED: { label: '差异已确认', cls: 'status-confirmed', bg: '#FFF8E1', color: '#F57C00' },
  ADJUSTMENT_CREATED: { label: '已生成调整单', cls: 'status-adj', bg: '#E8F5E9', color: '#3E8E41' },
  COMPLETED: { label: '已完成', cls: 'status-completed', bg: '#F3E5F5', color: '#7B1FA2' },
}

const tabConfig = {
  '': { label: '全部', color: '#587766' },
  DRAFT: { label: '草稿', color: '#8C8C8C' },
  PENDING_CHECK: { label: '待盘点', color: '#D4880F' },
  COUNTING: { label: '盘点中', color: '#1976D2' },
  DIFFERENCE_PENDING: { label: '待确认差异', color: '#C62828' },
  DIFFERENCE_CONFIRMED: { label: '差异已确认', color: '#F57C00' },
  ADJUSTMENT_CREATED: { label: '已生成调整单', color: '#3E8E41' },
  COMPLETED: { label: '已完成', color: '#7B1FA2' },
}

const filteredData = computed(() => {
  let list = checkOrders.value
  if (statusFilter.value) list = list.filter(o => o.check_status === statusFilter.value)
  const q = searchQuery.value.trim().toLowerCase()
  if (q) list = list.filter(o => o.check_no.toLowerCase().includes(q) || o.check_range.toLowerCase().includes(q))
  const af = advancedFilter
  if (af.check_no) list = list.filter(o => o.check_no.toLowerCase().includes(af.check_no.toLowerCase()))
  if (af.check_range) list = list.filter(o => o.check_range.includes(af.check_range))
  if (af.check_statuses.length) list = list.filter(o => af.check_statuses.includes(o.check_status))
  if (af.created_at_from) list = list.filter(o => o.created_at >= af.created_at_from)
  if (af.created_at_to) list = list.filter(o => o.created_at <= af.created_at_to)
  return list
})

const totalItems = computed(() => filteredData.value.length)
const totalPages = computed(() => Math.ceil(totalItems.value / pageSize.value))
const pagedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredData.value.slice(start, start + pageSize.value)
})

const stats = computed(() => {
  const d = checkOrders.value
  return {
    total: d.length,
    draft: d.filter(o => o.check_status === 'DRAFT').length,
    pending: d.filter(o => o.check_status === 'PENDING_CHECK').length,
    counting: d.filter(o => o.check_status === 'COUNTING').length,
    diff: d.filter(o => o.check_status === 'DIFFERENCE_PENDING').length,
    diffConfirmed: d.filter(o => o.check_status === 'DIFFERENCE_CONFIRMED').length,
    adj: d.filter(o => o.check_status === 'ADJUSTMENT_CREATED').length,
    completed: d.filter(o => o.check_status === 'COMPLETED').length,
  }
})

const statCards = computed(() => [
  { label: '总盘点单', value: stats.value.total, filterKey: '' },
  { label: '草稿', value: stats.value.draft, filterKey: 'DRAFT' },
  { label: '待盘点', value: stats.value.pending, filterKey: 'PENDING_CHECK' },
  { label: '盘点中', value: stats.value.counting, filterKey: 'COUNTING' },
  { label: '待确认差异', value: stats.value.diff, filterKey: 'DIFFERENCE_PENDING' },
  { label: '差异已确认', value: stats.value.diffConfirmed, filterKey: 'DIFFERENCE_CONFIRMED' },
  { label: '已生成调整单', value: stats.value.adj, filterKey: 'ADJUSTMENT_CREATED' },
])

const detailDifferenceItems = computed(() => {
  const target = detailVisible.value
  if (!target || !target.items) return []
  return target.items.filter(line => {
    const diff = Number(line.diff_qty ?? (Number(line.actual_qty || 0) - Number(line.sys_qty || 0)))
    return diff !== 0
  })
})

const detailDifferenceCount = computed(() => detailDifferenceItems.value.length)

const detailAbsoluteDifferenceTotal = computed(() => {
  return detailDifferenceItems.value.reduce((sum, line) => {
    const diff = Number(line.diff_qty ?? (Number(line.actual_qty || 0) - Number(line.sys_qty || 0)))
    return sum + Math.abs(diff)
  }, 0)
})

function filterByStatus(key) {
  if (!key) return
  statusFilter.value = key
  currentPage.value = 1
}

function toggleMore(idx) {
  moreMenuOpen.value = moreMenuOpen.value === idx ? null : idx
}

function isLastThree(idx) {
  return pagedData.value.length - idx <= 3
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
  advancedFilter.check_no = ''
  advancedFilter.check_range = ''
  advancedFilter.check_statuses = []
  advancedFilter.created_at_from = ''
  advancedFilter.created_at_to = ''
  currentPage.value = 1
}

function applyAdvancedFilter() {
  currentPage.value = 1
  showAdvancedFilter.value = false
  showToast('查询完成')
}

function toggleAdvStatus(status) {
  const idx = advancedFilter.check_statuses.indexOf(status)
  if (idx >= 0) advancedFilter.check_statuses.splice(idx, 1)
  else advancedFilter.check_statuses.push(status)
}

const rangeOptions = computed(() => {
  const opts = [{ value: 'ALL', label: '全仓盘点' }]
  ;(zones.value || []).forEach(z => {
    opts.push({ value: z.code, label: `${z.name}（${z.code}）` })
  })
  return opts
})

watch(() => addForm.check_range, (val) => {
  if (val === 'ALL') addForm.check_type = 'FULL'
  else if (val) addForm.check_type = 'ZONE'
  else addForm.check_type = 'CYCLE'
})

const typeOptions = [
  { value: 'FULL', label: '全盘' },
  { value: 'ZONE', label: '区域盘点' },
  { value: 'ITEM', label: '物料盘点' },
  { value: 'CYCLE', label: '临时抽盘' },
]
const executorOptions = ['张仓库', '李仓库', '王仓库']

function openAddDialog() {
  addForm.check_range = ''
  addForm.check_type = 'CYCLE'
  addForm.executor = ''
  addForm.plan_date = ''
  addForm.notes = ''
  addForm.freeze_inventory = false
  showAddDialog.value = true
}

async function confirmAdd(draft = false) {
  if (!addForm.check_range) { showToast('请选择盘点范围'); return }
  const isDraft = draft || !addForm.executor
  if (isApiMode.value) {
    try {
      const result = await createStocktake(1, addForm.check_type, addForm.check_range, 1)
      if (result) {
        const mapped = mapStocktakeOrder(result)
        if (!isDraft && addForm.executor) {
          await dispatchStocktake(result.stocktakeId, 1)
          mapped.check_status = 'PENDING_CHECK'
          mapped.executor = addForm.executor
          mapped.executor_at = new Date().toISOString().slice(0,16).replace('T',' ')
        }
        mapped.notes = addForm.notes
        mapped.freeze_inventory = addForm.freeze_inventory
        checkOrders.value.unshift(mapped)
  
        showAddDialog.value = false
        showToast(isDraft ? '已保存为草稿' : '盘点任务已下发！')
        return
      }
    } catch (e) {
      showToast('API创建失败: ' + e.message)
      return
    }
  }
  const lastNo = checkOrders.value.length ? checkOrders.value[checkOrders.value.length - 1].check_no : 'CK-2026-07000'
  const num = parseInt(lastNo.split('-')[2]) + 1
  const newItem = {
    check_no: `CK-2026-${String(num).padStart(3,'0')}`,
    check_range: addForm.check_range,
    check_type: addForm.check_type,
    check_status: isDraft ? 'DRAFT' : 'PENDING_CHECK',
    created_by: '韩库存',
    created_at: new Date().toISOString().slice(0,16).replace('T',' '),
    executor: isDraft ? null : addForm.executor,
    executor_at: isDraft ? null : new Date().toISOString().slice(0,16).replace('T',' '),
    reviewer: null,
    reviewed_at: null,
    notes: addForm.notes,
    freeze_inventory: addForm.freeze_inventory,
    items: [],
    diff_total: 0,
    adjustment_no: null,
    evidence: [],
  }
  checkOrders.value.unshift(newItem)
  showAddDialog.value = false
  showToast(isDraft ? '已保存为草稿' : '盘点任务已下发！')
}

async function dispatchOrder(item) {
  if (isApiMode.value && item.stocktakeId) {
    try {
      await dispatchStocktake(item.stocktakeId, 1)
      const detail = await getStocktakeDetail(item.stocktakeId)
      if (detail && detail.lines) {
        item.items = detail.lines.map(line => ({
          stocktakeLineId: line.stocktakeLineId,
          item_code: line.itemCode || '',
          item_name: line.itemName || '',
          batch_no: line.batchNo || '',
          location_code: line.locationCode || '',
          sys_qty: line.bookQty ?? 0,
          actual_qty: line.countedQty ?? null,
          diff_qty: line.differenceQty ?? null,
          remark: line.remark || '',
        }))
      }
    } catch (e) {
      console.warn('dispatchStocktake failed', e)
    }
  }
  item.check_status = 'PENDING_CHECK'
  item.executor_at = new Date().toISOString().slice(0,16).replace('T',' ')
  showToast('已下发盘点任务')
  moreMenuOpen.value = null
}

function deleteOrder(item) {
  const idx = checkOrders.value.indexOf(item)
  if (idx !== -1) checkOrders.value.splice(idx, 1)
  showToast('已删除盘点单')
  moreMenuOpen.value = null
}

async function openCheckDialog(item) {
  checkTarget.value = item
  if (isApiMode.value && item.stocktakeId) {
    try {
      const detail = await getStocktakeDetail(item.stocktakeId)
      if (detail && detail.lines) {
        item.items = detail.lines.map(line => ({
          stocktakeLineId: line.stocktakeLineId,
          item_code: line.itemCode || '',
          item_name: line.itemName || '',
          batch_no: line.batchNo || '',
          location_code: line.locationCode || '',
          sys_qty: line.bookQty ?? 0,
          actual_qty: line.countedQty ?? null,
          diff_qty: line.differenceQty ?? null,
          remark: line.remark || '',
        }))
      }
    } catch (e) {
      console.warn('getStocktakeDetail failed', e)
    }
  }
  checkForm.items = item.items.map(i => ({ ...i }))
  showCheckDialog.value = true
}

async function confirmCheck() {
  const target = checkTarget.value
  if (!target) return
  target.items = checkForm.items.map(i => ({ ...i }))
  let hasNull = false
  target.items.forEach(i => {
    if (i.actual_qty === null || i.actual_qty === undefined || i.actual_qty === '') hasNull = true
    else {
      i.diff_qty = i.actual_qty - i.sys_qty
    }
  })
  target.check_status = hasNull ? 'COUNTING' : 'DIFFERENCE_PENDING'
  target.executor_at = target.executor_at || new Date().toISOString().slice(0,16).replace('T',' ')
  target.diff_total = target.items.reduce((s, i) => s + (i.diff_qty || 0), 0)
  if (!hasNull) {
    target.reviewer = null
    target.reviewed_at = null
  }
  if (isApiMode.value && target.stocktakeId) {
    try {
      const countLines = target.items
        .filter(i => i.actual_qty !== null && i.actual_qty !== undefined && i.actual_qty !== '')
        .filter(i => i.stocktakeLineId)
        .map(i => ({
          stocktakeLineId: i.stocktakeLineId,
          countedQty: i.actual_qty,
        }))
      if (countLines.length) {
        await submitCountResult({ stocktakeId: target.stocktakeId, lines: countLines })
      }
    } catch (e) {
      console.warn('submitCountResult failed', e)
    }
  }
  showCheckDialog.value = false
  checkTarget.value = null
  showToast(hasNull ? '已保存实盘数据，继续盘点' : '盘点完成，差异待确认')
}

function openDiffDialog(item) {
  diffTarget.value = item
  showDiffConfirm.value = true
}

async function confirmDiff() {
  const target = diffTarget.value
  if (!target) return
  if (isApiMode.value && target.stocktakeId) {
    try {
      await confirmStocktakeDifference(target.stocktakeId)
      showToast('差异确认成功')
      showDiffConfirm.value = false
      diffTarget.value = null
      await fetchFromApi()
      return
    } catch (e) {
      console.error('确认差异失败：', e)
      showToast(e.message || '确认差异失败')
      showDiffConfirm.value = false
      diffTarget.value = null
      return
    }
  }
  const hasDiff = target.items && target.items.some(i => i.diff_qty !== null && i.diff_qty !== 0)
  target.check_status = hasDiff ? 'DIFFERENCE_CONFIRMED' : 'COMPLETED'
  target.reviewer = '韩库存'
  target.reviewed_at = new Date().toISOString().slice(0,16).replace('T',' ')
  showDiffConfirm.value = false
  diffTarget.value = null
  showToast(hasDiff ? '差异已确认' : '零差异，盘点已完成')
}

function openAdjustment(stocktakeId) {
  const item = checkOrders.value.find(o => o.stocktakeId === stocktakeId)
  if (item) {
    showDetail(item)
    detailTab.value = 'adj'
  }
}

async function handleCreateAdjustment(item) {
  if (!item.stocktakeId) { showToast('盘点单ID无效'); return }
  try {
    if (item.check_status !== 'DIFFERENCE_CONFIRMED') {
      await confirmStocktakeDifference(item.stocktakeId)
    }
    const result = await createAdjustment(item.stocktakeId, '盘点差异调整')
    item.adjustment_no = result.adjustmentNo
    showToast(`调整单 ${result.adjustmentNo} 生成成功`)
    await Promise.all([
      fetchFromApi(),
    ])
  } catch (e) {
    console.error('生成调整单失败：', e)
    showToast(e.message || '生成调整单失败')
  }
}

function openGenerateAdjDialog(item) {
  genAdjTarget.value = item
  showGenerateAdjDialog.value = true
}

async function confirmGenerateAdj() {
  const target = genAdjTarget.value
  if (!target) return
  const diffLines = target.items.filter(i => i.diff_qty !== null && i.diff_qty !== 0)
  if (!diffLines.length) { showToast('无差异，无需生成调整单'); return }
  if (isApiMode.value && target.stocktakeId) {
    try {
      const result = await createAdjustment(target.stocktakeId, '盘点差异调整')
      if (result) {
        target.adjustment_no = result.adjustmentNo
        target.check_status = 'ADJUSTMENT_CREATED'
  
        showGenerateAdjDialog.value = false
        genAdjTarget.value = null
        showToast(`已生成调整单 ${result.adjustmentNo}，请在盘点调整中查看`)
        return
      }
    } catch (e) {
      console.warn('createAdjustment failed', e)
    }
  }
  showGenerateAdjDialog.value = false
  genAdjTarget.value = null
  showToast('生成调整单失败')
}

function completeCheck(item) {
  if (item.check_status === 'COUNTING') {
    item.items.forEach(i => {
      i.actual_qty = i.actual_qty ?? 0
      i.diff_qty = i.actual_qty - i.sys_qty
    })
    item.diff_total = item.items.reduce((s, i) => s + (i.diff_qty || 0), 0)
    item.check_status = 'DIFFERENCE_PENDING'
      showToast('盘点完成，差异待确认')
  } else {
    item.check_status = 'COMPLETED'
      showToast('盘点已完成')
  }
}

function viewEvidence(item) {
  actionItem.value = item
  showEvidenceDialog.value = true
}

function handleImportFileChange(e) {
  const file = e.target.files[0]
  if (file) { importFile.value = file; importFileName.value = file.name }
}

function confirmImport() {
  if (!importFile.value) { showToast('请先选择文件'); return }
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const data = new Uint8Array(e.target.result)
      const workbook = XLSX.read(data, { type: 'array' })
      const sheet = workbook.Sheets[workbook.SheetNames[0]]
      const rows = XLSX.utils.sheet_to_json(sheet, { header: 1 })
      if (rows.length < 2) { showToast('导入失败，模板为空或格式错误'); return }
      const imported = []
      for (let i = 1; i < rows.length; i++) {
        const row = rows[i]
        if (!row || !row[0]) continue
        const no = row[0].toString().trim()
        const range = (row[1] && row[1].toString().trim()) || ''
        const statusText = (row[2] && row[2].toString().trim()) || '草稿'
        const creator = (row[3] && row[3].toString().trim()) || '韩库存'
        const time = (row[4] && row[4].toString().trim()) || new Date().toISOString().slice(0,16).replace('T',' ')
        const statusKey = Object.entries(statusMap).find(([, v]) => v.label === statusText)?.[0] || 'DRAFT'
        imported.push({
          check_no: no, check_range: range, check_status: statusKey,
          created_by: creator, created_at: time,
          executor: null, executor_at: null, reviewer: null, reviewed_at: null,
          notes: '',
          items: [],
          diff_total: 0, adjustment_no: null, evidence: [],
        })
      }
      if (!imported.length) { showToast('导入失败，未读取到有效数据'); return }
      checkOrders.value.unshift(...imported)

      showImportDialog.value = false
      importFile.value = null
      importFileName.value = ''
      if (fileInputRef.value) fileInputRef.value.value = ''
      showToast(`导入成功！共导入 ${imported.length} 条数据`)
    } catch { showToast('导入失败，请检查模板格式') }
  }
  reader.readAsArrayBuffer(importFile.value)
}

function downloadStocktakeTemplate() {
  const header = [['盘点单号', '盘点范围', '状态', '创建人', '创建时间']]
  const ws = XLSX.utils.aoa_to_sheet([header, ['CK-2026-07005', 'A区主库区', '草稿', '韩库存', '2026-07-10 14:00']])
  ws['!cols'] = [{ wch: 16 }, { wch: 14 }, { wch: 10 }, { wch: 8 }, { wch: 18 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '导入模板')
  XLSX.writeFile(wb, `盘点单导入模板.xlsx`)
  showToast('模板下载成功')
}

function downloadCountTemplate() {
  const header = [['盘点单号', '物料编码', '物料名称', '批次号', '库位编码', '账面数量', '实盘数量', '盘点人', '备注']]
  const ws = XLSX.utils.aoa_to_sheet([header])
  ws['!cols'] = [{ wch: 16 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 20 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '实盘导入模板')
  XLSX.writeFile(wb, `实盘结果导入模板.xlsx`)
  showToast('实盘模板下载成功')
}

function handleExportList() {
  const headerRow = ['盘点单号', '盘点范围', '状态', '创建人', '创建时间', '执行人', '差异总数', '关联调整单']
  const dataRows = filteredData.value.map(o => [
    o.check_no, o.check_range, statusMap[o.check_status]?.label || o.check_status,
    o.created_by, o.created_at, o.executor || '-', o.diff_total, o.adjustment_no || '-',
  ])
  const ws = XLSX.utils.aoa_to_sheet([headerRow, ...dataRows])
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '盘点表')
  XLSX.writeFile(wb, `盘点单列表_${formatDate()}.xlsx`)
  showToast('导出盘点列表成功')
}

function handleExportTemplate(item) {
  const target = item || detailVisible.value
  if (!target) { showToast('请先选择一张盘点单'); return }
  const headerRow = ['物料编码', '物料名称', '批次号', '库位编码', '账面数量', '实盘数量', '备注']
  const dataRows = (target.items || []).map(i => [
    i.item_code, i.item_name, i.batch_no, i.location_code, i.sys_qty, '', '',
  ])
  const ws = XLSX.utils.aoa_to_sheet([headerRow, ...dataRows])
  ws['!cols'] = [{ wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 10 }, { wch: 10 }, { wch: 20 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '盘点模板')
  XLSX.writeFile(wb, `盘点模板_${target.check_no}_${formatDate()}.xlsx`)
  showToast('导出盘点模板成功')
}

async function showDetail(item) {
  if (detailVisible.value?.check_no === item.check_no) {
    detailVisible.value = null
  } else {
    if (isApiMode.value && item.stocktakeId) {
      try {
        const detail = await getStocktakeDetail(item.stocktakeId)
        if (detail && detail.lines) {
          item.items = detail.lines.map(line => ({
            stocktakeLineId: line.stocktakeLineId,
            item_code: line.itemCode || '',
            item_name: line.itemName || '',
            batch_no: line.batchNo || '',
            location_code: line.locationCode || '',
            sys_qty: line.bookQty ?? 0,
            actual_qty: line.countedQty ?? null,
            diff_qty: line.differenceQty ?? null,
            remark: line.remark || '',
          }))
        }
      } catch (e) {
        console.warn('getStocktakeDetail failed', e)
      }
    }
    detailVisible.value = item
    detailTab.value = 'info'
  }
}

function calcDiffTotal(items) {
  return items.reduce((s, i) => s + (i.diff_qty || 0), 0)
}

function isCheckComplete(item) {
  return item.items.every(i => i.actual_qty !== null && i.actual_qty !== undefined && i.actual_qty !== '')
}

function canGenAdj(item) {
  return item.check_status === 'DIFFERENCE_PENDING' && item.items.some(i => i.diff_qty !== null && i.diff_qty !== 0)
}

function getTypeLabel(type) {
  return typeOptions.find(t => t.value === type)?.label || type
}

function formatQuantity(value) {
  if (value === null || value === undefined || value === '') return '-'
  return Number(value).toFixed(3).replace(/\.?0+$/, '')
}
</script>

<template>
  <div class="chk-page">
    <header class="chk-header">
      <div>
        <h1>库存盘点管理</h1>
      </div>
      <div class="chk-strip">
        <div v-for="c in statCards" :key="c.label" class="strip-item" :class="{ clickable: c.filterKey }" @click="filterByStatus(c.filterKey)">
          <strong class="num">{{ c.value }}</strong>
          <span class="lbl">{{ c.label }}</span>
        </div>
      </div>
    </header>

    <div class="chk-toolbar">
      <div class="toolbar-left">
        <button class="tb-btn primary" @click="openAddDialog">新建盘点</button>
        <div class="more-wrap">
            <button type="button" class="original-more-btn" @click="moreToolbarOpen = !moreToolbarOpen"><span>更多</span><span class="more-arrow">▼</span></button>
          <div v-if="moreToolbarOpen" class="more-toolbar-dropdown">
            <button class="drop-item" @click="moreToolbarOpen = false; showImportDialog = true">导入实盘结果</button>
            <button class="drop-item" @click="moreToolbarOpen = false; handleExportList()">导出盘点列表</button>
            <button class="drop-item" @click="moreToolbarOpen = false; handleExportTemplate()">导出盘点模板</button>
            <button class="drop-item" @click="moreToolbarOpen = false; showAdvancedFilter = !showAdvancedFilter">高级查询</button>
          </div>
        </div>
      </div>
      <div v-if="moreToolbarOpen" class="more-overlay" @click="moreToolbarOpen = null"></div>
      <div class="toolbar-center">
        <div class="toolbar-tabs">
          <button v-for="(cfg, key) in tabConfig" :key="key"
            :class="['tab-btn', { active: statusFilter === key }]"
            :style="statusFilter === key ? { background: cfg.color, borderColor: cfg.color, color: '#fff' } : { background: '#fff', borderColor: '#cddbd3', color: '#303133' }"
            @click="statusFilter = key; currentPage = 1">{{ cfg.label }}</button>
        </div>
      </div>
      <div class="toolbar-right">
        <input v-model="searchQuery" placeholder="搜索盘点单号/范围" class="tb-search" />
      </div>
    </div>

    <transition name="slide">
      <div v-if="showAdvancedFilter" class="adv-filter-panel">
        <div class="adv-filter-grid">
          <div class="adv-field">
            <label>盘点单号</label>
            <input v-model="advancedFilter.check_no" placeholder="输入盘点单号" />
          </div>
          <div class="adv-field">
            <label>盘点范围</label>
            <input v-model="advancedFilter.check_range" placeholder="输入盘点范围" />
          </div>
          <div class="adv-field adv-field-wide">
            <label>状态</label>
            <div class="adv-check-group">
              <label v-for="s in ['DRAFT', 'PENDING_CHECK', 'COUNTING', 'DIFFERENCE_PENDING', 'ADJUSTMENT_CREATED', 'COMPLETED']" :key="s" class="adv-check">
                <input type="checkbox" :checked="advancedFilter.check_statuses.includes(s)" @change="toggleAdvStatus(s)" />
                <span>{{ statusMap[s]?.label }}</span>
              </label>
            </div>
          </div>
          <div class="adv-field">
            <label>创建时间</label>
            <div class="adv-date-range">
              <input v-model="advancedFilter.created_at_from" type="date" />
              <span>至</span>
              <input v-model="advancedFilter.created_at_to" type="date" />
            </div>
          </div>
        </div>
        <div class="adv-filter-actions">
          <button class="tb-btn primary" @click="applyAdvancedFilter">查询</button>
          <button class="tb-btn" @click="resetAdvancedFilter">重置</button>
        </div>
      </div>
    </transition>

    <div class="stocktake-list">
      <div class="table-card">
        <div class="table-scroll-window stocktake-table-window">
          <table class="compact-table stocktake-table">
            <colgroup>
              <col style="width: 270px" />
              <col style="width: 220px" />
              <col style="width: 140px" />
              <col style="width: 130px" />
              <col style="width: 90px" />
              <col style="width: 165px" />
              <col style="width: 100px" />
              <col style="width: 100px" />
              <col style="width: 180px" />
              <col style="width: 130px" />
            </colgroup>
            <thead>
              <tr>
                <th>盘点单号</th>
                <th>盘点范围</th>
                <th>盘点方式</th>
                <th>状态</th>
                <th>创建人</th>
                <th>创建时间</th>
                <th>执行人</th>
                <th>差异数量</th>
                <th>关联调整单</th>
                <th class="action-column">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, idx) in pagedData" :key="item.check_no" @click="showDetail(item)" :class="{ active: detailVisible?.check_no === item.check_no }">
                <td><span class="no-text">{{ item.check_no }}</span></td>
                <td>{{ item.check_range }}</td>
                <td><span class="type-tag">{{ getTypeLabel(item.check_type) }}</span></td>
                <td>
                  <span :class="['status-tag', statusMap[item.check_status]?.cls]"
                    :style="{ background: statusMap[item.check_status]?.bg, color: statusMap[item.check_status]?.color }">
                    <i class="status-dot" :style="{ background: statusMap[item.check_status]?.color }"></i>
                    {{ statusMap[item.check_status]?.label }}
                  </span>
                </td>
                <td>{{ item.created_by }}</td>
                <td>{{ item.created_at }}</td>
                <td>{{ item.executor || '-' }}</td>
                <td :class="{ 'text-orange': item.diff_total !== 0 }">{{ item.diff_total || 0 }}</td>
                <td>
                  <button v-if="item.adjustment_no" type="button" class="adjustment-link" @click.stop="openAdjustment(item.stocktakeId)">{{ item.adjustment_no }}</button>
                  <span v-else>-</span>
                </td>
                <td @click.stop>
                  <div class="action-cell">
                    <template v-if="item.check_status === 'DRAFT'">
                      <button class="action-btn edit-btn" @click="openCheckDialog(item)">编辑盘点</button>
                      <button class="action-link" @click="showDetail(item)">详情</button>
                      <button class="action-link" style="color:#c0392b" @click="deleteOrder(item)">删除</button>
                    </template>
                    <template v-else-if="item.check_status === 'PENDING_CHECK'">
                      <button class="action-btn start-btn" @click="dispatchOrder(item)">下发盘点</button>
                      <button class="action-link" @click="openCheckDialog(item)">录入</button>
                      <button class="action-link" @click="showDetail(item)">详情</button>
                    </template>
                    <template v-else-if="item.check_status === 'COUNTING'">
                      <button class="action-btn input-btn" @click="openCheckDialog(item)">录入实盘</button>
                      <button class="action-link" @click="completeCheck(item)">完成</button>
                      <button class="action-link" @click="showDetail(item)">详情</button>
                    </template>
                    <template v-else-if="item.check_status === 'DIFFERENCE_PENDING'">
                      <button v-if="Number(item.diff_total) > 0 && !item.adjustmentId" class="action-btn diff-btn" @click="openDiffDialog(item)">确认差异</button>
                      <button v-if="Number(item.diff_total) > 0 && !item.adjustmentId" class="action-btn gen-btn" @click="handleCreateAdjustment(item)">生成调整单</button>
                      <button class="action-link" @click="showDetail(item)">详情</button>
                    </template>
                    <template v-else-if="item.check_status === 'DIFFERENCE_CONFIRMED'">
                      <button v-if="Number(item.diff_total) > 0 && !item.adjustmentId" class="action-btn gen-btn" @click="handleCreateAdjustment(item)">生成调整单</button>
                      <button class="action-link" @click="showDetail(item)">详情</button>
                    </template>
                    <template v-else-if="item.check_status === 'ADJUSTMENT_CREATED'">
                      <button class="action-btn gen-btn" @click="showDetail(item); detailTab = 'adj'">查看调整单</button>
                      <button class="action-link" @click="showDetail(item)">详情</button>
                    </template>
                    <template v-else>
                      <button type="button" class="table-detail-btn" @click="showDetail(item)">查看详情</button>
                    </template>
                  </div>
                </td>
              </tr>
              <tr v-if="pagedData.length === 0">
                <td colspan="10" class="empty-cell">暂无盘点数据。点击「新建盘点」创建首个盘点任务，从 A区主库区 开始</td>
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
            </select>
            <span>条</span>
          </div>
          <div class="page-controls">
            <button :disabled="currentPage === 1" @click="changePage(currentPage - 1)">‹</button>
            <span class="page-num">{{ currentPage }} / {{ totalPages || 1 }} 页</span>
            <button :disabled="currentPage === totalPages || totalPages === 0" @click="changePage(currentPage + 1)">›</button>
          </div>
        </div>
      </div>

      <!-- Detail Panel -->
      <transition name="slide">
        <div v-if="detailVisible" class="detail-panel">
          <div class="detail-head">
            <div class="detail-title">
              <strong>{{ detailVisible.check_no }}</strong>
              <span :class="['status-tag', statusMap[detailVisible.check_status]?.cls]"
                :style="{ background: statusMap[detailVisible.check_status]?.bg, color: statusMap[detailVisible.check_status]?.color }">
                <i class="status-dot" :style="{ background: statusMap[detailVisible.check_status]?.color }"></i>
                {{ statusMap[detailVisible.check_status]?.label }}
              </span>
            </div>
            <button class="detail-close" @click="detailVisible = null">×</button>
          </div>
          <div class="detail-tabs">
            <button
              v-for="tab in detailTabs"
              :key="tab.key"
              type="button"
              class="detail-tab"
              :class="{ active: detailTab === tab.key }"
              @click="detailTab = tab.key"
            >{{ tab.label }}</button>
          </div>
          <div class="detail-body">
        <div v-if="detailTab === 'info'" class="detail-meta">
          <div class="meta-item"><span class="meta-label">盘点单号</span><span>{{ detailVisible.check_no }}</span></div>
          <div class="meta-item"><span class="meta-label">盘点范围</span><span>{{ detailVisible.check_range }}</span></div>
          <div class="meta-item"><span class="meta-label">盘点方式</span><span>{{ getTypeLabel(detailVisible.check_type) }}</span></div>
          <div class="meta-item"><span class="meta-label">创建人</span><span>{{ detailVisible.created_by }}</span></div>
          <div class="meta-item"><span class="meta-label">创建时间</span><span>{{ detailVisible.created_at }}</span></div>
          <div class="meta-item"><span class="meta-label">执行人</span><span>{{ detailVisible.executor || '-' }}</span></div>
          <div class="meta-item"><span class="meta-label">执行时间</span><span>{{ detailVisible.executor_at || '-' }}</span></div>
          <div class="meta-item"><span class="meta-label">审核人</span><span>{{ detailVisible.reviewer || '-' }}</span></div>
          <div class="meta-item"><span class="meta-label">审核时间</span><span>{{ detailVisible.reviewed_at || '-' }}</span></div>
          <div class="meta-item"><span class="meta-label">差异总数</span><span :class="{ 'text-orange': detailVisible.diff_total !== 0 }">{{ detailVisible.diff_total || 0 }}</span></div>
          <div class="meta-item"><span class="meta-label">调整单号</span><span>{{ detailVisible.adjustment_no || '-' }}</span></div>
          <div class="meta-item"><span class="meta-label">锁定库存</span><span>{{ detailVisible.freeze_inventory ? '是' : '-' }}</span></div>
          <div v-if="detailVisible.notes" class="meta-item meta-item-wide"><span class="meta-label">备注</span><span>{{ detailVisible.notes }}</span></div>
        </div>
        <div v-if="detailTab === 'items'" class="detail-section">
          <div class="detail-section-header">
            <span class="detail-subtitle">盘点明细</span>
            <button class="tb-btn" @click="handleExportTemplate(detailVisible)">导出盘点模板</button>
          </div>
          <div class="stocktake-detail-table-wrap">
            <table class="stocktake-detail-table">
              <colgroup>
                <col style="width:15%" />
                <col style="width:18%" />
                <col style="width:15%" />
                <col style="width:15%" />
                <col style="width:12%" />
                <col style="width:12%" />
                <col style="width:13%" />
              </colgroup>
              <thead>
                <tr>
                  <th>物料编码</th>
                  <th>物料名称</th>
                  <th>批次号</th>
                  <th>库位编码</th>
                  <th class="number-column">账面数量</th>
                  <th class="number-column">实盘数量</th>
                  <th class="number-column">差异</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="line in detailVisible.items" :key="line.item_code + line.batch_no + line.location_code">
                  <td>{{ line.item_code || '-' }}</td>
                  <td>{{ line.item_name || '-' }}</td>
                  <td>{{ line.batch_no || '-' }}</td>
                  <td>{{ line.location_code || '-' }}</td>
                  <td class="number-column">{{ formatQuantity(line.sys_qty) }}</td>
                  <td class="number-column">{{ formatQuantity(line.actual_qty) }}</td>
                  <td class="number-column difference-value" :class="{ negative: Number(line.diff_qty) < 0, positive: Number(line.diff_qty) > 0 }">{{ formatQuantity(line.diff_qty) }}</td>
                </tr>
                <tr v-if="!detailVisible.items?.length">
                  <td colspan="7" class="empty-table-cell">暂无盘点明细</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div v-if="detailTab === 'diff'" class="detail-section">
          <div v-if="detailDifferenceCount > 0" class="detail-diff-summary">
            <p>共有 <strong>{{ detailDifferenceCount }}</strong> 项差异，差异数量合计 <strong>{{ detailAbsoluteDifferenceTotal }}</strong></p>
          </div>
          <div v-else class="detail-diff-summary">
            <p>暂无差异或差异待计算</p>
          </div>
          <div class="stocktake-detail-table-wrap">
            <table class="stocktake-detail-table">
              <colgroup>
                <col style="width:16%" />
                <col style="width:19%" />
                <col style="width:16%" />
                <col style="width:12%" />
                <col style="width:12%" />
                <col style="width:11%" />
                <col style="width:14%" />
              </colgroup>
              <thead>
                <tr>
                  <th>物料编码</th>
                  <th>物料名称</th>
                  <th>库位编码</th>
                  <th class="number-column">账面数量</th>
                  <th class="number-column">实盘数量</th>
                  <th class="number-column">差异</th>
                  <th>备注</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="line in detailVisible.items.filter(i => i.diff_qty !== null && i.diff_qty !== 0)" :key="line.item_code + line.batch_no">
                  <td>{{ line.item_code || '-' }}</td>
                  <td>{{ line.item_name || '-' }}</td>
                  <td>{{ line.location_code || '-' }}</td>
                  <td class="number-column">{{ formatQuantity(line.sys_qty) }}</td>
                  <td class="number-column">{{ formatQuantity(line.actual_qty) }}</td>
                  <td class="number-column difference-value" :class="{ negative: Number(line.diff_qty) < 0, positive: Number(line.diff_qty) > 0 }">{{ formatQuantity(line.diff_qty) }}</td>
                  <td>{{ line.remark || '-' }}</td>
                </tr>
                <tr v-if="detailDifferenceCount === 0">
                  <td colspan="7" class="empty-table-cell">暂无盘点差异</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div v-if="detailTab === 'adj'" class="related-adjustment-content">
          <div
            v-if="detailVisible.adjustment_no"
            class="related-adjustment-notice"
          >
            已生成调整单
            <strong>{{ detailVisible.adjustment_no }}</strong>
            ，请前往
            <button
              type="button"
              class="adjustment-page-link"
              @click="goToAdjustmentPage"
            >
              盘点调整
            </button>
            菜单查看详情
          </div>
          <div
            v-else
            class="related-adjustment-empty"
          >
            当前盘点单尚未生成调整单
          </div>
        </div>
      </div>
    </div>
      </transition>

    <!-- Overlay -->
    <div v-if="moreMenuOpen !== null" class="more-overlay" @click="moreMenuOpen = null"></div>
    <div v-if="showAddDialog || showImportDialog || showCheckDialog || showDiffConfirm || showGenerateAdjDialog || showEvidenceDialog" class="overlay" @click="showAddDialog = false; showImportDialog = false; showCheckDialog = false; showDiffConfirm = false; showGenerateAdjDialog = false; showEvidenceDialog = false"></div>

    <!-- Add Dialog -->
    <div v-if="showAddDialog" class="dialog">
      <div class="dialog-head">
        <span>新建盘点单</span>
        <button class="dialog-close" @click="showAddDialog = false">×</button>
      </div>
      <div class="dialog-body">
        <div class="form-field">
          <label>盘点范围 <span class="required">*</span></label>
          <select v-model="addForm.check_range">
            <option value="">请选择盘点范围</option>
            <option v-for="r in rangeOptions" :key="r.value" :value="r.value">{{ r.label }}</option>
          </select>
        </div>
        <div class="form-field">
          <label>盘点方式 <span class="required">*</span></label>
          <select v-model="addForm.check_type">
            <option v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
          <p class="form-hint">选择盘点范围后自动匹配，也可手动修改</p>
        </div>
        <div class="form-field">
          <label>指定执行人</label>
          <select v-model="addForm.executor">
            <option value="">不指定（保存为草稿）</option>
            <option v-for="e in executorOptions" :key="e" :value="e">{{ e }}</option>
          </select>
        </div>
        <div class="form-field">
          <label>计划盘点时间</label>
          <input v-model="addForm.plan_date" type="date" />
        </div>
        <div class="form-field">
          <label class="checkbox-label">
            <input type="checkbox" v-model="addForm.freeze_inventory" />
            <span>盘点期间锁定库存（防止盘点期间出入库导致账实不符）</span>
          </label>
        </div>
        <div class="form-field">
          <label>备注</label>
          <input v-model="addForm.notes" placeholder="说明本次盘点原因（选填）" />
        </div>
        <div class="form-field form-note">
          <p>盘点基准时间：创建盘点单时系统自动记录，账面数量以此时间点的库存快照为准</p>
        </div>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn" @click="showAddDialog = false">取消</button>
        <button class="tb-btn" @click="confirmAdd(true)">保存草稿</button>
        <button class="tb-btn primary" @click="confirmAdd(false)">确认下发</button>
      </div>
    </div>

    <!-- Import Dialog -->
    <div v-if="showImportDialog" class="dialog">
      <div class="dialog-head">
        <span>导入实盘结果</span>
        <button class="dialog-close" @click="showImportDialog = false">×</button>
      </div>
      <div class="dialog-body">
        <p class="import-hint" style="margin-bottom:12px; color:#587766;">导入的是某张盘点单下面的实盘数量，不直接修改库存。</p>
        <div class="import-row">
          <button class="tb-btn" @click="downloadCountTemplate">📥 下载实盘导入模板</button>
        </div>
        <div class="import-divider">───或───</div>
        <div class="import-row">
          <label class="file-select">
            <input ref="fileInputRef" type="file" accept=".xlsx,.xls" @change="handleImportFileChange" />
            <span class="file-select-btn">选择文件</span>
          </label>
          <span class="file-name">{{ importFileName || '未选择任何文件' }}</span>
        </div>
        <p class="import-hint">模板字段：盘点单号、物料编码、物料名称、批次号、库位编码、账面数量、实盘数量、盘点人、备注</p>
        <p class="import-hint" style="color:#c98a2e;">注意：导入时不能新增盘点范围外的物料</p>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn" @click="showImportDialog = false">取消</button>
        <button class="tb-btn primary" @click="confirmImport">确认导入</button>
      </div>
    </div>

    <!-- Check Dialog (录入实盘) -->
    <div v-if="showCheckDialog && checkTarget" class="dialog dialog-lg">
      <div class="dialog-head">
        <span>录入实盘 —{{ checkTarget.check_no }}</span>
        <button class="dialog-close" @click="showCheckDialog = false">×</button>
      </div>
      <div class="dialog-body">
        <table class="detail-table">
          <thead>
            <tr>
              <th>物料编码</th>
              <th>物料名称</th>
              <th>批次号</th>
              <th>库位</th>
              <th>账面数</th>
              <th>实盘数</th>
              <th>差异</th>
              <th>备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(line, li) in checkForm.items" :key="li">
              <td><span class="code-text">{{ line.item_code }}</span></td>
              <td>{{ line.item_name }}</td>
              <td><span class="batch-text">{{ line.batch_no }}</span></td>
              <td><span class="loc-text">{{ line.location_code }}</span></td>
              <td class="col-num">{{ line.sys_qty }}</td>
              <td><input v-model.number="line.actual_qty" type="number" min="0" class="check-input" placeholder="实盘数" /></td>
              <td class="col-num" :class="{ 'text-orange': line.diff_qty < 0, 'text-green': line.diff_qty > 0 }">{{ line.diff_qty !== null ? (line.diff_qty > 0 ? '+' : '') + line.diff_qty : '-' }}</td>
              <td><input v-model="line.remark" class="check-input-sm" placeholder="备注" /></td>
            </tr>
          </tbody>
        </table>
        <div class="check-summary">差异合计：<strong :class="calcDiffTotal(checkForm.items) < 0 ? 'text-orange' : 'text-green'">{{ calcDiffTotal(checkForm.items) }}</strong></div>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn" @click="showCheckDialog = false">取消</button>
        <button class="tb-btn primary" @click="confirmCheck">保存盘点结果</button>
      </div>
    </div>

    <!-- Diff Confirm Dialog -->
    <div v-if="showDiffConfirm" class="dialog dialog-sm">
      <div class="dialog-head">
        <span>确认差异</span>
        <button class="dialog-close" @click="showDiffConfirm = false">×</button>
      </div>
      <div class="dialog-body">
        <p class="confirm-text">确认盘点差异结果吗？确认后差异将标记为已确认状态，并可生成调整单。</p>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn" @click="showDiffConfirm = false">取消</button>
        <button class="tb-btn primary" @click="confirmDiff">确认</button>
      </div>
    </div>

    <!-- Generate Adjustment Dialog -->
    <div v-if="showGenerateAdjDialog" class="dialog dialog-sm">
      <div class="dialog-head">
        <span>生成调整单</span>
        <button class="dialog-close" @click="showGenerateAdjDialog = false">×</button>
      </div>
      <div class="dialog-body">
        <p class="confirm-text">将为盘点差异项自动生成盘点调整单。调整单不会直接修改库存，需要审核通过后才更新库存余额。</p>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn" @click="showGenerateAdjDialog = false">取消</button>
        <button class="tb-btn primary" @click="confirmGenerateAdj">确认生成</button>
      </div>
    </div>

    <!-- Evidence Dialog -->
    <div v-if="showEvidenceDialog && actionItem" class="dialog">
      <div class="dialog-head">
        <span>查看证据 —{{ actionItem.check_no }}</span>
        <button class="dialog-close" @click="showEvidenceDialog = false">×</button>
      </div>
      <div class="dialog-body">
        <div class="evidence-section">
          <div class="detail-subtitle">盘点单信息</div>
          <div class="meta-item"><span class="meta-label">盘点单号</span><span>{{ actionItem.check_no }}</span></div>
          <div class="meta-item"><span class="meta-label">盘点范围</span><span>{{ actionItem.check_range }}</span></div>
        </div>
        <div v-if="actionItem.evidence && actionItem.evidence.length" class="evidence-section">
          <div class="detail-subtitle">关联文件</div>
          <span v-for="f in actionItem.evidence" :key="f" class="evidence-file">{{ f }}</span>
        </div>
        <div v-else class="evidence-section">
          <p class="evidence-empty">暂无关联证据文件</p>
        </div>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn primary" @click="showEvidenceDialog = false">关闭</button>
      </div>
    </div>

    <!-- Toast -->
    <transition name="toast">
      <div v-if="toastMessage" class="toast">{{ toastMessage }}</div>
    </transition>
  </div>
</div>
</template>

<style scoped>
.chk-page { width: 100%; max-width: 1400px; margin: 0 auto; font-family: inherit; position: relative; padding: 0 16px; box-sizing: border-box; }
.chk-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; padding-top: 6px; }
.chk-header h1 { margin: 0; font-family: Georgia, "Times New Roman", "Songti SC", serif; font-size: clamp(26px, 3vw, 34px); font-weight: 600; color: #2a3a33; }
.chk-subtitle { margin: 6px 0 0; color: #7d8983; font-size: 14px; }
.chk-strip { display: flex; gap: 6px; flex-wrap: wrap; }

.strip-item { min-width: 70px; border: 1px solid #e8e8e8; border-radius: 8px; padding: 10px 14px; background: #fff; text-align: center; box-shadow: 0 1px 4px rgba(0,0,0,0.04); transition: box-shadow 0.15s; }
.strip-item.clickable { cursor: pointer; }
.strip-item.clickable:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.strip-item .num { display: block; font-size: 22px; font-weight: 700; color: #303133; line-height: 1.2; }
.strip-item .lbl { display: block; margin-top: 3px; color: #909399; font-size: 12px; }

.chk-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 14px; padding: 10px 16px; background: rgba(255,255,255,0.85); border: 1px solid #e2eae5; border-radius: 10px; flex-wrap: wrap; }
.toolbar-left { display: flex; gap: 8px; flex-shrink: 0; }
.toolbar-center { flex: 1; display: flex; justify-content: center; }
.toolbar-right { display: flex; gap: 8px; align-items: center; flex-shrink: 0; }
.toolbar-tabs { display: flex; gap: 4px; }

.tab-btn { border: 1px solid #cddbd3; background: #fff; padding: 5px 10px; font-size: 12px; cursor: pointer; border-radius: 6px; transition: all 0.12s; white-space: nowrap; }
.tab-btn:hover { filter: brightness(0.95); }

.tb-btn { border: 1px solid #cddbd3; border-radius: 8px; padding: 7px 16px; background: #fff; color: #567566; font-size: 13px; cursor: pointer; transition: all 0.12s; white-space: nowrap; }
.tb-btn:hover { background: #eef4f0; }
.tb-btn.primary { background: #587766; border-color: #587766; color: #fff; }
.tb-btn.primary:hover { background: #4a6a59; }
.tb-btn.primary.danger { background: #c0392b; border-color: #c0392b; }
.tb-btn.primary.danger:hover { background: #a93226; }

.tb-search { border: 1px solid #dfe6e1; border-radius: 8px; padding: 7px 14px; font-size: 13px; color: #33473f; outline: none; background: #fff; width: 180px; }
.tb-search::placeholder { color: #bccbc2; }

.adv-filter-panel { margin-top: 10px; border: 1px solid #e2eae5; border-radius: 10px; padding: 16px; background: #fff; }
.adv-filter-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.adv-field-wide { grid-column: 1 / -1; }
.adv-field label { display: block; font-size: 12px; color: #8b958f; margin-bottom: 4px; }
.adv-field input, .adv-field select { width: 100%; border: 1px solid #dfe6e1; border-radius: 6px; padding: 6px 10px; font-size: 13px; color: #33473f; outline: none; background: #fff; box-sizing: border-box; }
.adv-check-group { display: flex; gap: 12px; flex-wrap: wrap; }
.adv-check { display: flex; align-items: center; gap: 4px; font-size: 13px; color: #46544f; cursor: pointer; }
.adv-check input { accent-color: #587766; }
.adv-date-range { display: flex; gap: 8px; align-items: center; }
.adv-date-range input { flex: 1; }
.adv-date-range span { color: #8b958f; font-size: 12px; }
.adv-filter-actions { display: flex; gap: 8px; margin-top: 12px; justify-content: flex-end; }

.stocktake-list { width: 100%; min-width: 0; overflow: hidden; }

.table-detail-btn {
  height: 30px;
  padding: 0 13px;
  border: 1px solid #d6e5dc;
  border-radius: 8px;
  color: #567366;
  background: #f1f6f3;
  border-color: transparent;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: 0.18s ease;
  appearance: none;
  -webkit-appearance: none;
  box-shadow: none;
}
.table-detail-btn:hover {
  color: #fff;
  border-color: #587d6b;
  background: #587d6b;
}

.adjustment-link {
  max-width: 210px;
  padding: 0;
  overflow: hidden;
  border: 0;
  outline: none;
  color: #3f7692;
  background: transparent;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  text-decoration: underline;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
  box-shadow: none;
}
.adjustment-link:hover {
  color: #2d5f78;
}

.action-btn {
  height: 30px;
  padding: 0 12px;
  border-radius: 7px;
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
  box-shadow: none;
  transition: 0.18s ease;
}

.code-text { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; color: #31433c; font-weight: 600; }
.batch-text { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; color: #6c7d75; }
.loc-text { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; color: #437a9e; font-weight: 500; }
.text-green { color: #3E8E41; font-weight: 600; }
.text-orange { color: #c98a2e; font-weight: 600; }
.col-num { text-align: right; }

.status-tag { display: inline-flex; align-items: center; gap: 5px; border-radius: 999px; padding: 3px 10px; font-size: 12px; font-weight: 500; white-space: nowrap; }
.status-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }

.more-btn { background: #fff; color: #567566; border: 1px solid #cddbd3; }
.more-btn:hover { background: #f0f5f2; }
.more-wrap { position: relative; display: inline-block; }
.more-toolbar-dropdown { position: absolute; left: 0; top: 100%; z-index: 100; min-width: 160px; overflow: hidden; background: #fff; border: 1px solid #e2eae5; border-radius: 8px; margin-top: 4px; box-shadow: 0 4px 16px rgba(0,0,0,0.1); }
.drop-item { display: block; width: 100%; border: 0; background: transparent; padding: 9px 16px; font-size: 13px; color: #46544f; text-align: left; cursor: pointer; transition: background 0.1s; }
.drop-item:hover { background: #f0f5f2; }
.drop-item + .drop-item { border-top: 1px solid #ecf1ee; }
.drop-item.danger { color: #C23531; }
.drop-item.danger:hover { background: #FFEBEE; }

.stocktake-detail-table-wrap { width: 100%; max-height: 280px; overflow: auto; border: 1px solid #e2ebe6; border-radius: 10px; box-sizing: border-box; }
.stocktake-detail-table { width: 100%; min-width: 850px; table-layout: fixed; border-collapse: collapse; }
.stocktake-detail-table th, .stocktake-detail-table td { height: 46px; padding: 0 14px; box-sizing: border-box; border-bottom: 1px solid #e5ece8; vertical-align: middle; font-size: 13px; color: #3e554b; }
.stocktake-detail-table th { height: 44px; color: #587066; background: #eef4f1; font-weight: 600; text-align: left; white-space: nowrap; position: sticky; top: 0; z-index: 2; }
.stocktake-detail-table thead th:first-child { border-radius: 10px 0 0 0; }
.stocktake-detail-table thead th:last-child { border-radius: 0 10px 0 0; }
.stocktake-detail-table tbody tr:hover { background: #fafcfb; }
.stocktake-detail-table td { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.stocktake-detail-table .number-column { text-align: right; font-variant-numeric: tabular-nums; }
.difference-value { font-weight: 700; }
.difference-value.negative { color: #c94b3c; }
.difference-value.positive { color: #3d8b5d; }
.empty-table-cell { height: 110px !important; color: #97a39d !important; text-align: center !important; }
.stocktake-detail-table-wrap::-webkit-scrollbar { width: 6px; height: 6px; }
.stocktake-detail-table-wrap::-webkit-scrollbar-thumb { border-radius: 999px; background: #c7d5ce; }



.detail-panel { width: 100%; max-width: 100%; margin-top: 18px; box-sizing: border-box; overflow: hidden; border: 1px solid #dbe7e1; border-radius: 14px; background: #fff; }
.detail-head { display: flex; align-items: center; justify-content: space-between; min-height: 58px; padding: 0 24px; box-sizing: border-box; background: #f8fbf9; border-bottom: 1px solid #e2eae5; }
.detail-title { display: flex; align-items: center; gap: 10px; }
.detail-title strong { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 15px; color: #2a3a33; }
.detail-close { border: 0; background: transparent; font-size: 20px; color: #8b958f; cursor: pointer; padding: 0 4px; line-height: 1; }
.detail-tabs { width: 100%; height: 58px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); align-items: stretch; border-bottom: 1px solid #dfe8e3; overflow: hidden; }
.detail-tab { appearance: none; min-width: 0; height: 58px; padding: 0 12px; border: 0; border-bottom: 3px solid transparent; color: #71837a; background: #fff; font-family: inherit; font-size: 14px; font-weight: 500; white-space: nowrap; cursor: pointer; }
.detail-tab:hover { color: #476858; background: #fafcfb; }
.detail-tab.active { color: #3e6553; border-bottom-color: #557c69; font-weight: 600; }
.detail-body { width: 100%; padding: 28px; box-sizing: border-box; }
.detail-meta { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px 80px; }
.meta-item { display: grid; grid-template-columns: 120px minmax(0, 1fr); align-items: center; gap: 8px; font-size: 13px; color: #46544f; }
.meta-item-wide { grid-column: 1 / -1; }
.meta-label { color: #8b958f; }
.detail-subtitle { font-weight: 600; font-size: 14px; color: #2c3b34; margin-bottom: 10px; }
.detail-section { margin-bottom: 8px; }
.detail-section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.detail-table { width: 100%; min-width: 850px; table-layout: fixed; border-collapse: collapse; }
.detail-table th, .detail-table td { height: 46px; padding: 0 14px; border-bottom: 1px solid #e5ece8; box-sizing: border-box; vertical-align: middle; font-size: 13px; color: #3e554b; }
.detail-table th { position: sticky; top: 0; z-index: 2; height: 44px; background: #eef4f1; color: #587066; font-weight: 600; text-align: left; white-space: nowrap; }
.detail-table td { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.detail-table .number-cell { text-align: right; font-variant-numeric: tabular-nums; }

.detail-diff-summary { margin-bottom: 14px; padding: 13px 16px; border-radius: 9px; background: #f6f9f7; font-size: 14px; color: #42584e; }
.detail-diff-summary p { margin: 0; }

.related-adjustment-content {
  padding: 28px 32px 32px;
}

.related-adjustment-notice {
  min-height: 64px;
  padding: 0 22px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  border-radius: 10px;
  color: #314b3f;
  background: #eaf5ec;
  font-size: 15px;
}

.related-adjustment-notice strong {
  color: #173d2d;
  font-size: 16px;
}

.adjustment-page-link {
  appearance: none;
  padding: 0;
  border: 0;
  color: #315f4b;
  background: transparent;
  font: inherit;
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}

.adjustment-page-link:hover {
  color: #244b3a;
}

.related-adjustment-empty {
  min-height: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  color: #89968f;
  background: #f7f9f8;
}

.overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.3); z-index: 200; }
.dialog { position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); z-index: 300; background: #fff; border-radius: 12px; box-shadow: 0 8px 32px rgba(0,0,0,0.2); width: 520px; max-width: 90vw; max-height: 85vh; display: flex; flex-direction: column; }
.dialog-sm { width: 400px; }
.dialog-lg { width: 90vw; max-width: 1000px; }
.dialog-head { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; border-bottom: 1px solid #ecf1ee; font-weight: 600; font-size: 15px; color: #2a3a33; }
.dialog-close { border: 0; background: transparent; font-size: 18px; color: #8b958f; cursor: pointer; padding: 0 4px; line-height: 1; }
.dialog-body { padding: 20px; overflow-y: auto; flex: 1; }
.dialog-foot { display: flex; gap: 8px; justify-content: flex-end; padding: 14px 20px; border-top: 1px solid #ecf1ee; }

.form-field { margin-bottom: 14px; }
.form-field label { display: block; font-size: 13px; font-weight: 500; color: #46544f; margin-bottom: 5px; }
.form-field .required { color: #d97a6b; }
.form-field input, .form-field select { width: 100%; border: 1px solid #dfe6e1; border-radius: 6px; padding: 7px 10px; font-size: 13px; color: #33473f; outline: none; background: #fff; box-sizing: border-box; }
.form-field input:focus, .form-field select:focus { border-color: #587766; }
.form-hint { font-size: 11px; color: #bccbc2; margin: 3px 0 0; }
.checkbox-label { display: flex !important; align-items: center; gap: 8px; cursor: pointer; font-weight: 400 !important; }
.checkbox-label input { accent-color: #587766; width: 16px; height: 16px; }
.form-note { background: #f8fbf9; border-radius: 6px; padding: 8px 12px; margin-top: 4px; }
.form-note p { margin: 0; font-size: 12px; color: #8b958f; }

.confirm-text { font-size: 14px; color: #46544f; line-height: 1.6; margin: 8px 0; }
.confirm-text strong { color: #2a3a33; }

.check-input { width: 80px; border: 1px solid #dfe6e1; border-radius: 4px; padding: 4px 6px; font-size: 13px; text-align: right; outline: none; }
.check-input:focus { border-color: #587766; }
.check-input-sm { width: 100px; border: 1px solid #dfe6e1; border-radius: 4px; padding: 4px 6px; font-size: 12px; outline: none; }
.check-input-sm:focus { border-color: #587766; }
.check-summary { margin-top: 12px; padding: 8px 12px; background: #f8fbf9; border-radius: 6px; font-size: 14px; text-align: right; }

.import-row { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.import-divider { text-align: center; color: #bccbc2; font-size: 13px; margin: 12px 0; }
.file-select { position: relative; display: inline-block; cursor: pointer; }
.file-select input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.file-select-btn { display: inline-block; border: 1px solid #cddbd3; border-radius: 8px; padding: 7px 16px; background: #fff; color: #567566; font-size: 13px; cursor: pointer; transition: all 0.12s; }
.file-select-btn:hover { background: #eef4f0; }
.file-name { font-size: 13px; color: #6c7d75; }
.import-hint { font-size: 12px; color: #bccbc2; margin-top: 8px; }

.evidence-section { margin-bottom: 16px; }
.evidence-file { display: inline-block; border: 1px solid #e2eae5; border-radius: 6px; padding: 6px 12px; font-size: 13px; color: #437a9e; background: #f0f5f2; margin: 0 6px 6px 0; }
.evidence-empty { color: #9caaa3; font-size: 13px; }

.more-overlay { position: fixed; inset: 0; z-index: 99; background: transparent; }

.toast { position: fixed; bottom: 40px; left: 50%; transform: translateX(-50%); z-index: 999; padding: 10px 24px; background: #2a3a33; color: #fff; border-radius: 8px; font-size: 13px; box-shadow: 0 4px 16px rgba(0,0,0,0.25); pointer-events: none; white-space: nowrap; }

.slide-enter-active, .slide-leave-active { transition: all 0.25s ease; }
.slide-enter-from, .slide-leave-to { opacity: 0; transform: translateY(-10px); }
.toast-enter-active, .toast-leave-active { transition: opacity 0.25s, transform 0.25s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(10px); }

.dialog-body::-webkit-scrollbar { width: 5px; }
.dialog-body::-webkit-scrollbar-track { background: transparent; }
.dialog-body::-webkit-scrollbar-thumb { background: #cddbd3; border-radius: 3px; }

/* ── 紧凑表格 ── */
.table-card {
  width: 100%;
  min-width: 0;
  border: 1px solid #dfe8e3;
  border-radius: 14px;
  background: #fff;
}

.table-scroll-window {
  position: relative;
  width: 100%;
  overflow-x: auto;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.adjustment-table-window { max-height: 360px; }
.stocktake-table-window { max-height: 430px; }

.compact-table {
  width: 100%;
  min-width: 1180px;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
}

.compact-table th,
.compact-table td,
.stocktake-table th,
.stocktake-table td,
.adjustment-table th,
.adjustment-table td {
  height: 48px;
  padding: 0 12px;
  box-sizing: border-box;
  border-bottom: 1px solid #e4ece8;
  color: #3d554a;
  font-size: 13px;
  line-height: 1.35;
  vertical-align: middle;
}

.compact-table th,
.stocktake-table th,
.adjustment-table th {
  position: sticky;
  top: 0;
  z-index: 5;
  height: 44px;
  color: #587066;
  background: #eef4f1;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.compact-table tbody tr:last-child td {
  border-bottom: 0;
}

.compact-table tbody tr:hover {
  background: #f8fbf9;
}

.compact-table td {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.original-more-btn {
  appearance: none;
  -webkit-appearance: none;
  height: 34px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid #c8d9d0;
  border-radius: 8px;
  color: #4e6e60;
  background: #ffffff;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  box-shadow: none;
  transition: 0.18s ease;
}
.original-more-btn:hover {
  color: #3f6554;
  border-color: #829f90;
  background: #f2f7f4;
}
.original-more-btn:focus-visible {
  outline: 2px solid rgba(83, 124, 105, 0.2);
  outline-offset: 2px;
}
.more-arrow {
  font-size: 9px;
  line-height: 1;
}

.action-column,
.action-cell {
  position: static;
  z-index: auto;
  width: 110px;
  min-width: 110px;
  text-align: center;
  overflow: visible !important;
  pointer-events: auto;
  background: transparent;
  box-shadow: none;
}

.action-cell,
.action-cell *,
.original-more-btn {
  pointer-events: auto;
}

.stocktake-table .status-tag {
  height: 25px;
  padding: 0 10px;
  font-size: 12px;
}

.stocktake-table .action-cell {
  width: 105px;
  text-align: center;
}

.compact-table .status-tag,
.compact-table .type-tag,
.compact-table .source-tag {
  min-width: auto;
  height: 25px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 25px;
  display: inline-block;
}

.compact-table .more-button,
.compact-table .detail-button,
.compact-table .action-button {
  height: 30px;
  padding: 0 12px;
  border-radius: 7px;
  font-size: 12px;
  white-space: nowrap;
}

.compact-table .order-no {
  font-size: 13px;
  font-weight: 600;
}

.compact-table .date-cell {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
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

/* ── 紧凑表格滚动条 ── */
.table-scroll-window::-webkit-scrollbar {
  width: 7px;
  height: 7px;
}
.table-scroll-window::-webkit-scrollbar-track {
  background: transparent;
}
.table-scroll-window::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #cedbd4;
}
.table-scroll-window::-webkit-scrollbar-thumb:hover {
  background: #aebfb6;
}
.table-scroll-window {
  scrollbar-width: thin;
  scrollbar-color: #cedbd4 transparent;
}

</style>
