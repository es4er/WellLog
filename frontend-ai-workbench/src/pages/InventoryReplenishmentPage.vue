<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as XLSX from 'xlsx'
import {
  REPLENISH_STATUS,
  REPLENISH_STATUS_OPTIONS,
  createInitialReplenishTasks,
  nextReplenishNo,
} from '../data/replenishmentMockData'

const route = useRoute()
const router = useRouter()

const searchQuery = ref('')
const pageSize = ref(20)
const currentPage = ref(1)
const selectedIds = ref([])
const showAdvancedFilter = ref(false)
const moreMenuOpen = ref(null)

const showCreateDialog = ref(false)
const showDetailDialog = ref(false)
const detailItem = ref(null)
const toastMessage = ref('')
let toastTimer = null

const advancedFilter = reactive({
  itemCode: '',
  taskNo: '',
  fromLocation: '',
  toLocation: '',
  status: '',
  createdFrom: '',
  createdTo: '',
})

const createForm = reactive({
  itemCode: '',
  batchNo: '',
  fromLocation: '',
  toLocation: '',
  sourceAvailable: null,
  targetCurrent: null,
  safetyStock: null,
  suggestQty: null,
  actualQty: null,
  reason: '',
  remark: '',
})

const tasks = ref(createInitialReplenishTasks())

function showToast(msg) {
  toastMessage.value = msg
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toastMessage.value = '' }, 2500)
}

function statusMeta(status) {
  return REPLENISH_STATUS[status] || { label: status || '—', cls: 'status-pending' }
}

const filteredData = computed(() => {
  let list = tasks.value
  const q = searchQuery.value.trim().toLowerCase()
  if (q) {
    list = list.filter((r) =>
      [r.taskNo, r.itemCode, r.itemName, r.batchNo].join(' ').toLowerCase().includes(q)
    )
  }
  const af = advancedFilter
  if (af.itemCode) list = list.filter((r) => r.itemCode.toLowerCase().includes(af.itemCode.toLowerCase()))
  if (af.taskNo) list = list.filter((r) => r.taskNo.toLowerCase().includes(af.taskNo.toLowerCase()))
  if (af.fromLocation) list = list.filter((r) => r.fromLocation.toLowerCase().includes(af.fromLocation.toLowerCase()))
  if (af.toLocation) list = list.filter((r) => r.toLocation.toLowerCase().includes(af.toLocation.toLowerCase()))
  if (af.status) list = list.filter((r) => r.status === af.status)
  if (af.createdFrom) list = list.filter((r) => (r.createdAt || '') >= af.createdFrom)
  if (af.createdTo) list = list.filter((r) => (r.createdAt || '').slice(0, 10) <= af.createdTo)
  return list
})

const totalItems = computed(() => filteredData.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize.value)))
const pagedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredData.value.slice(start, start + pageSize.value)
})

const stats = computed(() => {
  const d = tasks.value
  return {
    pending: d.filter((r) => ['DEMAND_PENDING', 'CREATED', 'ASSIGNED'].includes(r.status)).length,
    progress: d.filter((r) => r.status === 'IN_PROGRESS').length,
    done: d.filter((r) => r.status === 'COMPLETED').length,
    cancelled: d.filter((r) => r.status === 'CANCELLED').length,
  }
})

const canBatchDispatch = computed(() =>
  selectedIds.value.some((id) => {
    const row = tasks.value.find((t) => t.id === id)
    return row && row.status === 'CREATED'
  })
)

function changePage(p) {
  if (p < 1 || p > totalPages.value) return
  currentPage.value = p
}

function changeSize(size) {
  pageSize.value = size
  currentPage.value = 1
}

function resetAdvancedFilter() {
  Object.assign(advancedFilter, {
    itemCode: '',
    taskNo: '',
    fromLocation: '',
    toLocation: '',
    status: '',
    createdFrom: '',
    createdTo: '',
  })
  currentPage.value = 1
}

function applyAdvancedFilter() {
  currentPage.value = 1
  showAdvancedFilter.value = false
  showToast('查询完成')
}

function isSelected(row) {
  return selectedIds.value.includes(row.id)
}

function toggleSelect(row) {
  const idx = selectedIds.value.indexOf(row.id)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(row.id)
}

function selectAll() {
  if (selectedIds.value.length === pagedData.value.length) {
    selectedIds.value = []
  } else {
    selectedIds.value = pagedData.value.map((r) => r.id)
  }
}

function resetCreateForm() {
  Object.assign(createForm, {
    itemCode: '',
    batchNo: '',
    fromLocation: '',
    toLocation: '',
    sourceAvailable: null,
    targetCurrent: null,
    safetyStock: null,
    suggestQty: null,
    actualQty: null,
    reason: '',
    remark: '',
  })
}

function openCreateDialog(prefill = null) {
  resetCreateForm()
  if (prefill) {
    Object.assign(createForm, {
      itemCode: prefill.itemCode || '',
      batchNo: prefill.batchNo || '',
      fromLocation: prefill.fromLocation || prefill.locationCode || '',
      toLocation: prefill.toLocation || '',
      sourceAvailable: prefill.sourceAvailable ?? prefill.availableQty ?? null,
      targetCurrent: prefill.targetCurrent ?? null,
      safetyStock: prefill.safetyStock ?? null,
      suggestQty: prefill.suggestQty ?? prefill.availableQty ?? null,
      actualQty: prefill.actualQty ?? prefill.suggestQty ?? null,
      reason: prefill.reason || '人工创建补货任务',
      remark: prefill.remark || '',
    })
  }
  showCreateDialog.value = true
}

function confirmCreate() {
  if (!createForm.itemCode || !createForm.fromLocation || !createForm.toLocation) {
    showToast('请填写物料编码、来源库位与目标库位')
    return
  }
  const qty = Number(createForm.actualQty || createForm.suggestQty)
  if (!qty || qty <= 0) {
    showToast('请填写有效的补货数量')
    return
  }
  const taskNo = nextReplenishNo()
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const createdAt = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:00`

  tasks.value.unshift({
    id: taskNo,
    taskNo,
    itemCode: createForm.itemCode.trim(),
    itemName: createForm.itemCode.trim(),
    batchNo: (createForm.batchNo || '').trim() || '—',
    fromLocation: createForm.fromLocation.trim(),
    toLocation: createForm.toLocation.trim(),
    suggestQty: Number(createForm.suggestQty) || qty,
    actualQty: qty,
    status: 'CREATED',
    executor: '',
    createdAt,
    reason: createForm.reason || '人工创建补货任务',
    remark: createForm.remark || '',
    sourceAvailable: Number(createForm.sourceAvailable) || 0,
    targetCurrent: Number(createForm.targetCurrent) || 0,
    safetyStock: Number(createForm.safetyStock) || 0,
  })
  showCreateDialog.value = false
  currentPage.value = 1
  showToast(`补货任务已生成：${taskNo}`)
  // 清理 URL 预填参数，避免返回时重复弹窗
  if (route.query.openCreate) {
    router.replace({ path: route.path, query: {} })
  }
}

function openDetail(row) {
  detailItem.value = row
  showDetailDialog.value = true
  moreMenuOpen.value = null
}

function dispatchTask(row) {
  if (row.status !== 'CREATED' && row.status !== 'DEMAND_PENDING') {
    showToast('当前状态不可下发')
    return
  }
  row.status = 'ASSIGNED'
  row.executor = row.executor || '待认领'
  moreMenuOpen.value = null
  showToast(`已下发：${row.taskNo}`)
}

function startTask(row) {
  if (row.status !== 'ASSIGNED') {
    showToast('请先下发任务')
    return
  }
  row.status = 'IN_PROGRESS'
  if (!row.executor || row.executor === '待认领') row.executor = '韩库存'
  moreMenuOpen.value = null
  showToast(`开始执行：${row.taskNo}`)
}

function completeTask(row) {
  if (row.status !== 'IN_PROGRESS' && row.status !== 'ASSIGNED') {
    showToast('当前状态不可确认完成')
    return
  }
  row.status = 'COMPLETED'
  row.actualQty = row.actualQty || row.suggestQty
  moreMenuOpen.value = null
  showToast(`已完成：${row.taskNo}`)
}

function cancelTask(row) {
  if (row.status === 'COMPLETED' || row.status === 'CANCELLED') {
    showToast('当前状态不可取消')
    return
  }
  row.status = 'CANCELLED'
  moreMenuOpen.value = null
  showToast(`已取消：${row.taskNo}`)
}

function batchDispatch() {
  let count = 0
  for (const id of selectedIds.value) {
    const row = tasks.value.find((t) => t.id === id)
    if (row && (row.status === 'CREATED' || row.status === 'DEMAND_PENDING')) {
      row.status = 'ASSIGNED'
      row.executor = row.executor || '待认领'
      count += 1
    }
  }
  showToast(count ? `已批量下发 ${count} 条任务` : '没有可下发的任务')
}

function handleExport() {
  const header = [[
    '补货任务单号', '物料编码', '物料名称', '批次号', '来源库位', '目标库位',
    '建议数量', '实际数量', '状态', '执行人', '创建时间',
  ]]
  const rows = filteredData.value.map((r) => [
    r.taskNo, r.itemCode, r.itemName, r.batchNo, r.fromLocation, r.toLocation,
    r.suggestQty, r.actualQty ?? '', statusMeta(r.status).label, r.executor || '', r.createdAt,
  ])
  const ws = XLSX.utils.aoa_to_sheet([...header, ...rows])
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '补货任务')
  const d = new Date()
  const stamp = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
  XLSX.writeFile(wb, `补货任务_${stamp}.xlsx`)
  showToast('导出成功')
}

function handleRowCommand(command, row) {
  switch (command) {
    case 'detail':
      openDetail(row)
      break
    case 'dispatch':
      dispatchTask(row)
      break
    case 'start':
      startTask(row)
      break
    case 'complete':
      completeTask(row)
      break
    case 'cancel':
      cancelTask(row)
      break
  }
}

function applyRoutePrefill() {
  const q = route.query
  if (!q.openCreate && !q.itemCode) return
  openCreateDialog({
    itemCode: q.itemCode || '',
    batchNo: q.batchNo || '',
    fromLocation: q.fromLocation || q.locationCode || '',
    toLocation: q.toLocation || '',
    sourceAvailable: q.availableQty ? Number(q.availableQty) : null,
    suggestQty: q.suggestQty ? Number(q.suggestQty) : (q.availableQty ? Number(q.availableQty) : null),
    actualQty: q.suggestQty ? Number(q.suggestQty) : null,
    safetyStock: q.safetyStock ? Number(q.safetyStock) : null,
    reason: q.reason || (q.from === 'analytics' ? '数据分析触发补货' : '库存控制创建补货'),
  })
}

onMounted(applyRoutePrefill)
watch(() => route.query, applyRoutePrefill)
</script>

<template>
  <div class="inv-page">
    <header class="inv-header">
      <div>
        <h1>补货管理</h1>
      </div>
      <div class="inv-strip">
        <div class="strip-item"><strong class="num orange">{{ stats.pending }}</strong><span class="lbl">待处理</span></div>
        <div class="strip-item"><strong class="num blue">{{ stats.progress }}</strong><span class="lbl">执行中</span></div>
        <div class="strip-item"><strong class="num green">{{ stats.done }}</strong><span class="lbl">已完成</span></div>
        <div class="strip-item"><strong class="num gray">{{ stats.cancelled }}</strong><span class="lbl">已取消</span></div>
      </div>
    </header>

    <div class="inv-toolbar">
      <div class="toolbar-left">
        <button type="button" class="tb-btn primary" @click="openCreateDialog()">生成补货任务</button>
        <button type="button" class="tb-btn primary" :disabled="!canBatchDispatch" @click="batchDispatch">批量下发</button>
        <button type="button" class="tb-btn primary" @click="handleExport">导出</button>
        <button type="button" class="tb-btn primary" @click="showAdvancedFilter = !showAdvancedFilter">高级查询</button>
      </div>
      <div class="toolbar-right">
        <input v-model="searchQuery" class="tb-search" placeholder="搜索任务单号 / 物料编码…" />
      </div>
    </div>

    <transition name="fade">
      <div v-if="showAdvancedFilter" class="adv-filter">
        <div class="adv-filter-row">
          <div class="adv-field">
            <label>物料编码</label>
            <input v-model="advancedFilter.itemCode" class="adv-input" />
          </div>
          <div class="adv-field">
            <label>补货任务单号</label>
            <input v-model="advancedFilter.taskNo" class="adv-input" />
          </div>
          <div class="adv-field">
            <label>来源库位</label>
            <input v-model="advancedFilter.fromLocation" class="adv-input" />
          </div>
          <div class="adv-field">
            <label>目标库位</label>
            <input v-model="advancedFilter.toLocation" class="adv-input" />
          </div>
          <div class="adv-field">
            <label>任务状态</label>
            <select v-model="advancedFilter.status" class="adv-input">
              <option value="">全部</option>
              <option v-for="opt in REPLENISH_STATUS_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </div>
          <div class="adv-field">
            <label>创建时间起</label>
            <input v-model="advancedFilter.createdFrom" type="date" class="adv-input" />
          </div>
          <div class="adv-field">
            <label>创建时间止</label>
            <input v-model="advancedFilter.createdTo" type="date" class="adv-input" />
          </div>
          <div class="adv-actions">
            <button type="button" class="tb-btn primary" @click="applyAdvancedFilter">查询</button>
            <button type="button" class="tb-btn" @click="resetAdvancedFilter">重置</button>
          </div>
        </div>
      </div>
    </transition>

    <div class="inventory-table-wrap">
      <table class="inventory-main-table">
        <thead>
          <tr>
            <th style="width:48px">
              <input
                type="checkbox"
                :checked="pagedData.length > 0 && selectedIds.length === pagedData.length"
                @change="selectAll"
              />
            </th>
            <th>补货任务单号</th>
            <th>物料编码</th>
            <th>物料名称</th>
            <th>批次号</th>
            <th>来源库位</th>
            <th>目标库位</th>
            <th class="number-cell">建议数量</th>
            <th class="number-cell">实际数量</th>
            <th>状态</th>
            <th>执行人</th>
            <th>创建时间</th>
            <th class="inventory-action-column">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in pagedData" :key="row.id" :class="{ selected: isSelected(row) }">
            <td><input type="checkbox" :checked="isSelected(row)" @change="toggleSelect(row)" /></td>
            <td><span class="code-text">{{ row.taskNo }}</span></td>
            <td><span class="code-text">{{ row.itemCode }}</span></td>
            <td>{{ row.itemName }}</td>
            <td><span class="batch-text">{{ row.batchNo }}</span></td>
            <td><span class="loc-text">{{ row.fromLocation }}</span></td>
            <td><span class="loc-text">{{ row.toLocation }}</span></td>
            <td class="number-cell">{{ row.suggestQty }}</td>
            <td class="number-cell">{{ row.actualQty ?? '—' }}</td>
            <td>
              <span :class="['status-tag', statusMeta(row.status).cls]">
                <i class="status-dot"></i>
                {{ statusMeta(row.status).label }}
              </span>
            </td>
            <td>{{ row.executor || '—' }}</td>
            <td style="font-size:12px;color:#6c7d75">{{ row.createdAt }}</td>
            <td class="inventory-action-cell">
              <el-dropdown
                trigger="click"
                placement="bottom-end"
                :teleported="true"
                popper-class="inventory-more-dropdown"
                @command="(cmd) => handleRowCommand(cmd, row)"
              >
                <button type="button" class="inventory-action-btn" @click.stop>
                  更多
                  <span>▼</span>
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="detail">查看详情</el-dropdown-item>
                    <el-dropdown-item
                      v-if="row.status === 'CREATED' || row.status === 'DEMAND_PENDING'"
                      command="dispatch"
                    >下发任务</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'ASSIGNED'" command="start">开始执行</el-dropdown-item>
                    <el-dropdown-item
                      v-if="row.status === 'IN_PROGRESS' || row.status === 'ASSIGNED'"
                      command="complete"
                    >确认完成</el-dropdown-item>
                    <el-dropdown-item
                      v-if="row.status !== 'COMPLETED' && row.status !== 'CANCELLED'"
                      command="cancel"
                      divided
                    >
                      <span class="danger-menu-item">取消任务</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </td>
          </tr>
          <tr v-if="pagedData.length === 0">
            <td colspan="13" class="empty-cell">暂无补货任务 — 点击「生成补货任务」创建</td>
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
        <button type="button" :disabled="currentPage === 1" @click="changePage(currentPage - 1)">‹</button>
        <span class="page-num">{{ currentPage }} / {{ totalPages }} 页</span>
        <button type="button" :disabled="currentPage >= totalPages" @click="changePage(currentPage + 1)">›</button>
      </div>
    </div>

    <!-- 生成补货任务 -->
    <transition name="fade">
      <div v-if="showCreateDialog" class="modal-overlay" @click.self="showCreateDialog = false">
        <div class="modal-box modal-lg">
          <div class="modal-header">
            <h3>生成补货任务</h3>
            <button type="button" class="modal-close" @click="showCreateDialog = false">✕</button>
          </div>
          <div class="modal-body create-grid">
            <div class="form-row">
              <label>物料编码 <span class="req">*</span></label>
              <input v-model="createForm.itemCode" class="form-input" placeholder="请输入物料编码" />
            </div>
            <div class="form-row">
              <label>批次号</label>
              <input v-model="createForm.batchNo" class="form-input" placeholder="请输入批次号" />
            </div>
            <div class="form-row">
              <label>来源库位 <span class="req">*</span></label>
              <input v-model="createForm.fromLocation" class="form-input" placeholder="储备库位" />
            </div>
            <div class="form-row">
              <label>目标库位 <span class="req">*</span></label>
              <input v-model="createForm.toLocation" class="form-input" placeholder="拣选库位" />
            </div>
            <div class="form-row">
              <label>来源可用库存</label>
              <input v-model.number="createForm.sourceAvailable" type="number" min="0" class="form-input" />
            </div>
            <div class="form-row">
              <label>目标当前库存</label>
              <input v-model.number="createForm.targetCurrent" type="number" min="0" class="form-input" />
            </div>
            <div class="form-row">
              <label>安全库存</label>
              <input v-model.number="createForm.safetyStock" type="number" min="0" class="form-input" />
            </div>
            <div class="form-row">
              <label>建议补货数量</label>
              <input v-model.number="createForm.suggestQty" type="number" min="1" class="form-input" />
            </div>
            <div class="form-row">
              <label>实际补货数量 <span class="req">*</span></label>
              <input v-model.number="createForm.actualQty" type="number" min="1" class="form-input" />
            </div>
            <div class="form-row">
              <label>补货原因</label>
              <input v-model="createForm.reason" class="form-input" placeholder="请输入补货原因" />
            </div>
            <div class="form-row full">
              <label>备注</label>
              <textarea v-model="createForm.remark" class="form-textarea" rows="2" placeholder="选填" />
            </div>
          </div>
          <div class="modal-foot">
            <button type="button" class="tb-btn" @click="showCreateDialog = false">取消</button>
            <button type="button" class="tb-btn primary" @click="confirmCreate">确认生成</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 详情 -->
    <transition name="fade">
      <div v-if="showDetailDialog" class="modal-overlay" @click.self="showDetailDialog = false">
        <div class="modal-box modal-lg">
          <div class="modal-header">
            <h3>补货任务详情 — {{ detailItem?.taskNo }}</h3>
            <button type="button" class="modal-close" @click="showDetailDialog = false">✕</button>
          </div>
          <div class="modal-body" v-if="detailItem">
            <div class="detail-grid">
              <div><span>物料编码</span><strong>{{ detailItem.itemCode }}</strong></div>
              <div><span>物料名称</span><strong>{{ detailItem.itemName }}</strong></div>
              <div><span>批次号</span><strong>{{ detailItem.batchNo }}</strong></div>
              <div><span>状态</span><strong>{{ statusMeta(detailItem.status).label }}</strong></div>
              <div><span>来源库位</span><strong>{{ detailItem.fromLocation }}</strong></div>
              <div><span>目标库位</span><strong>{{ detailItem.toLocation }}</strong></div>
              <div><span>建议数量</span><strong>{{ detailItem.suggestQty }}</strong></div>
              <div><span>实际数量</span><strong>{{ detailItem.actualQty ?? '—' }}</strong></div>
              <div><span>来源可用</span><strong>{{ detailItem.sourceAvailable }}</strong></div>
              <div><span>目标当前</span><strong>{{ detailItem.targetCurrent }}</strong></div>
              <div><span>安全库存</span><strong>{{ detailItem.safetyStock }}</strong></div>
              <div><span>执行人</span><strong>{{ detailItem.executor || '—' }}</strong></div>
              <div class="full"><span>补货原因</span><strong>{{ detailItem.reason || '—' }}</strong></div>
              <div class="full"><span>备注</span><strong>{{ detailItem.remark || '—' }}</strong></div>
            </div>
          </div>
          <div class="modal-foot">
            <button type="button" class="tb-btn" @click="showDetailDialog = false">关闭</button>
          </div>
        </div>
      </div>
    </transition>

    <transition name="fade">
      <div v-if="toastMessage" class="toast-notification">{{ toastMessage }}</div>
    </transition>

    <div v-if="showCreateDialog || showDetailDialog" class="modal-overlay-bg"></div>
  </div>
</template>

<style scoped>
.inv-page {
  width: min(1280px, 100%);
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

.inv-strip { display: flex; gap: 6px; }

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

.toolbar-left { display: flex; gap: 8px; flex-wrap: wrap; }

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
.tb-btn:disabled { opacity: 0.45; cursor: not-allowed; }

.tb-btn.primary {
  background: #587766;
  border-color: #587766;
  color: #fff;
}

.tb-btn.primary:hover:not(:disabled) { background: #4a6a59; }

.tb-search {
  border: 1px solid #dfe6e1;
  border-radius: 8px;
  padding: 7px 14px;
  font-size: 13px;
  color: #33473f;
  outline: none;
  background: #fff;
  width: 260px;
}

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

.adv-actions { display: flex; gap: 6px; }

.inventory-table-wrap {
  width: 100%;
  margin-top: 12px;
  overflow-x: auto;
  border: 1px solid #e2eae5;
  border-radius: 10px;
  background: rgba(255,255,255,0.9);
}

.inventory-main-table {
  width: 100%;
  min-width: 1280px;
  border-collapse: collapse;
}

.inventory-main-table th,
.inventory-main-table td {
  height: 52px;
  padding: 0 12px;
  vertical-align: middle;
}

.inventory-main-table thead { background: #f0f5f2; }

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

.inventory-main-table tbody tr:hover { background: #f6f9f7; }
.inventory-main-table tbody tr.selected { background: #e6f1ea; }
.number-cell { text-align: right; font-variant-numeric: tabular-nums; }

.inventory-action-column,
.inventory-action-cell {
  width: 120px;
  text-align: center !important;
}

.code-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #31433c;
  font-weight: 600;
}

.batch-text,
.loc-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.loc-text { color: #437a9e; font-weight: 500; }
.batch-text { color: #6c7d75; }

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
}

.status-pending { background: #d6e6f0; color: #437a9e; }
.status-pending .status-dot { background: #437a9e; }
.status-created { background: #fdf0e8; color: #c98a2e; }
.status-created .status-dot { background: #c98a2e; }
.status-assigned { background: #efe8f7; color: #7a5ea8; }
.status-assigned .status-dot { background: #7a5ea8; }
.status-progress { background: #e8f0fb; color: #3d6fa8; }
.status-progress .status-dot { background: #3d6fa8; }
.status-done { background: #e0f0e6; color: #3b7a5a; }
.status-done .status-dot { background: #3b7a5a; }
.status-cancelled { background: #eef4f0; color: #8b958f; }
.status-cancelled .status-dot { background: #8b958f; }

.inventory-action-btn {
  appearance: none;
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
  background: #fff;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.inventory-action-btn:hover {
  border-color: #88a394;
  background: #f1f6f3;
}

.danger-menu-item { color: #c0392b; }

.empty-cell {
  text-align: center;
  padding: 40px 0 !important;
  color: #9caaa3;
}

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

.page-info { color: #8b958f; font-size: 13px; margin-right: auto; }
.page-size { display: flex; align-items: center; gap: 6px; color: #8b958f; font-size: 13px; }
.page-size select {
  border: 1px solid #dfe6e1;
  border-radius: 6px;
  padding: 4px 8px;
  background: #fff;
}
.page-controls { display: flex; align-items: center; gap: 8px; }
.page-controls button {
  border: 1px solid #cddbd3;
  border-radius: 6px;
  padding: 4px 10px;
  background: #fff;
  color: #567566;
  cursor: pointer;
}
.page-controls button:disabled { opacity: 0.4; cursor: not-allowed; }
.page-num { font-size: 13px; color: #46544f; min-width: 60px; text-align: center; }

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
  width: 480px;
  max-width: 92vw;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-box.modal-lg { width: 640px; }

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
}

.modal-body {
  padding: 16px 20px;
  overflow-y: auto;
}

.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px 16px;
  border-top: 1px solid #ecf1ee;
}

.create-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.create-grid .full { grid-column: 1 / -1; }

.form-row { margin-bottom: 14px; }
.form-row label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #46544f;
  margin-bottom: 5px;
}
.form-row .req { color: #d97a6b; }

.form-input,
.form-textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #dfe6e1;
  border-radius: 6px;
  padding: 7px 10px;
  font-size: 13px;
  color: #33473f;
  outline: none;
  background: #fff;
  font-family: inherit;
}

.form-input:focus,
.form-textarea:focus { border-color: #587766; }

.form-static {
  display: block;
  padding: 7px 0;
  font-size: 13px;
  color: #33473f;
  font-weight: 500;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 20px;
}

.detail-grid .full { grid-column: 1 / -1; }

.detail-grid span {
  display: block;
  font-size: 12px;
  color: #8b958f;
  margin-bottom: 2px;
}

.detail-grid strong {
  font-size: 13px;
  color: #33473f;
  font-weight: 600;
}

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

.fade-enter-active,
.fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from,
.fade-leave-to { opacity: 0; }

@media (max-width: 720px) {
  .create-grid { grid-template-columns: 1fr; }
  .tb-search { width: 100%; }
}
</style>
