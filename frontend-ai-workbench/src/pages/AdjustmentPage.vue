<script setup>
import { ref, computed, reactive, onMounted, nextTick } from 'vue'
import * as XLSX from 'xlsx'
import { useInventory } from '../composables/useInventory'
import { getAuthToken, apiGet } from '../api'

const { doLogin, getAdjustmentList, createManualAdjustment, approveAdjustment, rejectAdjustment } = useInventory()

const searchQuery = ref('')
const pageSize = ref(20)
const currentPage = ref(1)
const statusFilter = ref('')
const moreMenuOpen = ref(null)


const showAdvancedFilter = ref(false)
const advancedFilter = reactive({
  adjustment_no: '', adjustment_reason: '', adjustment_statuses: [],
  created_at_from: '', created_at_to: '',
})

const showAddDialog = ref(false)
const showImportDialog = ref(false)
const showEditDialog = ref(false)

const showExportDialog = ref(false)
const exportScope = ref('filtered')
const exportColumns = reactive({
  adjustment_no: true, adjustment_reason: true, status: true,
  created_by: true, created_at: true, approved_by: true, approved_at: true,
})

const showDeleteConfirm = ref(false)
const showEvidenceDialog = ref(false)
const showWithdrawConfirm = ref(false)

const addForm = reactive({
  adjustment_type: '', adjustment_reason: '', related_order: '',
  lines: [],
})
const addLineForm = reactive({ item_code: '', batch_no: '', difference_qty: '' })

const editForm = reactive({ adjustment_type: '', adjustment_reason: '', related_order: '', lines: [] })
const editLineForm = reactive({ item_code: '', batch_no: '', location_code: '', difference_qty: '' })
const editTarget = ref(null)
const editReadonly = ref(false)
const deleteTarget = ref(null)
const withdrawTarget = ref(null)
const actionItem = ref(null)
const evidenceOrder = ref({})
const evidenceLines = ref([])
const evidenceLoading = ref(false)

const importFile = ref(null)
const importFileName = ref('')
const fileInputRef = ref(null)

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

const reasonOptions = ['入库重复过账', '移库未过账', '质检不合格扣减', '系统初始化修正', '其他异常调整']

function mapAdjustmentData(o) {
  return {
    adjustment_no: o.adjustmentNo || '',
    adjustment_reason: o.adjustmentReason || '',
    adjustment_status: o.adjustmentStatus || 'DRAFT',
    created_by: o.createdBy || '',
    created_at: o.createdAt ? o.createdAt.replace('T', ' ').slice(0, 16) : '',
    approved_by: o.approvedBy || null,
    approved_at: o.approvedAt ? o.approvedAt.replace('T', ' ').slice(0, 16) : null,
    lines: (o.adjustmentLines || []).map(l => ({
      item_code: l.itemCode || '',
      item_name: l.itemName || '',
      batch_no: l.batchNo || '',
      location_code: l.locationCode || '',
      difference_qty: l.adjustmentQty ?? 0,
    })),
    evidence: o.evidence || [],
    related_order: o.relatedOrder || null,
    adjustmentId: o.adjustmentId,
  }
}

async function fetchFromApi() {
  if (!getAuthToken()) {
    await doLogin('admin', 'admin123')
  }
  const params = {}
  if (statusFilter.value && statusFilter.value !== 'ALL') {
    params.status = statusFilter.value
  }
  const response = await getAdjustmentList(params.status || null)
  if (response && response.length) {
    orders.value = response.map(mapAdjustmentData)
  }
}

onMounted(fetchFromApi)

const orders = ref([])

const statusMap = {
  DRAFT: { label: '草稿', cls: 'status-draft', bg: '#F4F4F5', color: '#8C8C8C' },
  PENDING: { label: '待提交', cls: 'status-pending', bg: '#FFF3E0', color: '#D4880F' },
  PENDING_APPROVAL: { label: '待审核', cls: 'status-review', bg: '#FFF3E0', color: '#D4880F' },
  APPROVED: { label: '已通过', cls: 'status-approved', bg: '#E8F5E9', color: '#3E8E41' },
  REJECTED: { label: '已驳回', cls: 'status-rejected', bg: '#FFEBEE', color: '#C23531' },
  COMPLETED: { label: '已完成', cls: 'status-completed', bg: '#E8F5E9', color: '#3E8E41' },
  CANCELLED: { label: '已取消', cls: 'status-cancelled', bg: '#F4F4F5', color: '#8C8C8C' },
}

const tabConfig = {
  '': { label: '全部', color: '#587766', unselectedColor: '#303133' },
  DRAFT: { label: '草稿', color: '#8C8C8C', unselectedColor: '#8C8C8C' },
  PENDING: { label: '待提交', color: '#D4880F', unselectedColor: '#D4880F' },
  PENDING_APPROVAL: { label: '待审核', color: '#D4880F', unselectedColor: '#D4880F' },
  APPROVED: { label: '已通过', color: '#3E8E41', unselectedColor: '#3E8E41' },
  REJECTED: { label: '已驳回', color: '#C23531', unselectedColor: '#C23531' },
  COMPLETED: { label: '已完成', color: '#3E8E41', unselectedColor: '#3E8E41' },
}

const filteredData = computed(() => {
  let list = orders.value
  if (statusFilter.value) list = list.filter(o => o.adjustment_status === statusFilter.value)
  const q = searchQuery.value.trim().toLowerCase()
  if (q) list = list.filter(o => o.adjustment_no.toLowerCase().includes(q))
  const af = advancedFilter
  if (af.adjustment_no) list = list.filter(o => o.adjustment_no.toLowerCase().includes(af.adjustment_no.toLowerCase()))
  if (af.adjustment_reason) list = list.filter(o => o.adjustment_reason === af.adjustment_reason)
  if (af.adjustment_statuses.length) list = list.filter(o => af.adjustment_statuses.includes(o.adjustment_status))
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
  const list = orders.value
  return {
    total: list.length,
    draft: list.filter(o => o.adjustment_status === 'DRAFT').length,
    pending: list.filter(o => o.adjustment_status === 'PENDING').length,
    pendingApproval: list.filter(o => o.adjustment_status === 'PENDING_APPROVAL').length,
    approved: list.filter(o => o.adjustment_status === 'APPROVED').length,
    rejected: list.filter(o => o.adjustment_status === 'REJECTED').length,
    completed: list.filter(o => o.adjustment_status === 'COMPLETED').length,
  }
})

const statCards = computed(() => [
  { label: '总调整单', value: stats.value.total, filterKey: '' },
  { label: '草稿', value: stats.value.draft, filterKey: 'DRAFT' },
  { label: '待提交', value: stats.value.pending, filterKey: 'PENDING' },
  { label: '待审核', value: stats.value.pendingApproval, filterKey: 'PENDING_APPROVAL' },
  { label: '已通过', value: stats.value.approved, filterKey: 'APPROVED' },
  { label: '已驳回', value: stats.value.rejected, filterKey: 'REJECTED' },
  { label: '已完成', value: stats.value.completed, filterKey: 'COMPLETED' },
])

function filterByStatus(key) {
  if (!key) return
  statusFilter.value = key
  currentPage.value = 1
}

function toggleMore(idx, event) {
  if (moreMenuOpen.value === idx) {
    moreMenuOpen.value = null
    return
  }
  moreMenuOpen.value = idx
  if (!event) return
  const btn = event.currentTarget
  const rect = btn.getBoundingClientRect()
  nextTick(() => {
    const dropdown = btn.parentElement.querySelector('.more-dropdown')
    if (dropdown) {
      dropdown.style.position = 'fixed'
      dropdown.style.top = 'auto'
      dropdown.style.bottom = (window.innerHeight - rect.top + 6) + 'px'
      dropdown.style.left = Math.max(8, rect.right - 200) + 'px'
      dropdown.style.right = 'auto'
    }
  })
}

function formatQty(value) {
  if (value === null || value === undefined) return '-'
  const n = Number(value)
  if (Number.isNaN(n)) return '-'
  const text = n.toFixed(3).replace(/\.?0+$/, '')
  return n > 0 ? '+' + text : String(text)
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
  advancedFilter.adjustment_no = ''
  advancedFilter.adjustment_reason = ''
  advancedFilter.adjustment_statuses = []
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
  const idx = advancedFilter.adjustment_statuses.indexOf(status)
  if (idx >= 0) advancedFilter.adjustment_statuses.splice(idx, 1)
  else advancedFilter.adjustment_statuses.push(status)
}

function openAddDialog() {
  addForm.adjustment_type = ''
  addForm.adjustment_reason = ''
  addForm.related_order = ''
  addForm.lines = []
  addLineForm.item_code = ''
  addLineForm.batch_no = ''
  addLineForm.difference_qty = ''
  showAddDialog.value = true
}

function addLine() {
  if (!addLineForm.item_code || !addLineForm.batch_no || !addLineForm.difference_qty) {
    showToast('请填写完整的物料信息')
    return
  }
  addForm.lines.push({
    item_code: addLineForm.item_code,
    batch_no: addLineForm.batch_no,
    location_code: 'A-00-00',
    difference_qty: parseInt(addLineForm.difference_qty) || 0,
  })
  addLineForm.item_code = ''
  addLineForm.batch_no = ''
  addLineForm.difference_qty = ''
}

function removeLine(idx) {
  addForm.lines.splice(idx, 1)
}

async function confirmAdd() {
  if (!addForm.adjustment_type && !addForm.adjustment_reason) {
    showToast('请选择调整类型或填写调整原因')
    return
  }
  const reason = addForm.adjustment_type || addForm.adjustment_reason
  const lines = addForm.lines.map(l => ({
    itemCode: l.item_code,
    batchNo: l.batch_no,
    locationCode: l.location_code || '',
    adjustmentQty: l.difference_qty || 0,
  }))
  try {
    const result = await createManualAdjustment(reason, lines, 1)
    if (result) {
      const mapped = {
        adjustment_no: 'ADJ-' + Date.now(),
        adjustment_reason: reason,
        adjustment_status: 'DRAFT',
        created_by: '韩库存',
        created_at: new Date().toISOString().slice(0, 16).replace('T', ' '),
        approved_by: null,
        approved_at: null,
        lines: addForm.lines.map(l => ({
          item_code: l.item_code,
          batch_no: l.batch_no,
          location_code: l.location_code || '',
          difference_qty: l.difference_qty || 0,
        })),
        evidence: [],
        related_order: addForm.related_order || null,
      }
      orders.value.unshift(mapped)
      showAddDialog.value = false
      showToast('新增成功！')
    }
  } catch (e) {
    showToast('创建失败')
  }
}

function handleImportFileChange(e) {
  const file = e.target.files[0]
  if (file) {
    importFile.value = file
    importFileName.value = file.name
  }
}

function confirmImport() {
  if (!importFile.value) {
    showToast('请先选择文件')
    return
  }
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const data = new Uint8Array(e.target.result)
      const workbook = XLSX.read(data, { type: 'array' })
      const sheet = workbook.Sheets[workbook.SheetNames[0]]
      const rows = XLSX.utils.sheet_to_json(sheet, { header: 1 })
      if (rows.length < 2) {
        showToast('导入失败，模板为空或格式错误')
        return
      }
      const imported = []
      for (let i = 1; i < rows.length; i++) {
        const row = rows[i]
        if (!row || !row[0]) continue
        const no = row[0].toString().trim()
        const reason = (row[1] && row[1].toString().trim()) || ''
        const statusText = (row[2] && row[2].toString().trim()) || '草稿'
        const creator = (row[3] && row[3].toString().trim()) || '韩库存'
        const time = (row[4] && row[4].toString().trim()) || new Date().toISOString().slice(0, 16).replace('T', ' ')
        const statusKey = Object.entries(statusMap).find(([, v]) => v.label === statusText)?.[0] || 'DRAFT'
        imported.push({
          adjustment_no: no,
          adjustment_reason: reason,
          adjustment_status: statusKey,
          created_by: creator,
          created_at: time,
          approved_by: null,
          approved_at: null,
          lines: [],
          evidence: [],
          related_order: null,
        })
      }
      if (!imported.length) {
        showToast('导入失败，未读取到有效数据')
        return
      }
      orders.value.unshift(...imported)
      showImportDialog.value = false
      importFile.value = null
      importFileName.value = ''
      if (fileInputRef.value) fileInputRef.value.value = ''
      showToast(`导入成功！共导入 ${imported.length} 条数据`)
    } catch (err) {
      showToast('导入失败，请检查模板格式')
    }
  }
  reader.readAsArrayBuffer(importFile.value)
}

function downloadTemplate() {
  const header = [['调整单号', '调整类型', '状态', '创建人', '创建时间']]
  const ws = XLSX.utils.aoa_to_sheet([header, ['ADJ-2026-07008', '入库重复过账', '草稿', '韩库存', '2026-07-10 14:00']])
  ws['!cols'] = [{ wch: 18 }, { wch: 14 }, { wch: 8 }, { wch: 8 }, { wch: 18 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '导入模板')
  XLSX.writeFile(wb, `调整明细导入模板.xlsx`)
  showToast('模板下载成功')
}

function handleExport() {
  showExportDialog.value = true
}

function confirmExport() {
  let rows
  if (exportScope.value === 'all') rows = orders.value
  else if (exportScope.value === 'current') rows = pagedData.value
  else rows = filteredData.value

  const colDefs = [
    { key: 'adjustment_no', label: '调整单号', fn: i => i.adjustment_no, active: exportColumns.adjustment_no },
    { key: 'adjustment_reason', label: '调整类型', fn: i => i.adjustment_reason, active: exportColumns.adjustment_reason },
    { key: 'status', label: '状态', fn: i => statusMap[i.adjustment_status]?.label || i.adjustment_status, active: exportColumns.status },
    { key: 'created_by', label: '创建人', fn: i => i.created_by, active: exportColumns.created_by },
    { key: 'created_at', label: '创建时间', fn: i => i.created_at, active: exportColumns.created_at },
    { key: 'approved_by', label: '审核人', fn: i => i.approved_by || '', active: exportColumns.approved_by },
    { key: 'approved_at', label: '审核时间', fn: i => i.approved_at || '', active: exportColumns.approved_at },
  ]
  const activeCols = colDefs.filter(c => c.active)
  if (!activeCols.length) {
    showToast('请至少选择一列')
    return
  }
  const header = activeCols.map(c => c.label)
  const data = rows.map(i => activeCols.map(c => c.fn(i)))
  const ws = XLSX.utils.aoa_to_sheet([header, ...data])
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '调整单')
  XLSX.writeFile(wb, `调整单列表_${formatDate()}.xlsx`)
  showExportDialog.value = false
  showToast('导出成功！')
}

function openEditDialog(item) {
  editTarget.value = item
  editReadonly.value = !canEdit(item.adjustment_status)
  editForm.adjustment_type = item.adjustment_reason
  editForm.adjustment_reason = item.adjustment_reason
  editForm.related_order = item.related_order || ''
  editForm.lines = item.lines.map(l => ({ ...l }))
  editLineForm.item_code = ''
  editLineForm.batch_no = ''
  editLineForm.location_code = ''
  editLineForm.difference_qty = ''
  showEditDialog.value = true
}

function canEdit(status) {
  return status === 'DRAFT' || status === 'PENDING'
}

function editAddLine() {
  if (!editLineForm.item_code || !editLineForm.difference_qty) {
    showToast('请填写物料编码和调整数量')
    return
  }
  editForm.lines.push({
    item_code: editLineForm.item_code,
    batch_no: editLineForm.batch_no,
    location_code: editLineForm.location_code || 'A-00-00',
    difference_qty: parseInt(editLineForm.difference_qty) || 0,
  })
  editLineForm.item_code = ''
  editLineForm.batch_no = ''
  editLineForm.location_code = ''
  editLineForm.difference_qty = ''
}

function editRemoveLine(idx) {
  editForm.lines.splice(idx, 1)
}

function confirmEdit() {
  if (editTarget.value) {
    editTarget.value.adjustment_reason = editForm.adjustment_type || editForm.adjustment_reason
    editTarget.value.related_order = editForm.related_order || null
    editTarget.value.lines = editForm.lines.map(l => ({ ...l }))
  }
  showEditDialog.value = false
  editTarget.value = null
  showToast('修改成功！')
}

async function submitReview(item) {
  item.adjustment_status = 'PENDING_APPROVAL'
  item.approved_by = null
  item.approved_at = null
  moreMenuOpen.value = null
  showToast('已提交审核')
}

async function approve(item) {
  if (item.adjustmentId) {
    try {
      await approveAdjustment(item.adjustmentId, 1)
    } catch (e) {
      showToast('审核通过失败: ' + (e.message || e))
      return
    }
  }
  item.adjustment_status = 'APPROVED'
  item.approved_by = '韩库存'
  item.approved_at = new Date().toISOString().slice(0, 16).replace('T', ' ')
  moreMenuOpen.value = null
  showToast('审核通过')
}

async function reject(item) {
  if (item.adjustmentId) {
    try {
      await rejectAdjustment(item.adjustmentId)
    } catch (e) {
      console.warn('rejectAdjustment failed', e)
    }
  }
  item.adjustment_status = 'REJECTED'
  moreMenuOpen.value = null
  showToast('已驳回')
}

async function viewEvidence(item) {
  if (!item.adjustmentId) {
    showToast('缺少调整单ID')
    return
  }
  evidenceLoading.value = true
  try {
    const res = await apiGet('/stocktake/adjustment/detail?adjustmentId=' + item.adjustmentId)
    evidenceOrder.value = res?.order ?? {}
    evidenceLines.value = Array.isArray(res?.lines) ? res.lines : []
    showEvidenceDialog.value = true
  } catch (e) {
    showToast('查询调整单详情失败')
    console.error(e)
  } finally {
    evidenceLoading.value = false
  }
}

function openWithdrawDialog(item) {
  withdrawTarget.value = item
  showWithdrawConfirm.value = true
  moreMenuOpen.value = null
}

function confirmWithdraw() {
  if (withdrawTarget.value) {
    withdrawTarget.value.adjustment_status = 'DRAFT'
  }
  showWithdrawConfirm.value = false
  withdrawTarget.value = null
  showToast('已撤回')
}

function canDelete(status) {
  return status === 'DRAFT'
}

function openDeleteDialog(item) {
  if (!canDelete(item.adjustment_status)) {
    showToast('仅草稿状态可删除')
    return
  }
  deleteTarget.value = item
  showDeleteConfirm.value = true
  moreMenuOpen.value = null
}

function confirmDelete() {
  if (deleteTarget.value) {
    const idx = orders.value.findIndex(o => o.adjustment_no === deleteTarget.value.adjustment_no)
    if (idx >= 0) orders.value.splice(idx, 1)
  }
  showDeleteConfirm.value = false
  deleteTarget.value = null
  showToast('删除成功')
}

async function handleAdjustmentCommand(command, row) {
  console.log('调整单操作：', command, row)
  if (!row) return
  switch (command) {
    case 'detail':
      await openAdjustmentDetail(row)
      break
    case 'evidence':
      await viewEvidence(row)
      break
    case 'inventory':
      await openRelatedInventory(row)
      break
    default:
      console.warn('未知操作：', command)
  }
}

function openAdjustmentDetail(row) {
  console.log('查看详情：', row)
}

function openRelatedInventory(row) {
  console.log('查看库存：', row)
}

</script>

<template>
  <div class="adj-page">
    <header class="adj-header">
      <div>
        <h1>盘点调整单管理</h1>
      </div>
      <div class="adj-strip">
        <div v-for="c in statCards" :key="c.label" class="strip-item" :class="{ clickable: c.filterKey }" @click="filterByStatus(c.filterKey)">
          <strong class="num">{{ c.value }}</strong>
          <span class="lbl">{{ c.label }}</span>
        </div>
      </div>
    </header>

    <div class="adj-toolbar">
      <div class="toolbar-left">
        <button class="tb-btn primary" @click="openAddDialog">新增调整单</button>
        <button class="tb-btn primary" @click="showImportDialog = true">导入调整明细</button>
        <button class="tb-btn primary" @click="handleExport">导出调整单</button>
        <button class="tb-btn primary" @click="showAdvancedFilter = !showAdvancedFilter">高级查询</button>
      </div>
      <div class="toolbar-center">
        <div class="toolbar-tabs">
          <button
            v-for="(cfg, key) in tabConfig"
            :key="key"
            :class="['tab-btn', { active: statusFilter === key }]"
            :style="statusFilter === key ? { background: cfg.color, borderColor: cfg.color, color: '#fff' } : { background: '#fff', borderColor: '#cddbd3', color: cfg.unselectedColor }"
            @click="statusFilter = key; currentPage = 1"
          >{{ cfg.label }}</button>
        </div>
      </div>
      <div class="toolbar-right">
        <input v-model="searchQuery" placeholder="搜索调整单号…" class="tb-search" />
      </div>
    </div>

    <transition name="slide">
      <div v-if="showAdvancedFilter" class="adv-filter-panel">
        <div class="adv-filter-grid">
          <div class="adv-field">
            <label>调整单号</label>
            <input v-model="advancedFilter.adjustment_no" placeholder="输入调整单号" />
          </div>
          <div class="adv-field">
            <label>调整类型</label>
            <select v-model="advancedFilter.adjustment_reason">
              <option value="">全部</option>
              <option v-for="r in reasonOptions" :key="r" :value="r">{{ r }}</option>
            </select>
          </div>
          <div class="adv-field adv-field-wide">
            <label>状态</label>
            <div class="adv-check-group">
              <label v-for="s in ['DRAFT', 'PENDING', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED']" :key="s" class="adv-check">
                <input type="checkbox" :checked="advancedFilter.adjustment_statuses.includes(s)" @change="toggleAdvStatus(s)" />
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

    <div class="table-card">
      <div class="table-scroll-window adjustment-table-window">
        <table class="compact-table adjustment-table">
          <colgroup>
            <col class="adjustment-no-column" style="width: 250px" />
            <col style="width: 170px" />
            <col style="width: 100px" />
            <col style="width: 110px" />
            <col style="width: 90px" />
            <col style="width: 170px" />
            <col style="width: 90px" />
            <col style="width: 170px" />
            <col style="width: 110px" />
          </colgroup>
          <thead>
            <tr>
              <th>调整单号</th>
              <th>调整类型</th>
              <th>来源</th>
              <th>状态</th>
              <th>创建人</th>
              <th>创建时间</th>
              <th>审核人</th>
              <th>审核时间</th>
              <th class="action-column">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in pagedData" :key="item.adjustment_no">
              <td><button type="button" class="adjustment-no-link" :title="item.adjustment_no" @click="openAdjustmentDetail(item)">{{ item.adjustment_no }}</button></td>
              <td>
                <span class="type-tag">{{ item.adjustment_reason }}</span>
              </td>
              <td>
                <span class="source-tag" :class="item.related_order ? 'from-stocktake' : 'from-manual'">{{ item.related_order ? '盘点' : '手动' }}</span>
              </td>
              <td>
                <span :class="['status-tag', statusMap[item.adjustment_status]?.cls]"
                  :style="{ background: statusMap[item.adjustment_status]?.bg, color: statusMap[item.adjustment_status]?.color }">
                  <i class="status-dot" :style="{ background: statusMap[item.adjustment_status]?.color }"></i>
                  {{ statusMap[item.adjustment_status]?.label }}
                </span>
              </td>
              <td>{{ item.created_by }}</td>
              <td>{{ item.created_at }}</td>
              <td>{{ item.approved_by || '—' }}</td>
              <td>{{ item.approved_at || '—' }}</td>
              <td class="adjustment-action-cell">
                <template v-if="item.adjustment_status === 'PENDING_APPROVAL'">
                  <button class="action-btn approve-btn" @click="approve(item)">通过</button>
                  <button class="action-btn reject-btn" @click="reject(item)">驳回</button>
                </template>
                <template v-else>
                  <el-dropdown
                    trigger="click"
                    placement="bottom-end"
                    :teleported="true"
                    popper-class="adjustment-more-dropdown"
                    @command="command => handleAdjustmentCommand(command, item)"
                  >
                    <button
                      type="button"
                      class="adjustment-more-button"
                      @click.stop
                    >
                      <span>更多</span>
                      <span class="adjustment-more-arrow">▼</span>
                    </button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="detail">查看详情</el-dropdown-item>
                        <el-dropdown-item command="evidence">查看证据</el-dropdown-item>
                        <el-dropdown-item command="inventory">查看库存</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </template>
              </td>
            </tr>
            <tr v-if="pagedData.length === 0">
              <td colspan="9" class="empty-cell">暂无调整单 — 盘点差异可通过「生成调整单」自动创建，非盘点原因需批量调账可手动新增调整单</td>
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

    <!-- Overlay -->
    <div v-if="moreMenuOpen !== null" class="more-overlay" @click="moreMenuOpen = null"></div>
    <div v-if="showAddDialog || showEditDialog || showEvidenceDialog || showImportDialog || showExportDialog || showDeleteConfirm || showWithdrawConfirm" class="overlay" @click="showAddDialog = false; showEditDialog = false; showEvidenceDialog = false; showImportDialog = false; showExportDialog = false; showDeleteConfirm = false; showWithdrawConfirm = false"></div>

    <!-- Add Dialog -->
    <div v-if="showAddDialog" class="dialog">
      <div class="dialog-head">
        <span>新增调整单</span>
        <button class="dialog-close" @click="showAddDialog = false">✕</button>
      </div>
      <div class="dialog-body">
        <p class="import-hint" style="margin-bottom:12px; color:#587766;">手动新增调整单适用于非盘点原因的库存修正场景，如：入库重复过账、移库未过账、质检不合格扣减等。</p>
        <div class="form-field">
          <label>调整类型 <span class="required">*</span></label>
          <select v-model="addForm.adjustment_type">
            <option value="">请选择</option>
            <option v-for="r in reasonOptions" :key="r" :value="r">{{ r }}</option>
          </select>
        </div>
        <div class="form-field">
          <label>调整原因 <span class="required">*</span></label>
          <input v-model="addForm.adjustment_reason" placeholder="输入调整原因" />
        </div>
        <div class="form-field">
          <label>关联盘点单</label>
          <input v-model="addForm.related_order" placeholder="选填，输入关联盘点单号" />
        </div>
        <div class="form-section-title">调整明细</div>
        <div class="form-lines">
          <div v-for="(line, li) in addForm.lines" :key="li" class="form-line-row">
            <span class="line-code">{{ line.item_code }}</span>
            <span class="line-batch">{{ line.batch_no }}</span>
            <span class="line-qty" :class="line.difference_qty >= 0 ? 'pos' : 'neg'">{{ line.difference_qty >= 0 ? '+' : '' }}{{ line.difference_qty }}</span>
            <button class="line-del" @click="removeLine(li)">✕</button>
          </div>
        </div>
        <div class="form-line-add">
          <input v-model="addLineForm.item_code" placeholder="物料编码" class="line-input" />
          <input v-model="addLineForm.batch_no" placeholder="批次号" class="line-input" />
          <input v-model="addLineForm.difference_qty" placeholder="调整数量" type="number" class="line-input line-input-sm" />
          <button class="add-line-btn" @click="addLine">+ 添加明细</button>
        </div>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn" @click="showAddDialog = false">取消</button>
        <button class="tb-btn primary" @click="confirmAdd">确认</button>
      </div>
    </div>

    <!-- Import Dialog -->
    <div v-if="showImportDialog" class="dialog">
      <div class="dialog-head">
        <span>导入调整明细</span>
        <button class="dialog-close" @click="showImportDialog = false">✕</button>
      </div>
      <div class="dialog-body">
        <p class="import-hint" style="margin-bottom:12px; color:#587766;">导入的是非盘点原因需要批量调账的调整明细（如：入库重复过账、移库未过账、质检不合格扣减等）。</p>
        <div class="import-row">
          <button class="tb-btn" @click="downloadTemplate">📥 下载导入模板</button>
        </div>
        <div class="import-divider">— 或 —</div>
        <div class="import-row">
          <label class="file-select">
            <input ref="fileInputRef" type="file" accept=".xlsx,.xls" @change="handleImportFileChange" />
            <span class="file-select-btn">选择文件</span>
          </label>
          <span class="file-name">{{ importFileName || '未选择任何文件' }}</span>
        </div>
        <p class="import-hint">模板字段：调整单号、调整类型、状态、创建人、创建时间</p>
        <p class="import-hint" style="color:#c98a2e;">注意：盘点差异产生的调整单优先从盘点单自动生成，不建议通过导入创建</p>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn" @click="showImportDialog = false">取消</button>
        <button class="tb-btn primary" @click="confirmImport">确认导入</button>
      </div>
    </div>

    <!-- Edit Dialog -->
    <div v-if="showEditDialog && editTarget" class="dialog">
      <div class="dialog-head">
        <span>编辑调整单</span>
        <button class="dialog-close" @click="showEditDialog = false">✕</button>
      </div>
      <div class="dialog-body">
        <div v-if="editReadonly" class="readonly-hint">该状态不可编辑</div>
        <div class="form-field">
          <label>调整单号</label>
          <span class="form-static">{{ editTarget.adjustment_no }}</span>
        </div>
        <div class="form-field">
          <label>调整类型</label>
          <select v-model="editForm.adjustment_type" :disabled="editReadonly">
            <option v-for="r in reasonOptions" :key="r" :value="r">{{ r }}</option>
          </select>
        </div>
        <div class="form-field">
          <label>调整原因</label>
          <input v-model="editForm.adjustment_reason" placeholder="输入调整原因" :readonly="editReadonly" :class="{ readonly: editReadonly }" />
        </div>
        <div class="form-field">
          <label>关联盘点单</label>
          <input v-model="editForm.related_order" placeholder="选填，输入关联盘点单号" :readonly="editReadonly" :class="{ readonly: editReadonly }" />
        </div>
        <div class="form-field">
          <label>状态</label>
          <span class="form-static">
            <span :class="['status-tag', statusMap[editTarget.adjustment_status]?.cls]"
              :style="{ background: statusMap[editTarget.adjustment_status]?.bg, color: statusMap[editTarget.adjustment_status]?.color }">
              {{ statusMap[editTarget.adjustment_status]?.label }}
            </span>
          </span>
        </div>
        <div class="form-section-title">调整明细</div>
        <div class="form-lines">
          <div v-for="(line, li) in editForm.lines" :key="li" class="form-line-row">
            <span class="line-code">{{ line.item_code }}</span>
            <span class="line-batch">{{ line.batch_no }}</span>
            <span class="line-loc">{{ line.location_code }}</span>
            <span class="line-qty" :class="line.difference_qty >= 0 ? 'pos' : 'neg'">{{ line.difference_qty >= 0 ? '+' : '' }}{{ line.difference_qty }}</span>
            <button v-if="!editReadonly" class="line-del" @click="editRemoveLine(li)">✕</button>
          </div>
        </div>
        <div v-if="!editReadonly" class="form-line-add">
          <input v-model="editLineForm.item_code" placeholder="物料编码" class="line-input" />
          <input v-model="editLineForm.batch_no" placeholder="批次号" class="line-input" />
          <input v-model="editLineForm.location_code" placeholder="库位" class="line-input line-input-sm" />
          <input v-model="editLineForm.difference_qty" placeholder="调整数量" type="number" class="line-input line-input-sm" />
          <button class="add-line-btn" @click="editAddLine">+ 添加明细</button>
        </div>
      </div>
      <div class="dialog-foot">
        <button v-if="!editReadonly" class="tb-btn" @click="showEditDialog = false">取消</button>
        <button v-if="!editReadonly" class="tb-btn primary" @click="confirmEdit">确认</button>
        <button v-if="editReadonly" class="tb-btn primary" @click="showEditDialog = false">关闭</button>
      </div>
    </div>

    <!-- Evidence Dialog -->
    <div v-if="showEvidenceDialog" class="dialog">
      <div class="dialog-head">
        <span>查看证据 — {{ evidenceOrder.adjustmentNo || evidenceOrder.adjustment_no || '-' }}</span>
        <button class="dialog-close" @click="showEvidenceDialog = false">✕</button>
      </div>
      <div class="dialog-body">
        <p class="evidence-reason"><strong>调整原因：</strong>{{ evidenceOrder.adjustmentReason || evidenceOrder.adjustment_reason || '-' }}</p>
        <div class="detail-subtitle">盘点差异明细</div>
        <table class="detail-table">
          <thead>
            <tr>
              <th>物料编码</th>
              <th>物料名称</th>
              <th>批次号</th>
              <th>库位编码</th>
              <th>调整数量</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="line in evidenceLines" :key="line.adjustmentLineId">
              <td>{{ line.itemCode || '-' }}</td>
              <td>{{ line.itemName || '-' }}</td>
              <td>{{ line.batchNo || '-' }}</td>
              <td>{{ line.locationCode || '-' }}</td>
              <td :class="line.adjustmentQty > 0 ? 'text-green' : 'text-orange'">{{ formatQty(line.adjustmentQty) }}</td>
            </tr>
            <tr v-if="evidenceLines.length === 0 && !evidenceLoading">
              <td colspan="5" class="empty-cell">暂无盘点差异明细</td>
            </tr>
            <tr v-if="evidenceLoading">
              <td colspan="5" class="empty-cell">加载中...</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn primary" @click="showEvidenceDialog = false">关闭</button>
      </div>
    </div>

    <!-- Withdraw Confirm -->
    <div v-if="showWithdrawConfirm" class="dialog dialog-sm">
      <div class="dialog-head">
        <span>确认撤回</span>
        <button class="dialog-close" @click="showWithdrawConfirm = false">✕</button>
      </div>
      <div class="dialog-body">
        <p class="confirm-text">确认撤回该调整单吗？撤回后状态将回到草稿。</p>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn" @click="showWithdrawConfirm = false">取消</button>
        <button class="tb-btn primary" @click="confirmWithdraw">确认撤回</button>
      </div>
    </div>

    <!-- Delete Confirm -->
    <div v-if="showDeleteConfirm" class="dialog dialog-sm">
      <div class="dialog-head">
        <span>确认删除</span>
        <button class="dialog-close" @click="showDeleteConfirm = false">✕</button>
      </div>
      <div class="dialog-body">
        <p class="confirm-text warn">确认删除该调整单吗？此操作不可恢复！</p>
        <p v-if="deleteTarget" class="confirm-target">{{ deleteTarget.adjustment_no }}</p>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn" @click="showDeleteConfirm = false">取消</button>
        <button class="tb-btn primary danger" @click="confirmDelete">确认删除</button>
      </div>
    </div>

    <!-- Export Dialog -->
    <div v-if="showExportDialog" class="dialog dialog-sm">
      <div class="dialog-head">
        <span>导出配置</span>
        <button class="dialog-close" @click="showExportDialog = false">✕</button>
      </div>
      <div class="dialog-body">
        <div class="export-section">
          <label class="export-section-label">导出范围</label>
          <label class="export-radio"><input type="radio" v-model="exportScope" value="filtered" /> 当前筛选数据（{{ filteredData.length }}条）</label>
          <label class="export-radio"><input type="radio" v-model="exportScope" value="all" /> 全部数据（{{ orders.length }}条）</label>
          <label class="export-radio"><input type="radio" v-model="exportScope" value="current" /> 当前页数据（{{ pagedData.length }}条）</label>
        </div>
        <div class="export-section">
          <label class="export-section-label">导出列</label>
          <div class="export-check-grid">
            <label class="export-check"><input type="checkbox" v-model="exportColumns.adjustment_no" /> 调整单号</label>
            <label class="export-check"><input type="checkbox" v-model="exportColumns.adjustment_reason" /> 调整类型</label>
            <label class="export-check"><input type="checkbox" v-model="exportColumns.status" /> 状态</label>
            <label class="export-check"><input type="checkbox" v-model="exportColumns.created_by" /> 创建人</label>
            <label class="export-check"><input type="checkbox" v-model="exportColumns.created_at" /> 创建时间</label>
            <label class="export-check"><input type="checkbox" v-model="exportColumns.approved_by" /> 审核人</label>
            <label class="export-check"><input type="checkbox" v-model="exportColumns.approved_at" /> 审核时间</label>
          </div>
        </div>
      </div>
      <div class="dialog-foot">
        <button class="tb-btn" @click="showExportDialog = false">取消</button>
        <button class="tb-btn primary" @click="confirmExport">确认导出</button>
      </div>
    </div>

    <!-- Toast -->
    <transition name="toast">
      <div v-if="toastMessage" class="toast">{{ toastMessage }}</div>
    </transition>
  </div>
</template>

<style scoped>
.adj-page {
  width: min(1200px, 100%);
  margin: 0 auto;
  font-family: inherit;
  position: relative;
}

.adj-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 6px;
}

.adj-header h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(26px, 3vw, 34px);
  font-weight: 600;
  color: #2a3a33;
}

.adj-subtitle {
  margin: 6px 0 0;
  color: #7d8983;
  font-size: 14px;
}

.adj-strip {
  display: flex;
  gap: 6px;
}

.strip-item {
  min-width: 80px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 12px 18px;
  background: #fff;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  transition: box-shadow 0.15s;
}

.strip-item.clickable {
  cursor: pointer;
}

.strip-item.clickable:hover {
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.strip-item .num {
  display: block;
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.strip-item .lbl {
  display: block;
  margin-top: 4px;
  color: #909399;
  font-size: 14px;
}

.adj-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 16px;
  padding: 10px 16px;
  background: rgba(255,255,255,0.85);
  border: 1px solid #e2eae5;
  border-radius: 10px;
  flex-wrap: wrap;
}

.toolbar-left { display: flex; gap: 8px; flex-shrink: 0; }
.toolbar-center { flex: 1; display: flex; justify-content: center; }
.toolbar-right { display: flex; gap: 8px; align-items: center; flex-shrink: 0; }

.toolbar-tabs {
  display: flex;
  gap: 4px;
}

.tab-btn {
  border: 1px solid #cddbd3;
  background: #fff;
  padding: 5px 10px;
  font-size: 12px;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.12s;
  white-space: nowrap;
}

.tab-btn:hover { filter: brightness(0.95); }

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

.tb-btn.primary.danger {
  background: #c0392b;
  border-color: #c0392b;
}

.tb-btn.primary.danger:hover { background: #a93226; }

.tb-search {
  border: 1px solid #dfe6e1;
  border-radius: 8px;
  padding: 7px 14px;
  font-size: 13px;
  color: #33473f;
  outline: none;
  background: #fff;
  width: 180px;
}

.tb-search::placeholder { color: #bccbc2; }

/* Advanced filter */
.adv-filter-panel {
  margin-top: 10px;
  border: 1px solid #e2eae5;
  border-radius: 10px;
  padding: 16px;
  background: #fff;
}

.adv-filter-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.adv-field-wide {
  grid-column: 1 / -1;
}

.adv-field label {
  display: block;
  font-size: 12px;
  color: #8b958f;
  margin-bottom: 4px;
}

.adv-field input,
.adv-field select {
  width: 100%;
  border: 1px solid #dfe6e1;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 13px;
  color: #33473f;
  outline: none;
  background: #fff;
  box-sizing: border-box;
}

.adv-check-group {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.adv-check {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #46544f;
  cursor: pointer;
}

.adv-check input { accent-color: #587766; }

.adv-date-range {
  display: flex;
  gap: 8px;
  align-items: center;
}

.adv-date-range input {
  flex: 1;
}

.adv-date-range span {
  color: #8b958f;
  font-size: 12px;
}

.adv-filter-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  justify-content: flex-end;
}



.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}



.empty-cell {
  text-align: center;
  padding: 40px 0 !important;
  color: #9caaa3;
  font-size: 14px;
}





.detail-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-title strong {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 15px;
  color: #2a3a33;
}



.detail-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 16px;
}

.meta-item {
  display: flex;
  gap: 8px;
  font-size: 13px;
  color: #46544f;
}

.meta-label {
  color: #8b958f;
  min-width: 60px;
}

.detail-subtitle {
  font-weight: 600;
  font-size: 14px;
  color: #2c3b34;
  margin-bottom: 10px;
}

.detail-table {
  width: 100%;
  min-width: 850px;
  table-layout: fixed;
  border-collapse: collapse;
}
.detail-table th, .detail-table td {
  height: 46px;
  padding: 0 14px;
  border-bottom: 1px solid #e5ece8;
  box-sizing: border-box;
  vertical-align: middle;
  font-size: 13px;
  color: #3e554b;
}
.detail-table th {
  position: sticky;
  top: 0;
  z-index: 2;
  height: 44px;
  background: #eef4f1;
  color: #587066;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}
.detail-table td {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.detail-table .number-cell {
  text-align: right;
  font-variant-numeric: tabular-nums;
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

.diff-num { font-weight: 600; }
.diff-num.pos { color: #3E8E41; }
.diff-num.neg { color: #C23531; }

/* More overlay (transparent, click-outside only) */
.more-overlay {
  position: fixed;
  inset: 0;
  z-index: 99;
  background: transparent;
}

/* Overlay */
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.3);
  z-index: 200;
}

/* Dialog */
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

.dialog-sm { width: 400px; }

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

/* Form */
.form-field {
  margin-bottom: 14px;
}

.form-field label {
  display: block;
  font-size: 13px;
  color: #46544f;
  margin-bottom: 4px;
  font-weight: 500;
}

.form-field .required { color: #C23531; }

.form-field input,
.form-field select {
  width: 100%;
  border: 1px solid #dfe6e1;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 13px;
  color: #33473f;
  outline: none;
  background: #fff;
  box-sizing: border-box;
}

.form-static {
  display: block;
  padding: 8px 0;
  font-size: 13px;
  color: #33473f;
}

.form-section-title {
  font-weight: 600;
  font-size: 13px;
  color: #2c3b34;
  margin: 16px 0 8px;
  padding-top: 12px;
  border-top: 1px dashed #e2eae5;
}

.form-lines {
  margin-bottom: 8px;
}

.form-line-row {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 6px 8px;
  background: #f6f9f7;
  border-radius: 6px;
  margin-bottom: 4px;
  font-size: 12px;
}

.form-line-row .line-code { font-weight: 600; flex: 1; }
.form-line-row .line-batch { color: #6c7d75; flex: 1; }
.form-line-row .line-qty { font-weight: 600; min-width: 50px; text-align: right; }
.form-line-row .line-qty.pos { color: #3E8E41; }
.form-line-row .line-qty.neg { color: #C23531; }
.form-line-row .line-del {
  border: 0;
  background: transparent;
  color: #bccbc2;
  cursor: pointer;
  font-size: 14px;
  padding: 2px 4px;
}
.form-line-row .line-del:hover { color: #C23531; }

.form-line-add {
  display: flex;
  gap: 6px;
  align-items: center;
}

.line-input {
  flex: 1;
  border: 1px solid #dfe6e1;
  border-radius: 6px;
  padding: 6px 8px;
  font-size: 12px;
  color: #33473f;
  outline: none;
  background: #fff;
  min-width: 0;
}

.line-input-sm { max-width: 80px; }

.add-line-btn {
  border: 1px solid #587766;
  border-radius: 6px;
  padding: 6px 12px;
  background: #fff;
  color: #587766;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.12s;
}

.add-line-btn:hover { background: #eef4f0; }

/* Import */
.import-row {
  margin-bottom: 12px;
}

.import-divider {
  text-align: center;
  color: #bccbc2;
  font-size: 12px;
  margin: 8px 0;
}

.file-select input[type="file"] {
  display: none;
}

.file-select-btn {
  display: inline-block;
  border: 1px solid #cddbd3;
  border-radius: 8px;
  padding: 7px 16px;
  background: #fff;
  color: #567566;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.12s;
}

.file-select-btn:hover { background: #eef4f0; }

.file-name {
  margin-left: 10px;
  font-size: 13px;
  color: #8b958f;
}

.import-hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: #bccbc2;
}

/* Confirm */
.confirm-text {
  font-size: 14px;
  color: #46544f;
  line-height: 1.6;
}

.confirm-text.warn { color: #C23531; }

.confirm-target {
  margin: 8px 0 0;
  font-size: 13px;
  color: #8b958f;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

/* Evidence */
.evidence-reason {
  font-size: 13px;
  color: #46544f;
  margin-bottom: 16px;
}

.evidence-files {
  margin-top: 16px;
}

.evidence-file {
  display: inline-block;
  border-radius: 6px;
  padding: 4px 10px;
  background: #eef4f0;
  color: #567566;
  font-size: 12px;
  margin: 2px 4px 2px 0;
}

/* readonly hint */
.readonly-hint {
  background: #f5f7fa;
  color: #909399;
  font-size: 13px;
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 14px;
  text-align: center;
}

.form-field input.readonly {
  background: #f5f7fa;
  color: #909399;
  cursor: not-allowed;
}

.form-line-row .line-loc {
  color: #437a9e;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  flex: 1;
}

/* Export */
.export-section {
  margin-bottom: 18px;
}

.export-section-label {
  display: block;
  font-size: 13px;
  color: #46544f;
  font-weight: 500;
  margin-bottom: 8px;
}

.export-radio {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #33473f;
  cursor: pointer;
  margin-bottom: 6px;
}

.export-radio input { accent-color: #587766; }

.export-check-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.export-check {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #33473f;
  cursor: pointer;
}

.export-check input { accent-color: #587766; }

/* Toast */
.toast {
  position: fixed;
  bottom: 40px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 999;
  background: #303133;
  color: #fff;
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.2);
}

.toast-enter-active, .toast-leave-active {
  transition: all 0.25s ease;
}
.toast-enter-from, .toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(10px);
}

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

.adjustment-no-column {
  width: 250px;
  min-width: 250px;
}

.adjustment-no-link {
  appearance: none;
  display: inline-block;
  max-width: none;
  padding: 0;
  overflow: visible;
  border: 0;
  color: #3f7692;
  background: transparent;
  font: inherit;
  font-weight: 600;
  text-decoration: underline;
  text-overflow: clip;
  white-space: nowrap;
  cursor: pointer;
}

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

.stocktake-table .status-tag,
.adjustment-table .status-tag {
  height: 25px;
  padding: 0 10px;
  font-size: 12px;
}

.stocktake-table .action-cell,
.adjustment-table .action-cell {
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

.adjustment-action-cell {
  position: static;
  width: 108px;
  min-width: 108px;
  padding: 0 12px !important;
  overflow: visible !important;
  text-align: center;
  vertical-align: middle;
  pointer-events: auto;
}

.adjustment-action-cell * {
  pointer-events: auto;
}

.adjustment-more-button {
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

.adjustment-more-button:hover {
  color: #3f6554;
  border-color: #88a394;
  background: #f1f6f3;
}

.adjustment-more-button:focus-visible {
  outline: 2px solid rgba(85, 124, 105, 0.2);
  outline-offset: 2px;
}

.adjustment-more-arrow {
  font-size: 9px;
  line-height: 1;
}

</style>

<style>
.adjustment-more-dropdown {
  z-index: 5000 !important;
}

.adjustment-more-dropdown.el-popper {
  position: absolute !important;
}

.adjustment-more-dropdown .el-dropdown-menu {
  display: block !important;
  min-width: 112px;
  margin: 0;
  padding: 6px;
  position: static;
  border: 0;
  background: #ffffff;
}

.adjustment-more-dropdown .el-dropdown-menu__item {
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

.adjustment-more-dropdown .el-dropdown-menu__item:hover,
.adjustment-more-dropdown .el-dropdown-menu__item:focus {
  color: #365747;
  background: #eef5f1;
}
</style>
