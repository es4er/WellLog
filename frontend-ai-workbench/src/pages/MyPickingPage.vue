<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../composables/useSession'
import { useWorker } from '../composables/useWorker'
import { menuToPath } from '../router/menuRoutes'

const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'worker')

const {
  workerDataLoading,
  workerDataError,
  loadWorkerData,
  setActiveTask,
  confirmHandover,
  exceptionStatusClass,
  executionQueue,
  getTaskException,
  taskProgress,
  scanRecords
} = useWorker()

const expandedPanel = ref(null)
const handoverTip = ref('')

const queue = computed(() => executionQueue.value)

const summaryItems = computed(() => [
  { label: '待处理', value: queue.value.summary.pending, accent: false },
  { label: '待扫码', value: queue.value.summary.pendingScan, accent: false },
  { label: '缺件', value: queue.value.summary.shortage, accent: true },
  { label: '已完成', value: queue.value.summary.completed, accent: false }
])

const hasTodayTodo = computed(() => {
  const g = queue.value.todayGroups
  return g.scan.length + g.shortage.length + g.handover.length > 0
})

onMounted(() => {
  if (!allowed.value) {
    router.replace('/module/' + encodeURIComponent('我的领料'))
    return
  }
  loadWorkerData()
})

function togglePanel(key) {
  expandedPanel.value = expandedPanel.value === key ? null : key
}

function goScan(taskId, itemId) {
  setActiveTask(taskId, itemId)
  router.push(menuToPath('扫码确认'))
}

function handlePriorityAction(card, action) {
  const task = card.task
  if (action === 'scan') {
    const item = task.items.find((i) => i.scanned < i.required)
    goScan(task.id, item?.id)
  } else if (action === 'progress') {
    const ex = getTaskException(task)
    if (ex) togglePanel(`ex-${ex.id}`)
    else togglePanel(`detail-${task.id}`)
  } else if (action === 'handover') {
    handleHandover(task.id)
  } else if (action === 'detail') {
    togglePanel(`detail-${task.id}`)
  }
}

function handleHandover(taskId) {
  handoverTip.value = ''
  confirmHandover(taskId).then((ok) => {
    if (ok) {
      handoverTip.value = '交接已确认，任务已移入最近完成'
    } else {
      handoverTip.value = '尚有未扫齐物料或异常未关闭，请先完成扫码或等待补拣'
    }
  })
}

function getScanEvidence(workOrder) {
  return scanRecords.value.filter((r) => r.workOrder === workOrder)
}

function shortageLabel(task) {
  const ex = getTaskException(task)
  return ex ? `${ex.material}缺 ${ex.shortage} 件` : '缺件待处理'
}
</script>

<template>
  <section v-if="allowed" class="worker-board worker-queue">
    <p v-if="workerDataLoading" class="data-hint">正在加载领料任务...</p>
    <p v-else-if="workerDataError" class="data-hint danger">{{ workerDataError }}</p>

    <header class="queue-header">
      <h1>我的领料</h1>
    </header>

    <!-- 状态摘要（只读） -->
    <div class="queue-summary">
      <div v-for="item in summaryItems" :key="item.label" class="queue-summary-item">
        <span class="queue-summary-label">{{ item.label }}</span>
        <strong :class="{ accent: item.accent }">{{ item.value }}</strong>
      </div>
    </div>

    <p v-if="handoverTip" :class="['queue-tip', { danger: handoverTip.includes('尚有') }]">{{ handoverTip }}</p>

    <!-- 优先处理 -->
    <section class="queue-section priority-section">
      <h2 class="queue-section-title">优先处理</h2>
      <p class="queue-section-desc">系统推荐 · 请先完成这一单</p>

      <article v-if="queue.priority" class="queue-card priority-card">
        <div class="queue-card-top">
          <span :class="['queue-badge', queue.priority.badgeClass]">{{ queue.priority.badge }}</span>
          <strong class="queue-wo">{{ queue.priority.workOrder }}</strong>
        </div>
        <p class="queue-requisition">领料单：{{ queue.priority.requisition }}</p>
        <p class="queue-headline">{{ queue.priority.headline }}</p>
        <div class="queue-meta-row">
          <span>{{ queue.priority.meta }}</span>
          <i class="plan-status warn">{{ queue.priority.status }}</i>
        </div>
        <div class="queue-actions">
          <button type="button" class="queue-btn primary" @click="handlePriorityAction(queue.priority, queue.priority.primaryAction)">
            {{ queue.priority.primaryLabel }}
          </button>
          <button type="button" class="queue-btn secondary" @click="handlePriorityAction(queue.priority, queue.priority.secondaryAction)">
            {{ queue.priority.secondaryLabel }}
          </button>
        </div>

        <div v-if="queue.priority.secondaryAction === 'progress' && expandedPanel === `ex-${getTaskException(queue.priority.task)?.id}`" class="queue-expand">
          <div class="worker-progress-steps">
            <div class="worker-progress-step done"><span>1</span><strong>工人提交异常</strong><em>{{ getTaskException(queue.priority.task)?.submittedAt }}</em></div>
            <div class="worker-progress-step current"><span>2</span><strong>仓管员补拣</strong><em>处理中</em></div>
            <div class="worker-progress-step"><span>3</span><strong>继续扫码确认</strong><em>等待</em></div>
          </div>
        </div>
      </article>

      <div v-else class="queue-empty-card">
        <span>✓</span>
        <p>当前没有待处理任务，辛苦了</p>
      </div>
    </section>

    <!-- 今日待办 -->
    <section class="queue-section">
      <h2 class="queue-section-title">今日待办</h2>
      <p class="queue-section-desc">自动分组 · 无需切换筛选</p>

      <template v-if="hasTodayTodo">
        <!-- 待扫码 -->
        <div v-if="queue.todayGroups.scan.length" class="queue-group">
          <h3 class="queue-group-title">待扫码</h3>
          <div class="queue-list">
            <article v-for="task in queue.todayGroups.scan" :key="task.id" class="queue-row-card">
              <div class="queue-row-main">
                <strong>{{ task.workOrder }}</strong>
                <span>{{ task.product }}</span>
                <em>{{ taskProgress(task).scannedItems }}/{{ taskProgress(task).totalItems }}</em>
              </div>
              <button type="button" class="queue-btn compact primary" @click="goScan(task.id)">
                {{ taskProgress(task).scannedItems > 0 ? '继续扫码' : '开始扫码' }}
              </button>
            </article>
          </div>
        </div>

        <!-- 缺件待处理 -->
        <div v-if="queue.todayGroups.shortage.length" class="queue-group">
          <h3 class="queue-group-title danger">缺件待处理</h3>
          <div class="queue-list">
            <article v-for="task in queue.todayGroups.shortage" :key="task.id" class="queue-row-card warn">
              <div class="queue-row-main">
                <strong>{{ task.workOrder }}</strong>
                <span class="shortage-gap">{{ shortageLabel(task) }}</span>
              </div>
              <div class="queue-row-actions">
                <button type="button" class="queue-btn compact secondary" @click="togglePanel(`ex-${getTaskException(task)?.id}`)">查看进度</button>
                <button type="button" class="queue-btn compact primary" @click="goScan(task.id)">继续扫码</button>
              </div>
              <div v-if="expandedPanel === `ex-${getTaskException(task)?.id}`" class="queue-expand inline">
                <p>状态：<i :class="['plan-status', exceptionStatusClass(getTaskException(task)?.status)]">{{ getTaskException(task)?.status }}</i></p>
                <p class="queue-expand-note">{{ getTaskException(task)?.note }}</p>
              </div>
            </article>
          </div>
        </div>

        <!-- 待交接 -->
        <div v-if="queue.todayGroups.handover.length" class="queue-group">
          <h3 class="queue-group-title ok">待交接</h3>
          <div class="queue-list">
            <article v-for="task in queue.todayGroups.handover" :key="task.id" class="queue-row-card ok">
              <div class="queue-row-main">
                <strong>{{ task.workOrder }}</strong>
                <span>{{ task.product }} · 已扫码完成</span>
              </div>
              <button type="button" class="queue-btn compact ok" @click="handleHandover(task.id)">确认交接</button>
            </article>
          </div>
        </div>
      </template>

      <div v-else class="queue-empty-inline">今日其余任务已全部处理完毕</div>
    </section>

    <!-- 最近完成 -->
    <section class="queue-section completed-section">
      <h2 class="queue-section-title muted">最近完成</h2>
      <p class="queue-section-desc">仅追溯 · 不影响当前执行</p>

      <div v-if="queue.recentCompleted.length" class="queue-list">
        <article v-for="task in queue.recentCompleted" :key="task.id" class="queue-row-card readonly">
          <div class="queue-row-main">
            <strong>{{ task.workOrder }}</strong>
            <span>已交接</span>
            <em>{{ task.handoverTime }}</em>
          </div>
          <button type="button" class="queue-btn compact ghost" @click="togglePanel(`done-${task.id}`)">查看记录</button>
          <div v-if="expandedPanel === `done-${task.id}`" class="queue-expand inline">
            <div class="worker-readonly-list compact">
              <p><span>领料单</span><strong>{{ task.requisition }}</strong></p>
              <p><span>产品</span><strong>{{ task.product }}</strong></p>
              <p><span>交接人</span><strong>{{ task.handler }}</strong></p>
            </div>
            <div v-if="getScanEvidence(task.workOrder).length" class="queue-evidence-mini">
              <span>扫码证据 {{ getScanEvidence(task.workOrder).length }} 条</span>
            </div>
          </div>
        </article>
      </div>
      <div v-else class="queue-empty-inline">暂无已完成记录</div>
    </section>
  </section>
</template>
