<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useDisplayName, useRoleAccess } from '../composables/useRoleAccess'
import { useWorker } from '../composables/useWorker'
import RoleAccessDenied from '../components/RoleAccessDenied.vue'
import WorkerReplenishPanel from '../components/WorkerReplenishPanel.vue'

const { allowed } = useRoleAccess('worker')
const { displayName } = useDisplayName()

const {
  workerDataLoading,
  workerDataError,
  loadWorkerData,
  loadReplenishRecords,
  pickingTasks,
  workerExceptions,
  workerNotifications,
  markNotificationRead,
  TODAY,
  taskProgress,
  displayTaskStatus,
  pickingStatusClass,
  confirmHandover,
  isTaskHandedOver,
  hasOpenException,
  getTaskException
} = useWorker()

const PAGE_SIZE = 5
const activeTab = ref('all')
const keyword = ref('')
const priorityFilter = ref('all')
const page = ref(1)
const detailTaskId = ref('')
const replenishTaskId = ref('')
const actionTip = ref('')
const confirmingId = ref('')

const stationMeta = {
  station: 'A 线 · 工位 03',
  shift: '白班 08:00–16:00',
  pda: '在线',
  pdaId: 'PDA-10086'
}

const now = ref(new Date())
let clockTimer = null

function formatNowLabel(d) {
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return {
    time: `${hh}:${mm}`,
    date: `${y}/${m}/${day} 星期${week}`
  }
}

const nowLabel = computed(() => formatNowLabel(now.value))

function classifyTask(task) {
  if (task.cancelled) return 'cancelled'
  if (isTaskHandedOver(task)) return 'done'
  if (hasOpenException(task) || task.status === '补料/异常') return 'exception'
  return 'pending-pick'
}

const todayTasks = computed(() =>
  pickingTasks.value.filter((t) => !t.cancelled && (t.planDate === TODAY || !isTaskHandedOver(t)))
)

const stats = computed(() => {
  const list = pickingTasks.value.filter((t) => !t.cancelled)
  const pending = list.filter((t) => !isTaskHandedOver(t)).length
  const pendingPick = list.filter((t) => classifyTask(t) === 'pending-pick').length
  const replenish = list.filter((t) => hasOpenException(t) && !isTaskHandedOver(t)).length
  const exceptions = workerExceptions.value.filter((ex) => ex.status !== '已关闭').length
  const done = list.filter((t) => isTaskHandedOver(t)).length
  return [
    { key: 'pending', label: '待任务', value: pending, tone: '', filter: 'all' },
    { key: 'pick', label: '待领料', value: pendingPick, tone: '', filter: 'pending' },
    { key: 'replenish', label: '补料中', value: replenish, tone: 'warn', filter: 'exception' },
    { key: 'exception', label: '异常数', value: exceptions, tone: 'danger', filter: 'exception' },
    { key: 'done', label: '已完成', value: done, tone: 'ok', filter: 'done' }
  ]
})

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待领料' },
  { key: 'exception', label: '异常/补料' },
  { key: 'done', label: '已完成' }
]

const filteredTasks = computed(() => {
  const source = activeTab.value === 'done'
    ? pickingTasks.value.filter((t) => !t.cancelled && isTaskHandedOver(t))
    : todayTasks.value
  const kw = keyword.value.trim().toLowerCase()
  return source.filter((task) => {
    const kind = classifyTask(task)
    const tabMatched =
      (activeTab.value === 'all' && kind !== 'done') ||
      (activeTab.value === 'pending' && kind === 'pending-pick') ||
      (activeTab.value === 'exception' && kind === 'exception') ||
      (activeTab.value === 'done' && kind === 'done')
    const priorityMatched = priorityFilter.value === 'all' || (task.priority || '中') === priorityFilter.value
    const text = [task.workOrder, task.product, task.requisition, statusLabel(task), task.priority]
      .join(' ')
      .toLowerCase()
    const keywordMatched = !kw || text.includes(kw)
    return tabMatched && priorityMatched && keywordMatched
  })
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredTasks.value.length / PAGE_SIZE)))

const pagedTasks = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return filteredTasks.value.slice(start, start + PAGE_SIZE)
})

const pageInfo = computed(() => {
  const total = filteredTasks.value.length
  if (!total) return '共 0 条'
  const start = (page.value - 1) * PAGE_SIZE + 1
  const end = Math.min(page.value * PAGE_SIZE, total)
  return `第 ${start}-${end} 条 / 共 ${total} 条`
})

const detailTask = computed(
  () => pickingTasks.value.find((t) => t.id === detailTaskId.value) ?? null
)

const replenishTask = computed(
  () => pickingTasks.value.find((t) => t.id === replenishTaskId.value) ?? null
)

watch([activeTab, keyword, priorityFilter], () => {
  page.value = 1
})

watch(filteredTasks, (list) => {
  if (page.value > totalPages.value) page.value = totalPages.value
  if (detailTaskId.value && !pickingTasks.value.find((t) => t.id === detailTaskId.value)) {
    detailTaskId.value = ''
  }
})

onMounted(async () => {
  now.value = new Date()
  clockTimer = setInterval(() => {
    now.value = new Date()
  }, 60_000)
  if (!allowed.value) return
  await loadWorkerData()
  await loadReplenishRecords()
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
})

function onStatClick(stat) {
  activeTab.value = stat.filter
  page.value = 1
}

function prevPage() {
  if (page.value > 1) page.value -= 1
}

function nextPage() {
  if (page.value < totalPages.value) page.value += 1
}

function statusLabel(task) {
  const kind = classifyTask(task)
  if (kind === 'pending-pick') {
    if (task.status === '备料区待领' || task.status === '备料区') return '备料区待领'
    return '待领料'
  }
  if (kind === 'exception') return '补料/异常'
  if (kind === 'done') return '已完成'
  return displayTaskStatus(task)
}

function priorityClass(priority) {
  if (priority === '高') return 'danger'
  if (priority === '中') return 'warn'
  return 'ok'
}

function canConfirmPickup(task) {
  return !task.cancelled && !isTaskHandedOver(task)
}

function openDetail(task) {
  detailTaskId.value = task.id
}

function closeDetail() {
  detailTaskId.value = ''
}

function openReplenish(task) {
  replenishTaskId.value = task.id
}

function closeReplenish() {
  replenishTaskId.value = ''
}

async function handleConfirmPickup(task) {
  if (!canConfirmPickup(task)) return
  confirmingId.value = task.id
  actionTip.value = ''
  try {
    await confirmHandover(task.id)
    actionTip.value = `已确认领料：${task.workOrder}`
    closeDetail()
  } catch (error) {
    actionTip.value = error.message || '确认领料失败'
  } finally {
    confirmingId.value = ''
  }
}

async function onReplenishSubmitted() {
  await loadWorkerData()
  await loadReplenishRecords()
}

function itemRemaining(item) {
  return Math.max(0, item.required - item.scanned)
}
</script>

<template>
  <section v-if="allowed" class="outbound-board quality-issue-board qa-redesign worker-redesign worker-today-page">
    <p v-if="workerDataLoading" class="data-hint">正在加载今日任务...</p>
    <p v-else-if="workerDataError" class="data-hint danger">{{ workerDataError }}</p>
    <p v-else-if="actionTip" class="data-hint">{{ actionTip }}</p>

    <header class="qa-issue-header worker-today-header">
      <div>
        <p class="worker-today-clock">{{ nowLabel.time }} · {{ nowLabel.date }}</p>
        <h1>你好，{{ displayName }}</h1>
      </div>
      <div class="worker-station-panel">
        <div>
          <small>工位</small>
          <strong>{{ stationMeta.station }}</strong>
        </div>
        <div>
          <small>班次</small>
          <strong>{{ stationMeta.shift }}</strong>
        </div>
        <div>
          <small>PDA 状态</small>
          <strong class="ok">{{ stationMeta.pda }} <em>{{ stationMeta.pdaId }}</em></strong>
        </div>
      </div>
    </header>

    <section v-if="workerNotifications.length" class="worker-notify-strip">
      <div class="worker-notify-strip__head">
        <strong>备料区通知</strong>
        <span v-if="workerNotifications[0] && !workerNotifications[0].read" class="worker-notify-badge">最新</span>
      </div>
      <button
        type="button"
        :class="['worker-notify-item', { unread: !workerNotifications[0].read }]"
        @click="markNotificationRead(workerNotifications[0].id)"
      >
        <span>{{ workerNotifications[0].title }}</span>
        <small>{{ workerNotifications[0].content }}</small>
      </button>
    </section>

    <section class="worker-metric-strip" aria-label="今日任务指标">
      <button
        v-for="card in stats"
        :key="card.key"
        type="button"
        :class="['worker-metric-item', card.tone, { active: activeTab === card.filter }]"
        @click="onStatClick(card)"
      >
        <small>{{ card.label }}</small>
        <strong>{{ card.value }}</strong>
      </button>
    </section>

    <div class="worker-today-main worker-today-main--full">
      <section class="qa-records-panel worker-today-list">
        <div class="worker-panel-title">
          <h2>今日生产任务</h2>
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

        <div class="qa-filter-row worker-today-filter">
          <label>
            <span>优先级：</span>
            <select v-model="priorityFilter">
              <option value="all">全部</option>
              <option value="高">高</option>
              <option value="中">中</option>
              <option value="低">低</option>
            </select>
          </label>
          <label class="qa-search">
            <input v-model="keyword" type="search" placeholder="搜索工单号 / 产品 / 状态" />
            <span>⌕</span>
          </label>
        </div>

        <div class="qa-issue-table worker-today-table">
          <div class="qa-issue-tr qa-issue-th worker-today-row worker-today-row--actions">
            <span>工单号</span>
            <span>产品</span>
            <span>物料项</span>
            <span>状态</span>
            <span>操作</span>
          </div>
          <div
            v-for="task in pagedTasks"
            :key="task.id"
            class="qa-issue-tr worker-today-row worker-today-row--actions"
          >
            <span>
              <button type="button" class="worker-order-link qa-code" @click="openDetail(task)">
                {{ task.workOrder }}
              </button>
            </span>
            <span>
              {{ task.product }}
              <em :class="['qa-pill', priorityClass(task.priority)]">{{ task.priority || '中' }}</em>
            </span>
            <span>{{ taskProgress(task).totalItems }} 项</span>
            <span><em :class="['qa-dot-status', pickingStatusClass(statusLabel(task))]">{{ statusLabel(task) }}</em></span>
            <span class="worker-row-actions">
              <button
                type="button"
                class="wb-btn-primary wb-btn-sm"
                :disabled="!canConfirmPickup(task) || confirmingId === task.id"
                @click="handleConfirmPickup(task)"
              >
                {{ confirmingId === task.id ? '确认中...' : '确认领料' }}
              </button>
              <button
                type="button"
                class="wb-btn-secondary wb-btn-sm"
                :disabled="isTaskHandedOver(task) || task.cancelled"
                @click="openReplenish(task)"
              >
                缺料
              </button>
            </span>
          </div>
          <p v-if="!pagedTasks.length" class="qa-empty">当前筛选暂无任务</p>
        </div>

        <div class="worker-pager">
          <button type="button" class="wb-btn-secondary wb-btn-sm" :disabled="page <= 1" @click="prevPage">上一页</button>
          <span>{{ page }} / {{ totalPages }}</span>
          <button type="button" class="wb-btn-secondary wb-btn-sm" :disabled="page >= totalPages" @click="nextPage">下一页</button>
        </div>
      </section>
    </div>

    <!-- 工单详情 -->
    <div v-if="detailTask" class="worker-overlay" @click.self="closeDetail">
      <section class="qa-card worker-task-detail-modal" role="dialog">
        <header class="worker-task-detail-modal__head">
          <div>
            <h2>当前工单详细信息</h2>
            <p>{{ detailTask.workOrder }} · {{ detailTask.product }}</p>
          </div>
          <button type="button" class="worker-modal-close" aria-label="关闭" @click="closeDetail">×</button>
        </header>

        <dl class="qa-info-list worker-detail-grid">
          <div><dt>领料单</dt><dd>{{ detailTask.requisition }}</dd></div>
          <div><dt>计划数量</dt><dd>{{ detailTask.planQty }} {{ detailTask.unit }}</dd></div>
          <div><dt>计划开始</dt><dd>{{ detailTask.planDate }} {{ detailTask.expectedTime }}</dd></div>
          <div><dt>优先级</dt><dd><em :class="['qa-pill', priorityClass(detailTask.priority)]">{{ detailTask.priority || '中' }}</em></dd></div>
          <div><dt>状态</dt><dd><em :class="['qa-dot-status', pickingStatusClass(statusLabel(detailTask))]">{{ statusLabel(detailTask) }}</em></dd></div>
          <div v-if="getTaskException(detailTask)">
            <dt>异常</dt>
            <dd class="danger">{{ getTaskException(detailTask).material }} · {{ getTaskException(detailTask).status }}</dd>
          </div>
        </dl>

        <section class="worker-task-detail-materials">
          <h3>物料清单</h3>
          <div class="qa-issue-table worker-material-table compact">
            <div class="qa-issue-tr qa-issue-th worker-mat-row">
              <span>物料编码 / 名称</span>
              <span>规格</span>
              <span>库位</span>
              <span>应领</span>
              <span>状态</span>
            </div>
            <div v-for="item in detailTask.items" :key="item.id" class="qa-issue-tr worker-mat-row">
              <span>
                <b class="qa-code">{{ item.materialCode }}</b>
                <em>{{ item.material }}</em>
              </span>
              <span>{{ item.spec || '—' }}</span>
              <span class="qa-code muted">{{ item.location || '—' }}</span>
              <span>{{ item.required }}</span>
              <span>
                <em v-if="itemRemaining(item) <= 0" class="qa-pill ok">已备齐</em>
                <em v-else class="qa-pill warn">缺 {{ itemRemaining(item) }}</em>
              </span>
            </div>
          </div>
        </section>

        <footer class="worker-task-detail-modal__foot">
          <button type="button" class="wb-btn-secondary" @click="closeDetail">关闭</button>
          <button
            type="button"
            class="wb-btn-secondary"
            :disabled="isTaskHandedOver(detailTask) || detailTask.cancelled"
            @click="openReplenish(detailTask); closeDetail()"
          >
            缺料
          </button>
          <button
            type="button"
            class="wb-btn-primary"
            :disabled="!canConfirmPickup(detailTask) || confirmingId === detailTask.id"
            @click="handleConfirmPickup(detailTask)"
          >
            {{ confirmingId === detailTask.id ? '确认中...' : '确认领料' }}
          </button>
        </footer>
      </section>
    </div>

    <WorkerReplenishPanel
      :open="Boolean(replenishTaskId)"
      :task="replenishTask"
      @close="closeReplenish"
      @submitted="onReplenishSubmitted"
    />
  </section>
  <RoleAccessDenied v-else required-role-id="worker" module-name="今日生产任务" />
</template>
