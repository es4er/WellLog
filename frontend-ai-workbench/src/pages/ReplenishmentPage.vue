<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useRoleAccess, useViewRole } from '../composables/useRoleAccess'
import { useWorker } from '../composables/useWorker'
import { menuToPath, demoPreserveRoute } from '../router/menuRoutes'
import RoleAccessDenied from '../components/RoleAccessDenied.vue'

const router = useRouter()
const { allowed } = useRoleAccess('worker')
const { isAdminDemo, demoRoleId } = useViewRole()

const {
  REPLENISH_REASONS,
  pickingTasks,
  workerDataLoading,
  loadWorkerData,
  submitReplenish,
  replenishRecords,
  resolvePickingTaskId,
  resolvePickingLineId,
  isTaskHandedOver,
  hasOpenException,
  getTaskException
} = useWorker()

const form = reactive({
  taskId: '',
  itemId: '',
  qty: 1,
  reason: REPLENISH_REASONS[0],
  note: ''
})
const submitting = ref(false)
const submitted = ref(false)
const tip = ref('')
const activeTab = ref('all')
const statusFilter = ref('all')
const keyword = ref('')
const selectedRecordId = ref('')

const PAGE_SIZE = 6
const page = ref(1)

const openTasks = computed(() =>
  pickingTasks.value.filter((t) => !t.cancelled && !isTaskHandedOver(t))
)

const selectedTask = computed(() => openTasks.value.find((t) => t.id === form.taskId) ?? null)
const selectedItem = computed(() => selectedTask.value?.items.find((i) => i.id === form.itemId) ?? null)

const pendingCount = computed(() =>
  replenishRecords.value.filter((r) => r.status !== '已完成').length
)
const doneCount = computed(() =>
  replenishRecords.value.filter((r) => r.status === '已完成').length
)

const tabs = [
  { key: 'all', label: '全部记录' },
  { key: 'open', label: '处理中' },
  { key: 'done', label: '已完成' }
]

const statusOptions = computed(() => [
  'all',
  ...new Set(replenishRecords.value.map((r) => r.status).filter(Boolean))
])

const filteredRecords = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return replenishRecords.value.filter((row) => {
    const tabMatched =
      activeTab.value === 'all' ||
      (activeTab.value === 'open' && row.status !== '已完成') ||
      (activeTab.value === 'done' && row.status === '已完成')
    const statusMatched = statusFilter.value === 'all' || row.status === statusFilter.value
    const text = [row.workOrder, row.product, row.material, row.materialCode, row.reason, row.status]
      .join(' ')
      .toLowerCase()
    return tabMatched && statusMatched && (!kw || text.includes(kw))
  })
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredRecords.value.length / PAGE_SIZE)))

const pagedRecords = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return filteredRecords.value.slice(start, start + PAGE_SIZE)
})

const pageInfo = computed(() => {
  const total = filteredRecords.value.length
  if (!total) return '共 0 条'
  const start = (page.value - 1) * PAGE_SIZE + 1
  const end = Math.min(page.value * PAGE_SIZE, total)
  return `第 ${start}-${end} 条 / 共 ${total} 条`
})

const selectedRecord = computed(
  () =>
    filteredRecords.value.find((r) => r.id === selectedRecordId.value) ||
    filteredRecords.value[0] ||
    null
)

/** 根据缺件/未领齐物料给出快捷建议 */
const suggestions = computed(() => {
  const list = []
  for (const task of openTasks.value) {
    for (const item of task.items) {
      const remain = Math.max(0, item.required - item.scanned)
      const ex = hasOpenException(task) ? getTaskException(task) : null
      if (remain > 0 || (ex && ex.material === item.material)) {
        list.push({
          key: `${task.id}-${item.id}`,
          taskId: task.id,
          itemId: item.id,
          workOrder: task.workOrder,
          product: task.product,
          material: item.material,
          materialCode: item.materialCode,
          spec: item.spec || '—',
          qty: remain || ex?.shortage || 1,
          hint: ex && ex.material === item.material ? '关联异常缺件' : '未领齐'
        })
      }
    }
  }
  return list.slice(0, 5)
})

watch(filteredRecords, (list) => {
  if (page.value > totalPages.value) page.value = totalPages.value
  if (!list.length) {
    selectedRecordId.value = ''
    return
  }
  if (!list.find((r) => r.id === selectedRecordId.value)) {
    selectedRecordId.value = list[0].id
  }
})

watch([activeTab, statusFilter, keyword], () => {
  page.value = 1
})

onMounted(() => {
  if (!allowed.value) return
  loadWorkerData().then(() => {
    if (openTasks.value.length) {
      form.taskId = openTasks.value[0].id
      form.itemId = openTasks.value[0].items[0]?.id || ''
    }
    selectedRecordId.value = replenishRecords.value[0]?.id || ''
  })
})

function onTaskChange() {
  form.itemId = selectedTask.value?.items[0]?.id || ''
}

function adjustQty(delta) {
  form.qty = Math.min(99, Math.max(1, form.qty + delta))
}

function applySuggestion(row) {
  form.taskId = row.taskId
  form.itemId = row.itemId
  form.qty = Math.max(1, row.qty)
  form.reason = REPLENISH_REASONS[0]
  form.note = `${row.hint}，建议补发 ${row.qty} 件`
  submitted.value = false
  tip.value = `已填入建议：${row.workOrder} / ${row.material}`
}

function selectRecord(row) {
  selectedRecordId.value = row.id
}

function fillFromRecord(row) {
  const task = openTasks.value.find((t) => t.workOrder === row.workOrder)
  if (task) {
    form.taskId = task.id
    const item = task.items.find((i) => i.materialCode === row.materialCode || i.material === row.material)
    form.itemId = item?.id || task.items[0]?.id || ''
  }
  form.qty = row.qty
  form.reason = REPLENISH_REASONS.includes(row.reason) ? row.reason : REPLENISH_REASONS[0]
  form.note = row.note || ''
  submitted.value = false
  tip.value = `已按记录 ${row.id} 回填申请单`
}

function statusClass(status) {
  if (status === '已完成') return 'ok'
  if (status === '补发中') return 'warn'
  return 'danger'
}

async function handleSubmit() {
  if (!selectedTask.value || !selectedItem.value) {
    tip.value = '请选择关联工单与申请物料'
    return
  }
  submitting.value = true
  tip.value = ''
  try {
    const entry = await submitReplenish({
      pickingTaskId: resolvePickingTaskId(selectedTask.value),
      pickingLineId: resolvePickingLineId(selectedItem.value),
      qty: form.qty,
      reason: form.reason,
      note: form.note,
      workOrder: selectedTask.value.workOrder,
      product: selectedTask.value.product,
      material: selectedItem.value.material,
      materialCode: selectedItem.value.materialCode,
      spec: selectedItem.value.spec || '—'
    })
    selectedRecordId.value = entry.id
    activeTab.value = 'all'
    page.value = 1
    submitted.value = true
    tip.value = `补料申请已提交：${entry.workOrder} / ${entry.material} × ${entry.qty}`
  } catch (error) {
    tip.value = error.message || '提交补料申请失败'
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  submitted.value = false
  form.qty = 1
  form.note = ''
  form.reason = REPLENISH_REASONS[0]
}

function goPicking() {
  router.push(demoPreserveRoute(menuToPath('我的领料'), demoRoleId.value, isAdminDemo.value))
}

function prevPage() {
  if (page.value > 1) page.value -= 1
}

function nextPage() {
  if (page.value < totalPages.value) page.value += 1
}
</script>

<template>
  <section v-if="allowed" class="outbound-board quality-issue-board qa-redesign worker-redesign worker-replenish-page">
    <p v-if="workerDataLoading" class="data-hint">正在加载...</p>
    <p v-if="tip" class="data-hint">{{ tip }}</p>

    <header class="qa-issue-header">
      <div>
        <h1>补料申请中心 <span>i</span></h1>
      </div>
      <div class="qa-issue-stats">
        <div>
          <span class="qa-stat-icon">□</span>
          <small>处理中</small>
          <strong>{{ pendingCount }} <em>单</em></strong>
        </div>
        <div>
          <span class="qa-stat-icon">✓</span>
          <small>已完成</small>
          <strong>{{ doneCount }} <em>单</em></strong>
        </div>
      </div>
    </header>

    <div class="worker-exception-layout">
      <!-- 左：补料申请 -->
      <section class="qa-card worker-exception-form-card">
        <div class="worker-panel-title">
          <h2>补料申请</h2>
          <button type="button" class="worker-link-btn" @click="goPicking">← 返回领料</button>
        </div>

        <div v-if="suggestions.length" class="worker-suggest-box">
          <h3>建议补料</h3>
          <p class="worker-suggest-hint">根据未领齐 / 异常缺件自动推荐，点击一键填入</p>
          <button
            v-for="row in suggestions"
            :key="row.key"
            type="button"
            class="worker-suggest-item"
            @click="applySuggestion(row)"
          >
            <div>
              <strong>{{ row.material }}</strong>
              <small>{{ row.workOrder }} · {{ row.hint }}</small>
            </div>
            <em>×{{ row.qty }}</em>
          </button>
        </div>

        <div class="worker-form-field">
          <label>关联工单</label>
          <select v-model="form.taskId" class="wb-select" @change="onTaskChange">
            <option v-for="task in openTasks" :key="task.id" :value="task.id">
              {{ task.workOrder }} · {{ task.product }}
            </option>
          </select>
        </div>

        <div class="worker-form-field">
          <label>申请物料</label>
          <select v-model="form.itemId" class="wb-select">
            <option v-for="item in selectedTask?.items || []" :key="item.id" :value="item.id">
              {{ item.materialCode }} {{ item.material }}
            </option>
          </select>
        </div>

        <div class="worker-form-field">
          <label>规格型号</label>
          <input class="wb-input" type="text" :value="selectedItem?.spec || '—'" readonly />
        </div>

        <div class="worker-form-field">
          <label>申请数量</label>
          <div class="worker-stepper">
            <button type="button" @click="adjustQty(-1)">−</button>
            <strong>{{ form.qty }}</strong>
            <button type="button" @click="adjustQty(1)">+</button>
          </div>
        </div>

        <div class="worker-form-field">
          <label>申请原因</label>
          <select v-model="form.reason" class="wb-select">
            <option v-for="reason in REPLENISH_REASONS" :key="reason" :value="reason">{{ reason }}</option>
          </select>
        </div>

        <div class="worker-form-field">
          <label>备注说明</label>
          <textarea
            v-model="form.note"
            class="wb-textarea"
            rows="3"
            maxlength="200"
            placeholder="选填，补充现场情况"
          ></textarea>
          <small class="worker-char-count">{{ form.note.length }}/200</small>
        </div>

        <div class="worker-form-actions">
          <button
            v-if="!submitted"
            type="button"
            class="wb-btn-primary"
            :disabled="submitting"
            @click="handleSubmit"
          >{{ submitting ? '提交中...' : '提交申请' }}</button>
          <button v-else type="button" class="wb-btn-secondary" @click="resetForm">再提一单</button>
        </div>
      </section>

      <!-- 右：补料记录 -->
      <section class="qa-records-panel worker-exception-records">
        <div class="worker-panel-title records-title">
          <h2>补料记录</h2>
          <small>{{ pageInfo }}</small>
        </div>

        <div class="qa-tabs" role="tablist">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >{{ tab.label }}</button>
        </div>

        <div class="qa-filter-row worker-exception-filter">
          <label>
            <span>状态：</span>
            <select v-model="statusFilter">
              <option value="all">全部状态</option>
              <option v-for="status in statusOptions.filter((s) => s !== 'all')" :key="status" :value="status">{{ status }}</option>
            </select>
          </label>
          <label class="qa-search">
            <input v-model="keyword" type="search" placeholder="工单 / 物料 / 原因" />
            <span>⌕</span>
          </label>
        </div>

        <div class="qa-issue-table worker-exception-table">
          <div class="qa-issue-tr qa-issue-th worker-rep-row">
            <span>工单</span>
            <span>物料</span>
            <span>数量</span>
            <span>状态</span>
            <span>时间</span>
          </div>
          <button
            v-for="row in pagedRecords"
            :key="row.id"
            type="button"
            :class="['qa-issue-tr', 'worker-rep-row', { selected: row.id === selectedRecord?.id }]"
            @click="selectRecord(row)"
          >
            <span class="qa-code">{{ row.workOrder }}</span>
            <span>{{ row.material }}</span>
            <span>{{ row.qty }}</span>
            <span><em :class="['qa-dot-status', statusClass(row.status)]">{{ row.status }}</em></span>
            <span>{{ row.submittedAt }}</span>
          </button>
          <p v-if="!pagedRecords.length" class="qa-empty">暂无符合条件的补料记录</p>
        </div>

        <div class="worker-pager compact">
          <button type="button" class="wb-btn-secondary wb-btn-sm" :disabled="page <= 1" @click="prevPage">上一页</button>
          <span>{{ page }} / {{ totalPages }}</span>
          <button type="button" class="wb-btn-secondary wb-btn-sm" :disabled="page >= totalPages" @click="nextPage">下一页</button>
        </div>

        <div v-if="selectedRecord" class="worker-exception-detail">
          <h3>申请详情</h3>
          <dl class="qa-info-list">
            <div><dt>工单</dt><dd>{{ selectedRecord.workOrder }}</dd></div>
            <div><dt>产品</dt><dd>{{ selectedRecord.product }}</dd></div>
            <div><dt>物料</dt><dd>{{ selectedRecord.materialCode }} {{ selectedRecord.material }}</dd></div>
            <div><dt>规格</dt><dd>{{ selectedRecord.spec }}</dd></div>
            <div><dt>数量</dt><dd>{{ selectedRecord.qty }}</dd></div>
            <div><dt>原因</dt><dd>{{ selectedRecord.reason }}</dd></div>
            <div><dt>备注</dt><dd>{{ selectedRecord.note || '—' }}</dd></div>
            <div><dt>申请人</dt><dd>{{ selectedRecord.handler }}</dd></div>
          </dl>
          <div class="worker-exception-detail-actions">
            <button type="button" class="wb-btn-secondary wb-btn-sm" @click="fillFromRecord(selectedRecord)">据此再申请</button>
            <button type="button" class="wb-btn-primary wb-btn-sm" @click="goPicking">去我的领料</button>
          </div>
        </div>
      </section>
    </div>
  </section>
  <RoleAccessDenied v-else required-role-id="worker" module-name="补料申请" />
</template>
