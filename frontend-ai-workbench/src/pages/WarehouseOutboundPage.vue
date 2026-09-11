<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSession } from '../composables/useSession'
import { useRoleAccess } from '../composables/useRoleAccess'
import { useWarehouse } from '../composables/useWarehouse'
import { useAgentTask } from '../composables/useAgentTask'
import { useClientPager } from '../composables/useClientPager'

const route = useRoute()
const router = useRouter()
const { prompt } = useSession()
const { allowed } = useRoleAccess('warehouse')

const {
  readyPendingRequisitions,
  failedPendingRequisitions,
  outboundOrders,
  pickingTasks,
  pendingCount,
  failedPendingCount,
  outboundCount,
  pickingPendingCount,
  pickingActiveCount,
  pickingPrepCount,
  pickingReviewCount,
  statusClass,
  warehouseTip,
  warehouseDataLoading,
  warehouseDataError,
  loadWarehouseData,
  setPendingOutboundRequisition,
  pickingByTab,
  coordinationNotices,
  coordinationOpenCount,
  handleCoordinationNotice
} = useWarehouse()

const { startOutboundAgentTask, agentTaskLoading, agentTaskError } = useAgentTask()

const TAB_KEYS = new Set([
  'pendingOrder',
  'failedOrder',
  'pendingPick',
  'picking',
  'prep',
  'review',
  'generated',
  'all'
])

function resolveInitialTab(value) {
  return TAB_KEYS.has(String(value || '')) ? String(value) : 'pendingOrder'
}

const activeTab = ref(resolveInitialTab(route.query.tab))
const keyword = ref('')

const completedOutboundCount = computed(() =>
  outboundOrders.value.filter((row) => ['已出库', '已完成'].includes(row.status)).length
)

const tabs = computed(() => [
  { id: 'pendingOrder', label: '待生成', count: pendingCount.value },
  { id: 'failedOrder', label: '生成失败', count: failedPendingCount.value },
  { id: 'pendingPick', label: '待拣货', count: pickingPendingCount.value },
  { id: 'picking', label: '拣货中', count: pickingActiveCount.value },
  { id: 'prep', label: '备料区', count: pickingPrepCount.value },
  { id: 'review', label: '待复核', count: pickingReviewCount.value },
  { id: 'generated', label: '已生成出库单', count: outboundCount.value },
  {
    id: 'all',
    label: '全部记录',
    count:
      pendingCount.value +
      failedPendingCount.value +
      pickingTasks.value.length +
      outboundOrders.value.length
  }
])

const summaryCards = computed(() => [
  {
    label: '待生成任务',
    value: pendingCount.value + failedPendingCount.value,
    meta: `其中失败待处理 ${failedPendingCount.value} 单`,
    tone: 'green'
  },
  {
    label: '拣货中任务',
    value: pickingActiveCount.value,
    meta: `另有待拣货 ${pickingPendingCount.value} 单`,
    tone: 'blue'
  },
  {
    label: '待复核任务',
    value: pickingReviewCount.value,
    meta: `备料区 ${pickingPrepCount.value} 单`,
    tone: 'orange'
  },
  {
    label: '已生成出库单',
    value: outboundCount.value,
    meta: `已完成 ${completedOutboundCount.value} 单`,
    tone: 'deep'
  }
])

const overviewSegments = computed(() => {
  const rows = [
    { label: '待拣货', value: pickingPendingCount.value, color: '#86b6d6' },
    { label: '拣货中', value: pickingActiveCount.value, color: '#437a9e' },
    { label: '待复核', value: pickingReviewCount.value, color: '#c98a2e' },
    { label: '已生成', value: outboundCount.value, color: '#3b7a5a' }
  ]
  const totalValue = rows.reduce((sum, item) => sum + item.value, 0)
  return rows.map((item) => ({
    ...item,
    percent: totalValue ? Math.round((item.value / totalValue) * 100) : 0
  }))
})

const overviewTotal = computed(() =>
  overviewSegments.value.reduce((sum, item) => sum + item.value, 0)
)

const overviewDonutStyle = computed(() => {
  const totalValue = overviewTotal.value
  if (!totalValue) return 'conic-gradient(#e8efeb 0deg 360deg)'

  let cursor = 0
  const stops = overviewSegments.value.map((item) => {
    const start = cursor
    cursor += (item.value / totalValue) * 360
    return `${item.color} ${start}deg ${cursor}deg`
  })
  return `conic-gradient(${stops.join(', ')})`
})

function dateKey(value) {
  const matched = String(value ?? '').match(/\d{4}-\d{2}-\d{2}/)
  return matched?.[0] ?? ''
}

const outboundTrend = computed(() => {
  const counts = new Map()
  outboundOrders.value.forEach((row) => {
    const key = dateKey(row.createdAt || row.demandDate)
    if (key) counts.set(key, (counts.get(key) || 0) + 1)
  })

  const dates = []
  const now = new Date()
  for (let offset = 6; offset >= 0; offset -= 1) {
    const day = new Date(now)
    day.setDate(now.getDate() - offset)
    const year = day.getFullYear()
    const month = String(day.getMonth() + 1).padStart(2, '0')
    const date = String(day.getDate()).padStart(2, '0')
    const key = `${year}-${month}-${date}`
    dates.push({ key, label: `${month}-${date}`, value: counts.get(key) || 0 })
  }

  const maxValue = Math.max(1, ...dates.map((item) => item.value))
  return dates.map((item) => ({
    ...item,
    height: item.value ? Math.max(20, Math.round((item.value / maxValue) * 100)) : 6
  }))
})

const workflowSteps = computed(() => [
  {
    no: 1,
    label: '任务分配',
    hint: '根据领料单生成出库单',
    count: pendingCount.value + failedPendingCount.value,
    state: pendingCount.value || failedPendingCount.value ? 'active' : 'done'
  },
  {
    no: 2,
    label: '拣货作业',
    hint: '按推荐库位 PDA 扫码',
    count: pickingPendingCount.value + pickingActiveCount.value,
    state: pickingActiveCount.value ? 'active' : pickingPendingCount.value ? 'waiting' : 'done'
  },
  {
    no: 3,
    label: '复核确认',
    hint: '核对数量、批次与质量',
    count: pickingReviewCount.value,
    state: pickingReviewCount.value ? 'active' : 'waiting'
  },
  {
    no: 4,
    label: '出库完成',
    hint: '完成出库与生产交接',
    count: completedOutboundCount.value,
    state: completedOutboundCount.value ? 'done' : 'waiting'
  },
  {
    no: 5,
    label: '记录归档',
    hint: '保留出库过程与操作记录',
    count: outboundCount.value,
    state: outboundCount.value ? 'done' : 'waiting'
  }
])

const allRecords = computed(() => [
  ...readyPendingRequisitions.value.map((row) => ({
    ...row,
    recordKey: `pending-${row.id}`,
    recordType: '待生成出库单',
    recordKind: 'pendingOrder',
    businessNo: row.id,
    relatedNo: row.plan || row.workOrder || '—',
    itemText: `${row.itemCount ?? 0} 项`,
    timeText: row.demandDate || '—'
  })),
  ...failedPendingRequisitions.value.map((row) => ({
    ...row,
    recordKey: `failed-${row.id}`,
    recordType: '生成失败',
    recordKind: 'failedOrder',
    businessNo: row.id,
    relatedNo: row.plan || row.workOrder || '—',
    itemText: `${row.itemCount ?? 0} 项`,
    timeText: row.demandDate || '—'
  })),
  ...pickingTasks.value.map((row) => ({
    ...row,
    recordKey: `picking-${row.id}`,
    recordType: '拣货任务',
    recordKind: 'pickingTask',
    businessNo: row.id,
    relatedNo: row.outbound || row.workOrder || '—',
    itemText: `${row.itemCount ?? row.progress?.total ?? 0} 项`,
    timeText: row.zone || row.assigneeName || '—'
  })),
  ...outboundOrders.value.map((row) => ({
    ...row,
    recordKey: `outbound-${row.id}`,
    recordType: '出库单',
    recordKind: 'outboundOrder',
    businessNo: row.id,
    relatedNo: row.requisition || row.workOrder || '—',
    itemText: `${row.itemCount ?? 0} 项`,
    timeText: row.createdAt || '—'
  }))
])

const sourceList = computed(() => {
  if (activeTab.value === 'pendingOrder') return readyPendingRequisitions.value
  if (activeTab.value === 'failedOrder') return failedPendingRequisitions.value
  if (activeTab.value === 'pendingPick') return pickingByTab('pending')
  if (activeTab.value === 'picking') return pickingByTab('picking')
  if (activeTab.value === 'prep') return pickingByTab('prep')
  if (activeTab.value === 'review') return pickingByTab('review')
  if (activeTab.value === 'generated') return outboundOrders.value
  return allRecords.value
})

function rowSearchText(row) {
  return [
    row.id,
    row.plan,
    row.workOrder,
    row.requisition,
    row.outbound,
    row.zone,
    row.status,
    row.assigneeName,
    row.recordType,
    row.businessNo,
    row.relatedNo
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
}

const filteredList = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return sourceList.value
  return sourceList.value.filter((row) => rowSearchText(row).includes(value))
})

const { currentPage, total, totalPages, pagedList, prevPage, nextPage, resetPage } =
  useClientPager(filteredList, 5)

watch([activeTab, keyword], () => resetPage())

watch(
  () => route.query.tab,
  (tab) => {
    const nextTab = resolveInitialTab(tab)
    if (activeTab.value !== nextTab) activeTab.value = nextTab
  }
)

onMounted(() => {
  if (allowed.value) loadWarehouseData()
})

function selectTab(tabId) {
  activeTab.value = tabId
  router.replace({ query: { ...route.query, tab: tabId } })
}

async function refreshData() {
  await loadWarehouseData()
}

async function generateOutbound(row) {
  const retry = row.status === '生成失败·待处理'
  prompt.value = retry
    ? `根据领料单 ${row.id} 重新生成出库单，完成库存校验与批次库位推荐。`
    : `根据领料单 ${row.id} 生成出库单，完成库存校验与批次库位推荐。`
  setPendingOutboundRequisition(row.id)
  await startOutboundAgentTask({
    requisitionId: row.requisitionId,
    planId: row.planId,
    planNo: row.plan,
    taskName: retry ? `仓管重新生成出库单-${row.id}` : `仓管生成出库单-${row.id}`,
    promptText: prompt.value,
    businessNo: row.id,
    pendingRequisitionNo: row.id
  })
}

function viewOutbound(row) {
  router.push(`/outbound-orders/${row.id}`)
}

function viewException() {
  router.push({ path: '/warehouse-exceptions', query: { tab: 'generate' } })
}

function openDetail(taskId) {
  router.push(`/warehouse-picking/${taskId}`)
}

function openScan(taskId) {
  router.push(`/warehouse-pick-scan/${taskId}`)
}

function openRecord(row) {
  if (row.recordKind === 'pendingOrder' || row.recordKind === 'failedOrder') {
    generateOutbound(row)
    return
  }
  if (row.recordKind === 'outboundOrder') {
    viewOutbound(row)
    return
  }
  if (row.status === '待拣货' || row.status === '拣货中') {
    openScan(row.id)
    return
  }
  openDetail(row.id)
}

function recordActionText(row) {
  if (row.recordKind === 'pendingOrder') return '生成出库单'
  if (row.recordKind === 'failedOrder') return '重新生成'
  if (row.recordKind === 'outboundOrder') return '查看出库单'
  if (row.status === '待拣货') return '开始拣货'
  if (row.status === '拣货中') return '继续扫码'
  if (row.status === '待复核') return '复核'
  return '查看详情'
}
</script>

<template>
  <section v-if="allowed" class="outbound-hub">
    <div class="dashboard-top">
      <div class="dashboard-main">
        <header class="hub-header">
          <div>
        <h1>出库管理</h1>
      </div>
          <div class="header-actions">
            <button type="button" class="secondary-action" :disabled="warehouseDataLoading" @click="refreshData">
              {{ warehouseDataLoading ? '刷新中...' : '刷新数据' }}
            </button>
            <button type="button" class="primary-action" @click="router.push('/warehouse-pick-scan')">
              PDA 扫码拣货
            </button>
          </div>
        </header>

        <div class="workflow-card">
          <template v-for="(step, index) in workflowSteps" :key="step.no">
            <article :class="['workflow-step', step.state]">
              <span class="workflow-icon">{{ step.no }}</span>
              <div>
                <strong>{{ step.label }}</strong>
                <small>{{ step.hint }}</small>
              </div>
              <em>{{ step.count }}</em>
            </article>
            <span v-if="index < workflowSteps.length - 1" class="workflow-arrow">→</span>
          </template>
        </div>

        <div class="summary-grid">
          <article v-for="card in summaryCards" :key="card.label" :class="['summary-card', card.tone]">
            <div class="summary-card-title">
              <span>{{ card.label }}</span>
            </div>
            <div class="summary-card-value">
              <strong>{{ card.value }}</strong>
              <b>单</b>
            </div>
            <small>{{ card.meta }}</small>
          </article>
        </div>
      </div>

      <aside class="overview-card">
        <h2>出库任务概览</h2>
        <div class="overview-content">
          <div class="overview-donut" :style="{ background: overviewDonutStyle }">
            <div>
              <strong>{{ overviewTotal }}</strong>
              <span>总任务</span>
            </div>
          </div>
          <ul class="overview-legend">
            <li v-for="item in overviewSegments" :key="item.label">
              <i :style="{ background: item.color }"></i>
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}（{{ item.percent }}%）</strong>
            </li>
          </ul>
        </div>

        <div class="trend-block">
          <h3>近 7 日出库趋势（单）</h3>
          <div class="trend-chart">
            <div v-for="item in outboundTrend" :key="item.key" class="trend-column">
              <div class="trend-value">{{ item.value }}</div>
              <div class="trend-bar-track">
                <span :style="{ height: `${item.height}%` }"></span>
              </div>
              <small>{{ item.label }}</small>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <p v-if="warehouseDataLoading" class="hub-message">正在加载出库管理数据...</p>
    <p v-if="warehouseDataError" class="hub-message danger">{{ warehouseDataError }}</p>
    <p v-if="warehouseTip" class="hub-message success">{{ warehouseTip }}</p>
    <p v-if="agentTaskError" class="hub-message danger">{{ agentTaskError }}</p>

    <section v-if="coordinationNotices.length" class="coord-notice-card">
      <div class="coord-notice-head">
        <h2>PMC 协同通知<em v-if="coordinationOpenCount" class="coord-badge">{{ coordinationOpenCount }}</em></h2>
        <small>PMC 计划员发起的催出库 / 补料调拨 / 复核跟进事项</small>
      </div>
      <ul class="coord-notice-list">
        <li v-for="n in coordinationNotices" :key="n.noticeId" :class="{ done: n.status === 'DONE' }">
          <div class="coord-notice-tags">
            <i class="coord-tag type">{{ n.noticeTypeLabel }}</i>
            <i :class="['coord-tag', n.status === 'DONE' ? 'ok' : n.status === 'READ' ? 'warn' : 'open']">
              {{ n.statusLabel }}
            </i>
            <span class="coord-notice-plan" v-if="n.planNo">计划 {{ n.planNo }}</span>
          </div>
          <strong>{{ n.title }}</strong>
          <p>{{ n.content }}</p>
          <div class="coord-notice-foot">
            <small>{{ n.createdByName || 'PMC计划员' }} · {{ n.createdAt }}<template v-if="n.handledBy"> · 处理人 {{ n.handledBy }}</template></small>
            <button
              v-if="n.status !== 'DONE'"
              type="button"
              class="coord-notice-btn"
              @click="handleCoordinationNotice(n.noticeId)"
            >标记处理</button>
          </div>
        </li>
      </ul>
    </section>

    <section class="task-card">
      <div class="task-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          type="button"
          :class="{ active: activeTab === tab.id }"
          @click="selectTab(tab.id)"
        >
          {{ tab.label }}
          <em>{{ tab.count }}</em>
        </button>
      </div>

      <div class="task-toolbar">
        <label class="search-box">
          <span>⌕</span>
          <input
            v-model="keyword"
            type="search"
            placeholder="搜索单号、工单号、库区或状态"
          />
        </label>
        <span>当前 {{ total }} 条记录</span>
      </div>

      <div v-if="activeTab === 'pendingOrder' || activeTab === 'failedOrder'" class="hub-table">
        <div class="table-row table-head cols-pending">
          <span>领料单号</span><span>生产计划</span><span>工单号</span><span>物料项</span>
          <span>需求时间</span><span>状态</span><span>操作</span>
        </div>
        <div v-for="row in pagedList" :key="row.id" class="table-row cols-pending">
          <span class="main-no">{{ row.id }}</span>
          <span>{{ row.plan || '—' }}</span>
          <span>{{ row.workOrder || '—' }}</span>
          <span>{{ row.itemCount }} 项</span>
          <span>{{ row.demandDate || '—' }}</span>
          <span><i :class="['status-pill', statusClass(row.status)]">{{ row.status }}</i></span>
          <span class="row-actions">
            <button
              type="button"
              class="mini-primary"
              :disabled="agentTaskLoading"
              @click="generateOutbound(row)"
            >
              {{ agentTaskLoading ? 'Agent 执行中...' : activeTab === 'failedOrder' ? '重新生成' : '生成出库单' }}
            </button>
            <button v-if="activeTab === 'failedOrder'" type="button" class="mini-ghost" @click="viewException">
              查看异常
            </button>
          </span>
        </div>
        <div v-if="!total" class="empty-state">当前标签暂无记录</div>
      </div>

      <div v-else-if="activeTab === 'pendingPick'" class="hub-table">
        <div class="table-row table-head cols-pick-pending">
          <span>拣货任务号</span><span>出库单号</span><span>工单号</span><span>物料项</span>
          <span>库区</span><span>状态</span><span>操作</span>
        </div>
        <div v-for="row in pagedList" :key="row.id" class="table-row cols-pick-pending">
          <span class="main-no">{{ row.id }}</span>
          <span>{{ row.outbound || '—' }}</span>
          <span>{{ row.workOrder || '—' }}</span>
          <span>{{ row.itemCount }} 项</span>
          <span>{{ row.zone || '—' }}</span>
          <span><i :class="['status-pill', statusClass(row.status)]">{{ row.status }}</i></span>
          <span class="row-actions">
            <button type="button" class="mini-primary" @click="openScan(row.id)">开始 PDA 拣货</button>
            <button type="button" class="mini-ghost" @click="openDetail(row.id)">详情</button>
          </span>
        </div>
        <div v-if="!total" class="empty-state">暂无待拣货任务</div>
      </div>

      <div v-else-if="activeTab === 'picking'" class="hub-table">
        <div class="table-row table-head cols-pick-active">
          <span>拣货任务号</span><span>出库单号</span><span>执行人</span>
          <span>当前进度</span><span>状态</span><span>操作</span>
        </div>
        <div v-for="row in pagedList" :key="row.id" class="table-row cols-pick-active">
          <span class="main-no">{{ row.id }}</span>
          <span>{{ row.outbound || '—' }}</span>
          <span>{{ row.assigneeName || '沈砚' }}</span>
          <span>{{ row.progress?.done ?? 0 }}/{{ row.progress?.total ?? 0 }}</span>
          <span><i :class="['status-pill', statusClass(row.status)]">{{ row.status }}</i></span>
          <span class="row-actions">
            <button type="button" class="mini-primary" @click="openScan(row.id)">继续扫码</button>
            <button type="button" class="mini-ghost" @click="openDetail(row.id)">详情</button>
          </span>
        </div>
        <div v-if="!total" class="empty-state">暂无进行中的拣货任务</div>
      </div>

      <div v-else-if="activeTab === 'prep' || activeTab === 'review'" class="hub-table">
        <div class="table-row table-head cols-review">
          <span>拣货任务号</span><span>出库单号</span><span>工单号</span>
          <span>{{ activeTab === 'prep' ? '通知工人' : '执行人' }}</span><span>状态</span><span>操作</span>
        </div>
        <div v-for="row in pagedList" :key="row.id" class="table-row cols-review">
          <span class="main-no">{{ row.id }}</span>
          <span>{{ row.outbound || '—' }}</span>
          <span>{{ row.workOrder || '—' }}</span>
          <span>{{ row.assigneeName || '—' }}</span>
          <span><i :class="['status-pill', statusClass(row.status)]">{{ row.status }}</i></span>
          <span class="row-actions">
            <button
              type="button"
              :class="activeTab === 'review' ? 'mini-primary' : 'mini-ghost'"
              @click="openDetail(row.id)"
            >{{ activeTab === 'review' ? '复核' : '查看' }}</button>
          </span>
        </div>
        <div v-if="!total" class="empty-state">当前标签暂无任务</div>
      </div>

      <div v-else-if="activeTab === 'generated'" class="hub-table">
        <div class="table-row table-head cols-generated">
          <span>出库单号</span><span>来源领料单</span><span>工单号</span><span>物料项</span>
          <span>状态</span><span>创建时间</span><span>操作</span>
        </div>
        <div v-for="row in pagedList" :key="row.id" class="table-row cols-generated">
          <span class="main-no">{{ row.id }}</span>
          <span>{{ row.requisition || '—' }}</span>
          <span>{{ row.workOrder || '—' }}</span>
          <span>{{ row.itemCount }} 项</span>
          <span><i :class="['status-pill', statusClass(row.status)]">{{ row.status }}</i></span>
          <span>{{ row.createdAt || '—' }}</span>
          <span class="row-actions">
            <button type="button" class="mini-ghost" @click="viewOutbound(row)">查看</button>
          </span>
        </div>
        <div v-if="!total" class="empty-state">暂无已生成出库单</div>
      </div>

      <div v-else class="hub-table">
        <div class="table-row table-head cols-all">
          <span>记录类型</span><span>业务单号</span><span>关联单号</span>
          <span>物料项</span><span>状态</span><span>时间/执行信息</span><span>操作</span>
        </div>
        <div v-for="row in pagedList" :key="row.recordKey" class="table-row cols-all">
          <span>{{ row.recordType }}</span>
          <span class="main-no">{{ row.businessNo }}</span>
          <span>{{ row.relatedNo }}</span>
          <span>{{ row.itemText }}</span>
          <span><i :class="['status-pill', statusClass(row.status)]">{{ row.status }}</i></span>
          <span>{{ row.timeText }}</span>
          <span class="row-actions">
            <button
              type="button"
              class="mini-ghost"
              :disabled="agentTaskLoading && ['pendingOrder', 'failedOrder'].includes(row.recordKind)"
              @click="openRecord(row)"
            >{{ recordActionText(row) }}</button>
          </span>
        </div>
        <div v-if="!total" class="empty-state">暂无出库记录</div>
      </div>

      <footer v-if="total" class="hub-pager">
        <span>共 {{ total }} 条 · 每页 5 条</span>
        <div>
          <button type="button" :disabled="currentPage <= 1" @click="prevPage">上一页</button>
          <strong>{{ currentPage }} / {{ totalPages }}</strong>
          <button type="button" :disabled="currentPage >= totalPages" @click="nextPage">下一页</button>
        </div>
      </footer>
    </section>
  </section>

  <section v-else class="outbound-hub">
    <p class="hub-message danger">当前角色无权访问出库管理模块</p>
  </section>
</template>

<style scoped>
.outbound-hub {
  width: min(1280px, 100%);
  min-height: 100%;
  margin: 0 auto;
  padding: 6px 4px 40px;
  box-sizing: border-box;
  color: #33473f;
  background: transparent;
  font-family: inherit;
}

.dashboard-top {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  align-items: stretch;
  gap: 22px;
}

.dashboard-main {
  display: flex;
  min-width: 0;
  min-height: 100%;
  flex-direction: column;
}

.hub-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 22px;
  margin-bottom: 20px;
}

.hub-header h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(26px, 3vw, 34px);
  font-weight: 600;
  color: #2a3a33;
  line-height: 1.25;
  letter-spacing: 0;
}

.hub-header p {
  margin: 6px 0 0;
  color: #7d8983;
  font-size: 14px;
  line-height: 1.7;
}

.header-actions,
.row-actions,
.hub-pager > div {
  display: flex;
  align-items: center;
  gap: 9px;
}

.primary-action,
.secondary-action,
.mini-primary,
.mini-ghost,
.hub-pager button {
  border: 1px solid #cddbd3;
  border-radius: 8px;
  background: #ffffff;
  color: #567566;
  font: inherit;
  cursor: pointer;
  transition: 0.18s ease;
}

.primary-action,
.secondary-action {
  min-height: 38px;
  padding: 0 15px;
  font-size: 13px;
  font-weight: 600;
}

.primary-action,
.mini-primary {
  border-color: #587766;
  background: #587766;
  color: #ffffff;
}

.primary-action:hover,
.mini-primary:hover {
  border-color: #4a6a59;
  background: #4a6a59;
}

.secondary-action:hover,
.mini-ghost:hover,
.hub-pager button:hover:not(:disabled) {
  border-color: #88a394;
  background: #eef4f0;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.workflow-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 15px 8px 12px;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.workflow-step {
  position: relative;
  display: flex;
  min-width: 0;
  flex: 1 1 0;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  text-align: center;
}

.workflow-step > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.workflow-step strong {
  color: #26332e;
  font-size: 14px;
  font-weight: 650;
  white-space: nowrap;
}

.workflow-step small {
  display: block;
  max-width: 118px;
  overflow: hidden;
  color: #7f8984;
  font-size: 11px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-step em {
  position: absolute;
  top: -7px;
  left: calc(50% + 12px);
  min-width: 19px;
  height: 19px;
  padding: 0 5px;
  border-radius: 10px;
  background: #eef3f0;
  color: #536d60;
  font-size: 10px;
  font-style: normal;
  font-weight: 700;
  line-height: 19px;
  text-align: center;
}

.workflow-icon {
  display: grid;
  width: 43px;
  height: 43px;
  place-items: center;
  border: 2px solid #aab4af;
  border-radius: 50%;
  background: #ffffff;
  color: #77827d;
  font-size: 14px;
  font-weight: 700;
}

.workflow-step.active .workflow-icon {
  border-color: #c98a2e;
  color: #c98a2e;
  box-shadow: 0 0 0 5px #fdf0e8;
}

.workflow-step.done .workflow-icon {
  border-color: #587766;
  background: #587766;
  color: #ffffff;
  box-shadow: 0 0 0 5px #eef4f0;
}

.workflow-arrow {
  flex: 0 0 auto;
  margin-top: 12px;
  color: #88a394;
  font-size: 22px;
  font-weight: 400;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
  padding-top: 0;
}

.summary-card {
  height: 100%;
  min-height: 105px;
  padding: 15px 16px 13px;
  box-sizing: border-box;
  border: 1px solid #e2eae5;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
}

.summary-card-title {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #5f6c66;
  font-size: 12px;
}

.summary-card-value {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin: 8px 0 5px;
}

.summary-card-value strong {
  color: #3b7a5a;
  font-size: 25px;
  font-weight: 700;
  line-height: 1;
}

.summary-card-value b {
  color: #5f6e67;
  font-size: 11px;
  font-weight: 400;
}

.summary-card > small {
  color: #9aa39e;
  font-size: 10px;
}

.summary-card.blue .summary-card-value strong { color: #437a9e; }
.summary-card.orange .summary-card-value strong { color: #c98a2e; }
.summary-card.deep .summary-card-value strong { color: #33473f; }

.overview-card {
  display: flex;
  min-width: 0;
  height: 100%;
  flex-direction: column;
  padding: 18px;
  box-sizing: border-box;
  border: 1px solid #e2eae5;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
}

.overview-card h2,
.trend-block h3 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  color: #2a3a33;
  font-size: 15px;
  font-weight: 600;
}

.overview-content {
  display: grid;
  grid-template-columns: 114px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 15px 0 14px;
  border-bottom: 1px solid #edf1ef;
}

.overview-donut {
  display: grid;
  width: 106px;
  height: 106px;
  place-items: center;
  border-radius: 50%;
}

.overview-donut::before {
  grid-area: 1 / 1;
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: #ffffff;
  content: '';
}

.overview-donut > div {
  z-index: 1;
  display: flex;
  grid-area: 1 / 1;
  flex-direction: column;
  align-items: center;
}

.overview-donut strong {
  color: #222e29;
  font-size: 23px;
  line-height: 1.1;
}

.overview-donut span {
  margin-top: 4px;
  color: #77817c;
  font-size: 10px;
}

.overview-legend {
  display: grid;
  gap: 9px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.overview-legend li {
  display: grid;
  grid-template-columns: 8px 1fr auto;
  align-items: center;
  gap: 7px;
  color: #68736d;
  font-size: 10px;
}

.overview-legend li i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.overview-legend li strong {
  color: #46514c;
  font-size: 10px;
  font-weight: 500;
  white-space: nowrap;
}

.trend-block {
  margin-top: 14px;
  padding-top: 0;
}

.trend-chart {
  display: grid;
  height: 100px;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  align-items: end;
  gap: 7px;
  margin-top: 10px;
}

.trend-column {
  display: grid;
  height: 100%;
  grid-template-rows: 14px 1fr 18px;
  align-items: end;
  text-align: center;
}

.trend-value {
  color: #8b9690;
  font-size: 9px;
}

.trend-bar-track {
  position: relative;
  display: flex;
  height: 58px;
  align-items: flex-end;
  justify-content: center;
  border-bottom: 1px solid #dfe7e3;
}

.trend-bar-track span {
  width: 17px;
  min-height: 4px;
  border-radius: 3px 3px 0 0;
  background: linear-gradient(180deg, #cfe3d7, #587766);
}

.trend-column small {
  color: #8b9690;
  font-size: 8px;
  white-space: nowrap;
}

.hub-message {
  margin: 14px 0 0;
  padding: 9px 12px;
  border-radius: 8px;
  background: #eef4f0;
  color: #587766;
  font-size: 12px;
}

.hub-message.success { background: #e0f0e6; color: #3b7a5a; }
.hub-message.danger { background: #fdecea; color: #c0392b; }

.coord-notice-card {
  margin-top: 16px;
  padding: 16px 18px;
  border: 1px solid #e3e9f5;
  border-left: 3px solid #4c7ef3;
  border-radius: 10px;
  background: #fbfcff;
}
.coord-notice-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.coord-notice-head h2 {
  margin: 0;
  font-size: 15px;
  color: #24324a;
  display: flex;
  align-items: center;
  gap: 8px;
}
.coord-notice-head small { color: #8a97ab; font-size: 12px; }
.coord-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #e5504a;
  color: #fff;
  font-size: 11px;
  font-style: normal;
}
.coord-notice-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
.coord-notice-list li {
  padding: 10px 12px;
  border: 1px solid #eef1f7;
  border-radius: 8px;
  background: #fff;
}
.coord-notice-list li.done { opacity: 0.6; }
.coord-notice-tags { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.coord-tag {
  font-style: normal;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
}
.coord-tag.type { background: #eef2fe; color: #3d63c9; }
.coord-tag.open { background: #fdecec; color: #c94b42; }
.coord-tag.warn { background: #fff5e6; color: #b9812f; }
.coord-tag.ok { background: #eaf7ef; color: #2f8256; }
.coord-notice-plan { font-size: 12px; color: #8a97ab; }
.coord-notice-list strong { display: block; font-size: 13px; color: #24324a; }
.coord-notice-list p { margin: 4px 0 8px; font-size: 12px; color: #5e6a7d; }
.coord-notice-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.coord-notice-foot small { color: #97a2b4; font-size: 11px; }
.coord-notice-btn {
  border: 1px solid #4c7ef3;
  background: #4c7ef3;
  color: #fff;
  border-radius: 6px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
}
.coord-notice-btn:hover { background: #3a6ae0; }

.task-card {
  overflow: hidden;
  margin-top: 22px;
  border: 1px solid #e2eae5;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
}

.task-tabs {
  display: flex;
  align-items: center;
  gap: 0;
  flex-wrap: wrap;
  padding: 0 14px;
  border-bottom: 1px solid #e8eeeb;
}

.task-tabs button {
  display: flex;
  min-height: 50px;
  align-items: center;
  gap: 6px;
  padding: 0 14px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #66736d;
  font: inherit;
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
}

.task-tabs button.active {
  border-bottom-color: #587766;
  color: #587766;
  font-weight: 700;
}

.task-tabs em {
  min-width: 21px;
  height: 21px;
  padding: 0 5px;
  border-radius: 11px;
  background: #eef4f0;
  font-size: 11px;
  font-style: normal;
  line-height: 21px;
  text-align: center;
}

.task-tabs button.active em {
  background: #e0f0e6;
  color: #3b7a5a;
}

.task-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 13px 16px;
  border-bottom: 1px solid #edf2ef;
  color: #7a8982;
  font-size: 12px;
}

.search-box {
  display: flex;
  width: min(440px, 100%);
  min-height: 38px;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid #dfe6e1;
  border-radius: 8px;
  background: #fff;
}

.search-box:focus-within {
  border-color: #587766;
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: #284238;
  font: inherit;
  font-size: 12px;
}

.hub-table {
  min-width: 900px;
  overflow-x: auto;
}

.table-row {
  display: grid;
  min-height: 58px;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-bottom: 1px solid #ecf1ee;
  color: #3f554b;
  font-size: 12px;
}

.table-row:not(.table-head):hover {
  background: #f6f9f7;
}

.table-head {
  min-height: 46px;
  background: #f0f5f2;
  color: #5f7268;
  font-size: 11px;
  font-weight: 600;
}

.cols-pending,
.cols-pick-pending,
.cols-generated,
.cols-all {
  grid-template-columns: 1.35fr 1.1fr 0.9fr 0.65fr 0.9fr 0.9fr 1.15fr;
}

.cols-pick-active,
.cols-review {
  grid-template-columns: 1.35fr 1.1fr 1fr 0.9fr 0.9fr 1.25fr;
}

.main-no {
  overflow-wrap: anywhere;
  color: #31433c;
  font-weight: 700;
}

.status-pill {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  padding: 0 9px;
  border-radius: 12px;
  background: #fdf0e8;
  color: #c98a2e;
  font-size: 10px;
  font-style: normal;
  white-space: nowrap;
}

.status-pill.ok { background: #e0f0e6; color: #3b7a5a; }
.status-pill.warn { background: #fdf0e8; color: #c98a2e; }
.status-pill.danger { background: #fdecea; color: #c0392b; }

.mini-primary,
.mini-ghost {
  min-height: 29px;
  padding: 0 10px;
  border-radius: 6px;
  font-size: 10px;
  white-space: nowrap;
}

.empty-state {
  padding: 42px 18px;
  color: #89958f;
  font-size: 12px;
  text-align: center;
}

.hub-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  color: #75827c;
  font-size: 12px;
}

.hub-pager button {
  min-height: 32px;
  padding: 0 12px;
}

.hub-pager strong {
  min-width: 54px;
  text-align: center;
}

@media (max-width: 1120px) {
  .dashboard-top {
    grid-template-columns: 1fr;
  }

  .dashboard-main {
    min-height: auto;
  }

  .summary-grid {
    margin-top: 18px;
    padding-top: 0;
  }

  .overview-card {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 22px;
  }

  .overview-card > h2 {
    grid-column: 1 / -1;
  }

  .overview-content {
    border-bottom: 0;
    border-right: 1px solid #edf1ef;
    padding: 0 22px 0 0;
  }

  .trend-block {
    padding-top: 0;
  }
}

@media (max-width: 860px) {
  .outbound-hub {
    padding: 22px 18px 32px;
  }

  .hub-header {
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workflow-card {
    overflow-x: auto;
    justify-content: flex-start;
    padding-bottom: 18px;
  }

  .workflow-step {
    min-width: 132px;
  }

  .workflow-arrow {
    min-width: 20px;
  }
}

@media (max-width: 620px) {
  .header-actions {
    width: 100%;
  }

  .header-actions button {
    flex: 1;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .overview-card {
    display: block;
  }

  .overview-content {
    border-right: 0;
    border-bottom: 1px solid #edf1ef;
    padding: 18px 0;
  }

  .trend-block {
    padding-top: 18px;
  }

  .task-toolbar,
  .hub-pager {
    align-items: stretch;
    flex-direction: column;
  }

  .search-box {
    width: auto;
  }
}
</style>
