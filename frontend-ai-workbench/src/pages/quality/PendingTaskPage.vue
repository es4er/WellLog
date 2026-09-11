<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useRoleAccess } from '../../composables/useRoleAccess'
import { useQuality } from '../../composables/useQuality'
import { qualityExecutePath } from '../../router/menuRoutes'
import { downloadCsv } from '../../utils/tableExport'

const router = useRouter()
const { allowed } = useRoleAccess('quality')
const {
  inspectionTasks,
  inspectionDetail,
  qualityDataLoading,
  qualityDataError,
  taskStats,
  lastSyncAt,
  loadQualityData,
  loadTaskList,
  loadInspectionDetail,
  createInspectionTask,
  startQualityPolling,
  stopQualityPolling,
  statusClass,
  selectTaskByReceiptId
} = useQuality()

const filterStatus = ref('')
const filterBatch = ref('')
const filterKeyword = ref('')
const starting = ref(false)
const startError = ref('')
const toolbarMessage = ref('')
const taskImportInput = ref(null)
const selectedReceiptId = ref(null)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailTask = ref(null)

const tabs = [
  { key: '', label: '全部任务' },
  { key: '待检测', label: '待检测' },
  { key: '检测中', label: '检测中' },
  { key: '完成', label: '已完成' }
]

onMounted(async () => {
  if (!allowed.value) {
    router.replace('/module/' + encodeURIComponent('待检任务'))
    return
  }
  await loadQualityData()
  startQualityPolling(8000)
})

onUnmounted(() => stopQualityPolling())

async function applyFilters() {
  await loadTaskList({
    status: filterStatus.value,
    batchNo: filterBatch.value,
    keyword: filterKeyword.value
  })
}

async function switchTab(key) {
  filterStatus.value = key
  await applyFilters()
}

function handleTaskCreate() {
  toolbarMessage.value = '新增待检任务表单待对接后端接口'
}

function handleTaskImportClick() {
  taskImportInput.value?.click()
}

function handleTaskImportChange(event) {
  const file = event.target.files?.[0]
  if (file) {
    toolbarMessage.value = `已选择文件「${file.name}」，导入功能待后端接口对接`
  }
  event.target.value = ''
}

function handleTaskExport() {
  if (!inspectionTasks.value.length) {
    toolbarMessage.value = '暂无待检任务可导出'
    return
  }
  downloadCsv(
    '待检任务.csv',
    ['质检编号', '来源单据', '批次', '数量', '状态'],
    inspectionTasks.value.map((task) => [
      task.id,
      task.receiptNo,
      task.batchNo,
      task.qty,
      task.status
    ])
  )
  toolbarMessage.value = '已导出待检任务表'
}

const detailLines = computed(() => inspectionDetail.value?.lineDetails ?? [])
const detailPendingCount = computed(() =>
  detailLines.value.filter((line) => ['INSPECTING', 'PENDING_INSPECTION'].includes(line.lineStatus)).length
)

function lineStatusText(status) {
  const map = {
    PENDING_INSPECTION: '待质检',
    INSPECTING: '检测中',
    INSPECTED: '已检待上架',
    QUALIFIED: '合格',
    UNQUALIFIED: '不合格',
    INSPECTION_FAILED: '不合格',
    QUALITY_FAIL: '不合格'
  }
  return map[status] || status || '待质检'
}

function lineStatusTone(status) {
  if (['INSPECTED', 'QUALIFIED'].includes(status)) return 'ok'
  if (['UNQUALIFIED', 'INSPECTION_FAILED', 'QUALITY_FAIL'].includes(status)) return 'danger'
  if (status === 'INSPECTING') return 'info'
  return 'warn'
}

async function openTaskDetail(task, event) {
  event?.stopPropagation?.()
  if (!task?.receiptId) return
  detailTask.value = task
  detailOpen.value = true
  detailLoading.value = true
  detailError.value = ''
  selectedReceiptId.value = task.receiptId
  try {
    await loadInspectionDetail(task.receiptId)
  } catch (error) {
    detailError.value = error.message || '加载质检物料明细失败'
  } finally {
    detailLoading.value = false
  }
}

function closeTaskDetail() {
  detailOpen.value = false
  detailTask.value = null
  detailError.value = ''
}

function selectTask(task) {
  selectedReceiptId.value = task.receiptId
}

async function goExecute(task) {
  if (!task.receiptId) return
  selectedReceiptId.value = task.receiptId
  starting.value = true
  try {
    if (!task.inspectionId && task.status === '待检测') {
      await createInspectionTask(task.receiptId)
    }
    selectTaskByReceiptId(task.receiptId)
    router.push(qualityExecutePath(task.receiptId))
  } catch (error) {
    startError.value = error.message || '创建质检任务失败'
  } finally {
    starting.value = false
  }
}

function taskStatusTone(status) {
  const cls = statusClass(status)
  if (cls === 'ok') return 'ok'
  if (cls === 'danger') return 'danger'
  return 'warn'
}
</script>

<template>
  <section v-if="allowed" class="outbound-board quality-task-center qa-redesign">
    <p v-if="qualityDataLoading" class="data-hint">正在加载待检任务...</p>
    <p v-else-if="qualityDataError" class="data-hint danger">{{ qualityDataError }}</p>
    <p v-if="startError" class="data-hint danger">{{ startError }}</p>
    <p v-if="toolbarMessage" class="data-hint">{{ toolbarMessage }}</p>

    <header class="qa-issue-header">
      <div>
        <h1>待检任务中心 <span>i</span></h1>
      </div>
      <div class="qa-task-stats">
        <div class="qa-task-stat tone-warn">
          <div>
            <strong>{{ taskStats.pending }}</strong>
            <small>待检测</small>
          </div>
        </div>
        <div class="qa-task-stat tone-info">
          <div>
            <strong>{{ taskStats.inspecting }}</strong>
            <small>检测中</small>
          </div>
        </div>
        <div class="qa-task-stat tone-ok">
          <div>
            <strong>{{ taskStats.completed }}</strong>
            <small>已完成</small>
          </div>
        </div>
      </div>
    </header>

    <section class="qa-records-panel">
      <div class="qa-tabs" role="tablist" aria-label="待检任务分类">
        <button
          v-for="tab in tabs"
          :key="tab.key || 'all'"
          type="button"
          :class="{ active: filterStatus === tab.key }"
          @click="switchTab(tab.key)"
        >{{ tab.label }}</button>
      </div>

      <div class="qa-filter-row qa-task-filter-row">
        <label>
          <span>状态：</span>
          <select v-model="filterStatus" @change="applyFilters">
            <option value="">全部状态</option>
            <option value="待检测">待检测</option>
            <option value="检测中">检测中</option>
            <option value="完成">完成</option>
          </select>
        </label>
        <label>
          <span>批次号：</span>
          <input v-model="filterBatch" type="text" placeholder="B20260708001" @keyup.enter="applyFilters" />
        </label>
        <label class="qa-search">
          <input v-model="filterKeyword" type="search" placeholder="请输入收货单/物料/供应商" @keyup.enter="applyFilters" />
          <span>⌕</span>
        </label>
        <div class="qa-task-toolbar">
          <button type="button" class="qa-task-btn" @click="applyFilters">筛选</button>
          <button type="button" class="qa-task-btn" @click="handleTaskCreate">新增</button>
          <button type="button" class="qa-task-btn ghost" @click="handleTaskImportClick">导入</button>
          <button type="button" class="qa-task-btn ghost" @click="handleTaskExport">导出</button>
          <small v-if="lastSyncAt">同步 {{ lastSyncAt }}</small>
        </div>
        <input
          ref="taskImportInput"
          type="file"
          accept=".csv,.xlsx,.xls"
          hidden
          @change="handleTaskImportChange"
        />
      </div>

      <div class="qa-issue-table qa-task-table">
        <div class="qa-issue-tr qa-issue-th qa-task-tr">
          <span></span>
          <span>质检编号</span>
          <span>来源单据</span>
          <span>批次</span>
          <span>数量</span>
          <span>状态</span>
          <span>操作</span>
        </div>
        <div
          v-for="(task, index) in inspectionTasks"
          :key="task.receiptId + '-' + (task.inspectionId || 'new')"
          role="button"
          tabindex="0"
          :class="['qa-issue-tr', 'qa-task-tr', { selected: task.receiptId === selectedReceiptId || (!selectedReceiptId && index === 0) }]"
          @click="selectTask(task)"
          @keydown.enter="selectTask(task)"
        >
          <span>
            <i :class="{ on: task.receiptId === selectedReceiptId || (!selectedReceiptId && index === 0) }"></i>
          </span>
          <span>
            <button type="button" class="qa-code qa-code-link" @click="openTaskDetail(task, $event)">
              {{ task.id }}
            </button>
          </span>
          <span>{{ task.receiptNo }}</span>
          <span>{{ task.batchNo }}</span>
          <span>{{ task.qty }}</span>
          <span class="qa-task-status">
            <em :class="['qa-dot-status', taskStatusTone(task.status)]">{{ task.status }}</em>
            <em v-if="task.strict && task.status !== '完成'" class="qa-pill danger">加严</em>
          </span>
          <span class="qa-task-ops" @click.stop>
            <button
              v-if="task.status !== '完成'"
              type="button"
              class="qa-view"
              :disabled="starting"
              @click="goExecute(task)"
            >{{ starting ? '处理中...' : '开始检测' }}</button>
            <span v-else class="qa-task-done">已检</span>
          </span>
        </div>
        <p v-if="!inspectionTasks.length" class="qa-empty">暂无符合条件的待检任务</p>
      </div>
    </section>

    <div
      v-if="detailOpen"
      class="qa-issue-modal-overlay"
      @click.self="closeTaskDetail"
    >
      <div class="qa-issue-modal qa-task-detail-modal" role="dialog" aria-modal="true" aria-labelledby="qa-task-detail-title">
        <header class="qa-issue-modal-head">
          <div>
            <h2 id="qa-task-detail-title">质检单物料明细</h2>
            <p>
              质检编号 {{ detailTask?.id || '—' }}
              · 收货单 {{ detailTask?.receiptNo || inspectionDetail?.receipt?.receiptNo || '—' }}
              · 供应商 {{ inspectionDetail?.supplierName || '—' }}
            </p>
          </div>
          <button type="button" class="qa-issue-modal-close" aria-label="关闭" @click="closeTaskDetail">×</button>
        </header>

        <div class="qa-issue-modal-body">
          <p v-if="detailLoading" class="data-hint">正在加载待检物料...</p>
          <p v-else-if="detailError" class="data-hint danger">{{ detailError }}</p>
          <p v-else-if="!detailLines.length" class="qa-empty">暂无物料明细</p>

          <div v-else class="qa-task-lines-panel">
            <div class="qa-task-lines-summary">
              共 <strong>{{ detailLines.length }}</strong> 种物料
              <template v-if="detailPendingCount > 0">
                · <strong>{{ detailPendingCount }}</strong> 种待检验
              </template>
              <template v-else>
                · 已全部完成
              </template>
              <span v-if="detailTask?.strict" class="qa-pill danger">加严</span>
            </div>
            <div class="qa-task-lines-table">
              <div class="qa-task-lines-tr qa-task-lines-th">
                <span>#</span>
                <span>物料名称</span>
                <span>物料编码</span>
                <span>批次号</span>
                <span>收货数量</span>
                <span>行状态</span>
              </div>
              <div
                v-for="(line, index) in detailLines"
                :key="line.receiptLineId || index"
                class="qa-task-lines-tr"
              >
                <span>{{ index + 1 }}</span>
                <span class="qa-task-line-name">{{ line.itemName || '—' }}</span>
                <span class="mono">{{ line.itemCode || '—' }}</span>
                <span class="mono">{{ line.batchNo || '—' }}</span>
                <span>{{ line.receivedQty ?? '—' }}</span>
                <span>
                  <em :class="['qa-dot-status', lineStatusTone(line.lineStatus)]">
                    {{ lineStatusText(line.lineStatus) }}
                  </em>
                </span>
              </div>
            </div>
          </div>
        </div>

        <footer v-if="detailTask && detailPendingCount > 0" class="qa-task-detail-foot">
          <button type="button" class="qa-task-btn ghost" @click="closeTaskDetail">关闭</button>
          <button
            type="button"
            class="qa-task-btn"
            :disabled="starting"
            @click="goExecute(detailTask); closeTaskDetail()"
          >{{ starting ? '处理中...' : '开始检测' }}</button>
        </footer>
      </div>
    </div>
  </section>
</template>
