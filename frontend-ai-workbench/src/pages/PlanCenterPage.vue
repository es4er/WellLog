<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet } from '../api'
import { usePmc } from '../composables/usePmc'
import { useAgentTask } from '../composables/useAgentTask'
import { menuToPath } from '../router/menuRoutes'

const router = useRouter()
const {
  productionPlans,
  planShortages,
  pmcDataLoading,
  pmcDataError,
  planStats,
  lastSyncAt,
  loadPmcData,
  startPmcPolling,
  stopPmcPolling,
  statusClass,
  approveCustomerOrder,
  syncMessage,
  syncLoading,
  syncError,
  syncOrdersFromErpMes,
  shortageTypeClass,
  pmcKpis
} = usePmc()
const { startPmcAgentTask, agentTaskLoading, agentTaskError } = useAgentTask()

const approveLoading = ref(null)
const localTip = ref('')
const orderModalOpen = ref(false)
const orderModalRow = ref(null)
const orderDetailLoading = ref(false)
const orderDetailData = ref(null)
const selectedPlanId = ref(null)
const selectedOrderId = ref(null)
const shortageExpanded = ref(false)

/** Tabs：对齐参考图，按现有 hasPlan / status 映射 */
const activeTab = ref('pending')
const filterStatus = ref('')
const filterSource = ref('')
const filterKeyword = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const pageSize = ref(10)
const currentPage = ref(1)
const selectedKeys = ref([])

const TAB_DEFS = [
  { key: 'pending', label: '待排产 / 待审核' },
  { key: 'planned', label: '已排产' },
  { key: 'completed', label: '已完成' },
  { key: 'closed', label: '已关闭' }
]

function tabOf(row) {
  if (!row.hasPlan) return 'pending'
  if (row.status === '已完成') return 'completed'
  if (row.status === '已关闭') return 'closed'
  return 'planned'
}

const sourceOptions = computed(() => {
  const set = new Set()
  for (const row of productionPlans.value) {
    if (row.source) set.add(row.source)
  }
  return [...set]
})

const statusOptions = computed(() => {
  const set = new Set()
  for (const row of productionPlans.value) {
    if (row.status) set.add(row.status)
  }
  return [...set]
})

const tabCounts = computed(() => {
  const counts = { pending: 0, planned: 0, completed: 0, closed: 0 }
  for (const row of productionPlans.value) {
    counts[tabOf(row)] += 1
  }
  return counts
})

const filteredPlans = computed(() => {
  const kw = filterKeyword.value.trim().toLowerCase()
  return productionPlans.value.filter((row) => {
    if (tabOf(row) !== activeTab.value) return false
    if (filterStatus.value && row.status !== filterStatus.value) return false
    if (filterSource.value && row.source !== filterSource.value) return false
    if (dateFrom.value && row.start && row.start !== '—' && row.start < dateFrom.value) return false
    if (dateTo.value && row.start && row.start !== '—' && row.start > dateTo.value) return false
    if (kw) {
      const hay = `${row.id || ''} ${row.order || ''} ${row.product || ''} ${row.req || ''}`.toLowerCase()
      if (!hay.includes(kw)) return false
    }
    return true
  })
})

const totalFiltered = computed(() => filteredPlans.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalFiltered.value / pageSize.value)))

const pagedPlans = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredPlans.value.slice(start, start + pageSize.value)
})

const pageNumbers = computed(() => {
  const total = totalPages.value
  const cur = currentPage.value
  if (total <= 5) return Array.from({ length: total }, (_, i) => i + 1)
  if (cur <= 3) return [1, 2, 3, 4, 5]
  if (cur >= total - 2) return [total - 4, total - 3, total - 2, total - 1, total]
  return [cur - 2, cur - 1, cur, cur + 1, cur + 2]
})

watch([activeTab, filterStatus, filterSource, filterKeyword, dateFrom, dateTo, pageSize], () => {
  currentPage.value = 1
})

const hasShortageFilter = computed(
  () => selectedPlanId.value != null || selectedOrderId.value != null
)

const shortagePageSize = ref(5)
const shortageCurrentPage = ref(1)

const filteredShortages = computed(() => {
  const rows = planShortages.value || []
  if (selectedPlanId.value != null) {
    return rows.filter((r) => r.planId === selectedPlanId.value)
  }
  if (selectedOrderId.value != null) {
    return rows.filter((r) => r.orderId === selectedOrderId.value)
  }
  return rows
})

const shortageTotal = computed(() => filteredShortages.value.length)
const shortageTotalPages = computed(() => Math.max(1, Math.ceil(shortageTotal.value / shortagePageSize.value)))

const pagedShortages = computed(() => {
  const start = (shortageCurrentPage.value - 1) * shortagePageSize.value
  return filteredShortages.value.slice(start, start + shortagePageSize.value)
})

const shortagePageNumbers = computed(() => {
  const total = shortageTotalPages.value
  const cur = shortageCurrentPage.value
  if (total <= 5) return Array.from({ length: total }, (_, i) => i + 1)
  if (cur <= 3) return [1, 2, 3, 4, 5]
  if (cur >= total - 2) return [total - 4, total - 3, total - 2, total - 1, total]
  return [cur - 2, cur - 1, cur, cur + 1, cur + 2]
})

watch([selectedPlanId, selectedOrderId, shortagePageSize], () => {
  shortageCurrentPage.value = 1
})

watch(shortageTotalPages, (pages) => {
  if (shortageCurrentPage.value > pages) shortageCurrentPage.value = pages
})

const shortageFilterHint = computed(() => {
  if (selectedPlanId.value != null) {
    const plan = productionPlans.value.find((p) => p.planId === selectedPlanId.value)
    return plan
      ? `正在查看计划 ${plan.id}（订单 ${plan.order}）的缺料 · InventoryAgent 回写优先`
      : '正在按选中计划过滤缺料'
  }
  if (selectedOrderId.value != null) {
    return `正在按订单过滤缺料（订单 ID ${selectedOrderId.value}）`
  }
  return '显示全部计划缺料（点击上方计划行可过滤；优先展示 Agent 齐套回写结果）'
})

/** 底部分析：全部由现有数据推导 */
const overviewSlices = computed(() => {
  const total = Math.max(productionPlans.value.length, 1)
  const items = [
    { key: 'pending', label: '待排产/待审核', color: '#f5a623', count: tabCounts.value.pending },
    { key: 'planned', label: '已排产', color: '#3b82f6', count: tabCounts.value.planned },
    { key: 'completed', label: '已完成', color: '#22c55e', count: tabCounts.value.completed },
    { key: 'closed', label: '已关闭', color: '#94a3b8', count: tabCounts.value.closed }
  ]
  let offset = 0
  return items.map((item) => {
    const pct = (item.count / total) * 100
    const slice = { ...item, pct, offset }
    offset += pct
    return slice
  })
})

const donutStyle = computed(() => {
  const parts = overviewSlices.value
    .filter((s) => s.count > 0)
    .map((s) => `${s.color} ${s.offset}% ${s.offset + s.pct}%`)
  if (!parts.length) return { background: 'conic-gradient(#e5ebe7 0 100%)' }
  return { background: `conic-gradient(${parts.join(', ')})` }
})

const avgReady = computed(() => {
  const withPlan = productionPlans.value.filter((p) => p.hasPlan && typeof p.ready === 'number')
  if (!withPlan.length) return 0
  return Math.round((withPlan.reduce((s, p) => s + (p.ready || 0), 0) / withPlan.length) * 10) / 10
})

const keyMetrics = computed(() => {
  const kpiMap = Object.fromEntries((pmcKpis.value || []).map((row) => [row[0], row[1]]))
  return [
    { label: '平均齐套率', value: kpiMap['平均齐套率'] || `${avgReady.value}%`, tone: 'ok' },
    {
      label: '计划达成率',
      value: kpiMap['按期可执行计划']
        ? String(kpiMap['按期可执行计划']).includes('%')
          ? kpiMap['按期可执行计划']
          : `${kpiMap['按期可执行计划']}`
        : planStats.value.withPlan
          ? `${Math.round((productionPlans.value.filter((p) => p.hasPlan && p.ready >= 90).length / Math.max(planStats.value.withPlan, 1)) * 1000) / 10}%`
          : '—',
      tone: 'ok'
    },
    {
      label: '按期可执行',
      value: kpiMap['按期可执行计划'] || String(productionPlans.value.filter((p) => p.hasPlan && p.ready >= 90).length),
      tone: 'ok'
    },
    { label: '缺料件数', value: String(planStats.value.shortage || filteredShortages.value.length || 0), tone: 'danger' }
  ]
})

const readyBuckets = computed(() => {
  const buckets = [
    { label: '0-50%', min: 0, max: 50, color: '#ef4444', count: 0 },
    { label: '50-80%', min: 50, max: 80, color: '#f59e0b', count: 0 },
    { label: '80-95%', min: 80, max: 95, color: '#84cc16', count: 0 },
    { label: '95-100%', min: 95, max: 101, color: '#22c55e', count: 0 }
  ]
  for (const p of productionPlans.value) {
    if (!p.hasPlan || typeof p.ready !== 'number') continue
    const b = buckets.find((x) => p.ready >= x.min && p.ready < x.max)
    if (b) b.count += 1
  }
  const max = Math.max(...buckets.map((b) => b.count), 1)
  return buckets.map((b) => ({ ...b, height: Math.round((b.count / max) * 100) }))
})

const readyWarnings = computed(() =>
  productionPlans.value
    .filter((p) => p.hasPlan && typeof p.ready === 'number' && p.ready < 80)
    .sort((a, b) => a.ready - b.ready)
    .slice(0, 5)
)

const recentLogs = computed(() => {
  const logs = []
  if (localTip.value) logs.push({ time: lastSyncAt.value || '刚刚', text: localTip.value, user: '周计划' })
  if (syncMessage.value && syncMessage.value !== localTip.value) {
    logs.push({ time: lastSyncAt.value || '刚刚', text: syncMessage.value, user: '系统' })
  }
  if (lastSyncAt.value) logs.push({ time: lastSyncAt.value, text: '刷新订单与计划列表', user: '系统' })
  for (const p of productionPlans.value.filter((x) => x.hasPlan).slice(0, 3)) {
    logs.push({
      time: p.kittingCheckedAt || lastSyncAt.value || '—',
      text: `计划 ${p.id} 齐套率 ${p.ready ?? '—'}% · 领料单 ${p.req || '—'}`,
      user: '周计划'
    })
  }
  return logs.slice(0, 6)
})

const allPageSelected = computed(() => {
  if (!pagedPlans.value.length) return false
  return pagedPlans.value.every((p) => selectedKeys.value.includes(rowKey(p)))
})

onMounted(() => {
  loadPmcData()
  startPmcPolling(8000)
})

onUnmounted(() => {
  stopPmcPolling()
})

function rowKey(row) {
  return row.hasPlan ? `plan-${row.planId || row.id}` : `order-${row.orderId || row.order}`
}

function toggleSelectAll() {
  if (allPageSelected.value) {
    const pageKeys = new Set(pagedPlans.value.map(rowKey))
    selectedKeys.value = selectedKeys.value.filter((k) => !pageKeys.has(k))
  } else {
    const merged = new Set(selectedKeys.value)
    pagedPlans.value.forEach((p) => merged.add(rowKey(p)))
    selectedKeys.value = [...merged]
  }
}

function toggleSelect(row) {
  const key = rowKey(row)
  if (selectedKeys.value.includes(key)) {
    selectedKeys.value = selectedKeys.value.filter((k) => k !== key)
  } else {
    selectedKeys.value = [...selectedKeys.value, key]
  }
}

function readyBarClass(ready) {
  if (ready == null || ready === '—') return 'muted'
  if (ready >= 90) return 'ok'
  if (ready >= 70) return 'mid'
  return 'low'
}

function statusTone(status) {
  if (status === '待审核') return 'info'
  if (status === '待排产' || status === '待领料') return 'warn'
  if (status === '缺料') return 'danger'
  if (status === '可生产' || status === '已完成' || status === '已排产') return 'ok'
  return statusClass(status) || 'warn'
}

function periodText(plan) {
  if (!plan.start || plan.start === '—') return '—'
  if (!plan.finish || plan.finish === '—') return plan.start
  return `${plan.start} ~ ${plan.finish}`
}

const orderHead = computed(() => orderDetailData.value?.order ?? null)
const orderLines = computed(() => orderDetailData.value?.lines ?? [])

function orderStatusLabel(status) {
  const map = {
    PENDING_REVIEW: '待审核',
    APPROVED: '已审核',
    CANCELLED: '已取消'
  }
  return map[status] || status || '—'
}

function sourceSystemLabel(systemId) {
  if (systemId === 2) return 'MES'
  if (systemId === 1) return 'ERP'
  return '客户订单'
}

function formatQty(qty) {
  if (qty == null || qty === '') return '—'
  const num = Number(qty)
  return Number.isFinite(num) ? `${num} 件` : String(qty)
}

function lineItemName(line) {
  if (!line) return '—'
  return line.itemName || line.itemCode || (line.itemId != null ? `物料 #${line.itemId}` : '—')
}

function parseErpOrderNo(erpOrderNo) {
  const erp = String(erpOrderNo || '')
  const idx = erp.indexOf('|')
  return idx > 0 ? erp.slice(0, idx) : erp || '—'
}

function parseErpProductName(erpOrderNo, fallback = '—') {
  const erp = String(erpOrderNo || '')
  const parts = erp.split('|')
  if (parts.length >= 2 && parts[1]?.trim()) return parts[1].trim()
  return fallback
}

async function openOrderAuditModal(row) {
  if (!row?.orderId) {
    localTip.value = '缺少订单 ID，无法加载详情'
    return
  }
  orderModalRow.value = row
  orderModalOpen.value = true
  orderDetailLoading.value = true
  orderDetailData.value = null
  localTip.value = ''
  try {
    orderDetailData.value = await apiGet('/order/detail', { orderId: row.orderId })
  } catch (error) {
    localTip.value = error.message || '加载订单详情失败'
    closeOrderModal()
  } finally {
    orderDetailLoading.value = false
  }
}

function closeOrderModal() {
  orderModalOpen.value = false
  orderModalRow.value = null
  orderDetailData.value = null
}

async function confirmApproveOrder() {
  const row = orderModalRow.value
  if (!row?.orderId) return
  approveLoading.value = row.orderId
  localTip.value = ''
  try {
    await approveCustomerOrder(row.orderId)
    closeOrderModal()
    localTip.value = `订单 ${row.order} 已审核，可生成生产计划`
  } catch (error) {
    localTip.value = error.message || '审核失败'
  } finally {
    approveLoading.value = null
  }
}

async function createProductionPlan(row) {
  if (row.status === '待审核') {
    localTip.value = '请先审核订单，再生成生产计划'
    return
  }
  selectedOrderId.value = row.orderId ?? null
  selectedPlanId.value = null
  localTip.value = `正在为订单 ${row.order} 启动多智能体排产（BOM 展开 → 齐套 → 领料）…`
  await startPmcAgentTask({
    orderId: row.orderId,
    planNo: row.order,
    taskName: `订单转生产计划-${row.order}`,
    promptText: `为已审核订单 ${row.order} 制定生产计划，按质检合格库存做齐套校验后生成领料单，交仓管员出库。`,
    autoApproveOrder: false
  })
  if (agentTaskError.value) {
    const msg = agentTaskError.value
    localTip.value = msg.includes('BOM') || msg.includes('未配置') ? msg : `排产失败：${msg}`
  }
}

async function syncExternalOrders() {
  localTip.value = ''
  try {
    const result = await syncOrdersFromErpMes()
    activeTab.value = 'pending'
    resetFilters()
    const syncedNo = result?.orderNos?.[0]
    if (syncedNo) {
      const syncedRow = productionPlans.value.find((row) => row.order === syncedNo)
      if (syncedRow) {
        selectPlan(syncedRow)
        localTip.value = `已同步订单 ${syncedNo}（${syncedRow.product || '测井设备'}），请在下方列表审核。`
      } else {
        filterKeyword.value = syncedNo
        localTip.value = result?.message || `已同步订单 ${syncedNo}，请在「待排产 / 待审核」中查看。`
      }
    } else {
      localTip.value = result?.message || syncMessage.value || 'ERP/MES 订单已同步（本次无新增，可能已存在同号订单）'
    }
  } catch (error) {
    localTip.value = error.message || syncError.value || 'ERP/MES 同步失败'
  }
}

function selectPlan(row) {
  if (!row?.hasPlan) {
    selectedOrderId.value = row?.orderId ?? null
    selectedPlanId.value = null
    shortageExpanded.value = false
    return
  }
  selectedPlanId.value = row.planId ?? null
  selectedOrderId.value = row.orderId ?? null
  shortageExpanded.value = true
}

function isPlanSelected(plan) {
  if (selectedPlanId.value != null && plan.planId === selectedPlanId.value) return true
  if (selectedPlanId.value == null && selectedOrderId.value != null && plan.orderId === selectedOrderId.value) {
    return true
  }
  return false
}

function resetFilters() {
  filterStatus.value = ''
  filterSource.value = ''
  filterKeyword.value = ''
  dateFrom.value = ''
  dateTo.value = ''
  currentPage.value = 1
}

function focusCreatePlan() {
  activeTab.value = 'pending'
  const candidate = productionPlans.value.find((p) => !p.hasPlan && p.status !== '待审核')
  if (candidate) {
    createProductionPlan(candidate)
    return
  }
  const pendingAudit = productionPlans.value.find((p) => !p.hasPlan && p.status === '待审核')
  if (pendingAudit) {
    localTip.value = '请先审核订单，再生成生产计划'
    selectPlan(pendingAudit)
    return
  }
  localTip.value = '当前没有可新建生产计划的待排产订单'
}

function openShortagePanel() {
  shortageExpanded.value = true
  selectedPlanId.value = null
  selectedOrderId.value = null
}

function goOutbound() {
  router.push(menuToPath('出库协同'))
}

function goAnalytics() {
  router.push(menuToPath('数据分析'))
}

function handleWarning(plan) {
  activeTab.value = 'planned'
  selectPlan(plan)
}

function toggleShortagePanel() {
  shortageExpanded.value = !shortageExpanded.value
}

function goPage(page) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
}

function goShortagePage(page) {
  if (page < 1 || page > shortageTotalPages.value) return
  shortageCurrentPage.value = page
}
</script>

<template>
  <section class="plan-center">
    <p v-if="pmcDataLoading && !productionPlans.length" class="data-hint">正在从数据库加载订单与计划...</p>
    <p v-else-if="pmcDataError" class="data-hint danger">{{ pmcDataError }}</p>
    <p v-if="agentTaskError" class="data-hint danger">{{ agentTaskError }}</p>
    <p v-if="syncError" class="data-hint danger">{{ syncError }}</p>
    <p v-if="localTip || syncMessage" class="queue-tip">{{ localTip || syncMessage }}</p>

    <header class="pc-header">
      <div class="pc-header-copy">
        <h1>订单计划中心</h1>
        <small v-if="lastSyncAt" class="sync-hint">列表刷新 {{ lastSyncAt }}</small>
      </div>
      <div class="pc-stats">
        <div class="pc-stat tone-total">
          <span class="pc-stat-icon" aria-hidden="true">☰</span>
          <div>
            <strong>{{ planStats.total }}</strong>
            <small>全部条目</small>
          </div>
        </div>
        <div class="pc-stat tone-warn">
          <span class="pc-stat-icon" aria-hidden="true">◷</span>
          <div>
            <strong>{{ planStats.pendingOrders }}</strong>
            <small>待排产订单</small>
          </div>
        </div>
        <div class="pc-stat tone-info">
          <span class="pc-stat-icon" aria-hidden="true">▣</span>
          <div>
            <strong>{{ planStats.withPlan }}</strong>
            <small>已有计划</small>
          </div>
        </div>
        <div class="pc-stat tone-danger">
          <span class="pc-stat-icon" aria-hidden="true">!</span>
          <div>
            <strong>{{ planStats.shortage }}</strong>
            <small>缺料</small>
          </div>
        </div>
      </div>
    </header>

    <div class="pc-panel">
      <div class="pc-tabs">
        <button
          v-for="tab in TAB_DEFS"
          :key="tab.key"
          type="button"
          class="pc-tab"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
          <em v-if="tabCounts[tab.key]">{{ tabCounts[tab.key] }}</em>
        </button>
      </div>

      <div class="pc-filters">
        <label class="pc-field">
          <span>计划状态</span>
          <select v-model="filterStatus">
            <option value="">全部状态</option>
            <option v-for="s in statusOptions" :key="s" :value="s">{{ s }}</option>
          </select>
        </label>
        <label class="pc-field">
          <span>订单来源</span>
          <select v-model="filterSource">
            <option value="">全部来源</option>
            <option v-for="s in sourceOptions" :key="s" :value="s">{{ s }}</option>
          </select>
        </label>
        <label class="pc-field pc-field-date">
          <span>计划周期</span>
          <div class="pc-date-range">
            <input v-model="dateFrom" type="date" />
            <em>→</em>
            <input v-model="dateTo" type="date" />
          </div>
        </label>
        <label class="pc-field pc-field-search">
          <span>搜索</span>
          <div class="pc-search">
            <input v-model="filterKeyword" type="search" placeholder="搜索订单号/计划号/产品" />
            <i aria-hidden="true">⌕</i>
          </div>
        </label>
        <button type="button" class="pc-btn-text" @click="resetFilters">重置</button>
        <button
          type="button"
          class="pc-btn-text"
          :disabled="syncLoading"
          @click="syncExternalOrders"
        >
          {{ syncLoading ? '同步中...' : '同步 ERP/MES' }}
        </button>
        <button type="button" class="pc-btn-primary" :disabled="agentTaskLoading" @click="focusCreatePlan">
          {{ agentTaskLoading ? 'Agent 执行中...' : '新建生产计划' }}
        </button>
      </div>

      <div class="pc-table-wrap">
        <table class="pc-table">
          <thead>
            <tr>
              <th class="col-check">
                <input type="checkbox" :checked="allPageSelected" @change="toggleSelectAll" />
              </th>
              <th>生产计划单号</th>
              <th>来源订单</th>
              <th>产品</th>
              <th>计划数量</th>
              <th>计划周期</th>
              <th>齐套率</th>
              <th>状态</th>
              <th class="col-ops">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="plan in pagedPlans"
              :key="rowKey(plan)"
              :class="{ active: isPlanSelected(plan) }"
              @click="selectPlan(plan)"
            >
              <td class="col-check" @click.stop>
                <input
                  type="checkbox"
                  :checked="selectedKeys.includes(rowKey(plan))"
                  @change="toggleSelect(plan)"
                />
              </td>
              <td>
                <button type="button" class="pc-plan-link" @click.stop="selectPlan(plan)">
                  {{ plan.id }}
                </button>
                <small v-if="plan.req && plan.req !== '—'" class="pc-sub">领料单 {{ plan.req }}</small>
                <small v-if="plan.kittingCheckedAt" class="pc-sub">齐套校验 {{ plan.kittingCheckedAt }}</small>
              </td>
              <td>
                <strong class="pc-order">{{ plan.order }}</strong>
                <span class="pc-source-tag">{{ plan.source || '客户订单' }}</span>
              </td>
              <td>{{ plan.product }}</td>
              <td>{{ plan.qty }}</td>
              <td class="pc-period">{{ periodText(plan) }}</td>
              <td>
                <div v-if="plan.hasPlan" class="pc-ready">
                  <em>{{ plan.ready }}%</em>
                  <i class="pc-ready-track" :class="readyBarClass(plan.ready)">
                    <b :style="{ width: `${Math.min(plan.ready || 0, 100)}%` }"></b>
                  </i>
                </div>
                <span v-else class="pc-ready-empty">—</span>
              </td>
              <td>
                <i :class="['pc-status', statusTone(plan.status)]">{{ plan.status }}</i>
              </td>
              <td class="col-ops" @click.stop>
                <div class="pc-ops">
                  <button
                    v-if="plan.status === '待审核'"
                    type="button"
                    class="pc-op"
                    @click="openOrderAuditModal(plan)"
                  >详情</button>
                  <button v-else type="button" class="pc-op" @click="selectPlan(plan)">详情</button>
                  <button
                    v-if="plan.status === '待审核'"
                    type="button"
                    class="pc-op accent"
                    :disabled="approveLoading != null || agentTaskLoading"
                    @click="openOrderAuditModal(plan)"
                  >{{ approveLoading === plan.orderId ? '审核中...' : '审核' }}</button>
                  <button
                    v-else-if="!plan.hasPlan"
                    type="button"
                    class="pc-op accent"
                    :disabled="agentTaskLoading"
                    @click="createProductionPlan(plan)"
                  >{{ agentTaskLoading ? '执行中...' : '排产' }}</button>
                  <button
                    v-else
                    type="button"
                    class="pc-op"
                    @click="selectPlan(plan)"
                  >查看缺料</button>
                </div>
              </td>
            </tr>
            <tr v-if="!pagedPlans.length">
              <td colspan="9" class="pc-empty">当前筛选条件下暂无数据</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pc-pagination">
        <span class="pc-total">共 {{ totalFiltered }} 条</span>
        <div class="pc-pages">
          <button type="button" :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">‹</button>
          <button
            v-for="n in pageNumbers"
            :key="n"
            type="button"
            :class="{ active: n === currentPage }"
            @click="goPage(n)"
          >{{ n }}</button>
          <button type="button" :disabled="currentPage >= totalPages" @click="goPage(currentPage + 1)">›</button>
          <select v-model.number="pageSize" class="pc-page-size">
            <option :value="10">10 条/页</option>
            <option :value="20">20 条/页</option>
            <option :value="50">50 条/页</option>
          </select>
        </div>
      </div>
    </div>

    <section class="shortage-panel" :class="{ collapsed: !shortageExpanded }">
      <button
        type="button"
        class="shortage-panel-toggle"
        :aria-expanded="shortageExpanded"
        @click="toggleShortagePanel"
      >
        <span class="shortage-panel-toggle-main">
          <i class="plan-fold-icon" :class="{ open: shortageExpanded }">›</i>
          <strong>缺料分析</strong>
          <em>{{ filteredShortages.length }} 条</em>
        </span>
        <span class="shortage-panel-toggle-hint">
          {{
            shortageExpanded
              ? '点击收起'
              : hasShortageFilter
                ? '已选中计划，点击展开'
                : '点击上方计划行查看，或点此展开全部'
          }}
        </span>
      </button>
      <template v-if="shortageExpanded">
        <div class="shortage-legend">
          <span class="shortage-type-chip type-real">真实缺料</span>
          <span class="shortage-type-chip type-pending">质量未放行</span>
          <span class="shortage-type-chip type-abnormal">质量异常</span>
          <span class="shortage-type-chip type-location">库位/批次不可用</span>
        </div>
        <p class="data-hint">{{ shortageFilterHint }}</p>
        <div class="pc-shortage-table">
          <div class="pc-shortage-head">
            <span>订单号</span>
            <span>计划号</span>
            <span>物料</span>
            <span>分型</span>
            <span>需 / 缺</span>
            <span>建议</span>
          </div>
          <div
            v-for="row in pagedShortages"
            :key="`${row.planId}-${row.itemId}-${row.shortageType}`"
            class="pc-shortage-row"
          >
            <span>{{ row.orderNo }}</span>
            <span>{{ row.planNo }}</span>
            <span>
              <strong>{{ row.itemName }}</strong>
              <small>{{ row.source === 'SNAPSHOT' ? 'Agent 回写' : '实时重算' }}</small>
            </span>
            <span>
              <i :class="['shortage-type-chip', shortageTypeClass(row.shortageType)]">
                {{ row.shortageTypeLabel }}
              </i>
            </span>
            <span class="shortage-gap">需 {{ row.requiredQty || '—' }} / 缺 {{ row.shortageQty || '—' }}</span>
            <span>{{ row.advice }}</span>
          </div>
          <div v-if="!pagedShortages.length" class="pc-empty soft">
            {{ hasShortageFilter ? '当前选中订单/计划无缺料项' : '当前无缺料项' }}
          </div>
        </div>
        <div v-if="shortageTotal" class="pc-pagination pc-shortage-pagination">
          <span class="pc-total">共 {{ shortageTotal }} 条</span>
          <div class="pc-pages">
            <button type="button" :disabled="shortageCurrentPage <= 1" @click="goShortagePage(shortageCurrentPage - 1)">‹</button>
            <button
              v-for="n in shortagePageNumbers"
              :key="`s-${n}`"
              type="button"
              :class="{ active: n === shortageCurrentPage }"
              @click="goShortagePage(n)"
            >{{ n }}</button>
            <button type="button" :disabled="shortageCurrentPage >= shortageTotalPages" @click="goShortagePage(shortageCurrentPage + 1)">›</button>
            <select v-model.number="shortagePageSize" class="pc-page-size">
              <option :value="5">5 条/页</option>
              <option :value="10">10 条/页</option>
              <option :value="20">20 条/页</option>
              <option :value="50">50 条/页</option>
            </select>
          </div>
        </div>
      </template>
    </section>

    <div class="pc-bottom">
      <section class="pc-card">
        <h3>计划概览</h3>
        <div class="pc-overview">
          <div class="pc-donut" :style="donutStyle">
            <div class="pc-donut-hole">
              <strong>{{ planStats.total }}</strong>
              <small>全部</small>
            </div>
          </div>
          <ul class="pc-legend">
            <li v-for="s in overviewSlices" :key="s.key">
              <i :style="{ background: s.color }"></i>
              <span>{{ s.label }}</span>
              <em>{{ s.count }} · {{ Math.round(s.pct) }}%</em>
            </li>
          </ul>
        </div>
        <h3 class="pc-subhead">关键指标</h3>
        <div class="pc-metrics">
          <div v-for="m in keyMetrics" :key="m.label" class="pc-metric" :class="m.tone">
            <strong>{{ m.value }}</strong>
            <small>{{ m.label }}</small>
          </div>
        </div>
      </section>

      <section class="pc-card">
        <h3>齐套率分布</h3>
        <div class="pc-bars">
          <div v-for="b in readyBuckets" :key="b.label" class="pc-bar-col">
            <div class="pc-bar-track">
              <b :style="{ height: `${b.height}%`, background: b.color }"></b>
            </div>
            <strong>{{ b.count }}</strong>
            <small>{{ b.label }}</small>
          </div>
        </div>
        <h3 class="pc-subhead">齐套率预警</h3>
        <ul class="pc-warn-list">
          <li v-for="w in readyWarnings" :key="w.id">
            <i></i>
            <div>
              <strong>{{ w.id }}</strong>
              <span>{{ w.ready }}% · 偏低</span>
            </div>
            <button type="button" class="pc-op accent" @click="handleWarning(w)">去处理</button>
          </li>
          <li v-if="!readyWarnings.length" class="pc-empty soft">暂无齐套率偏低计划</li>
        </ul>
      </section>

      <section class="pc-card">
        <h3>快捷操作</h3>
        <div class="pc-quick">
          <button type="button" class="pc-quick-item tone-a" @click="focusCreatePlan">
            <span>＋</span>
            <strong>新建生产计划</strong>
          </button>
          <button type="button" class="pc-quick-item tone-b" @click="openShortagePanel">
            <span>◎</span>
            <strong>缺料分析</strong>
          </button>
          <button type="button" class="pc-quick-item tone-c" @click="goOutbound">
            <span>⇢</span>
            <strong>仓管出库</strong>
          </button>
          <button type="button" class="pc-quick-item tone-d" @click="goAnalytics">
            <span>▦</span>
            <strong>数据分析</strong>
          </button>
          <button type="button" class="pc-quick-item tone-e" @click="activeTab = 'planned'; shortageExpanded = true">
            <span>✓</span>
            <strong>齐套校验结果</strong>
          </button>
          <button type="button" class="pc-quick-item tone-f" @click="loadPmcData()">
            <span>↻</span>
            <strong>刷新列表</strong>
          </button>
        </div>
        <h3 class="pc-subhead">最近操作记录</h3>
        <ul class="pc-logs">
          <li v-for="(log, idx) in recentLogs" :key="idx">
            <time>{{ log.time }}</time>
            <p>{{ log.text }}</p>
            <em>{{ log.user }}</em>
          </li>
          <li v-if="!recentLogs.length" class="pc-empty soft">暂无操作记录</li>
        </ul>
      </section>
    </div>

    <Teleport to="body">
      <div
        v-if="orderModalOpen && orderModalRow"
        class="pc-order-modal-overlay"
        @click.self="closeOrderModal"
      >
        <div class="pc-order-modal" role="dialog" aria-modal="true" aria-labelledby="pc-order-modal-title">
          <header class="pc-order-modal-head">
            <div>
              <h2 id="pc-order-modal-title">订单详情</h2>
              <p>{{ orderModalRow.order }} · {{ orderModalRow.product || '—' }}</p>
            </div>
            <button type="button" class="pc-order-modal-close" aria-label="关闭" @click="closeOrderModal">×</button>
          </header>

          <div class="pc-order-modal-body">
            <p v-if="orderDetailLoading" class="data-hint">正在加载订单详情...</p>
            <template v-else-if="orderHead">
              <dl class="pc-order-info">
                <div><dt>订单号</dt><dd>{{ orderHead.orderNo || orderModalRow.order }}</dd></div>
                <div><dt>ERP 单号</dt><dd>{{ parseErpOrderNo(orderHead.erpOrderNo) }}</dd></div>
                <div><dt>来源系统</dt><dd>{{ sourceSystemLabel(orderHead.sourceSystemId) }}</dd></div>
                <div><dt>订单日期</dt><dd>{{ orderHead.orderDate || '—' }}</dd></div>
                <div><dt>交货日期</dt><dd>{{ orderHead.deliveryDate || orderModalRow.finish || '—' }}</dd></div>
                <div><dt>订单状态</dt><dd>{{ orderStatusLabel(orderHead.orderStatus) }}</dd></div>
                <div><dt>产品</dt><dd>{{ orderModalRow.product || parseErpProductName(orderHead.erpOrderNo) }}</dd></div>
                <div><dt>计划数量</dt><dd>{{ orderModalRow.qty || '—' }}</dd></div>
              </dl>

              <section v-if="orderLines.length" class="pc-order-lines">
                <h3>订单明细</h3>
                <table class="pc-order-lines-table">
                  <thead>
                    <tr>
                      <th>行号</th>
                      <th>物料名称</th>
                      <th>订购数量</th>
                      <th>需求日期</th>
                      <th>行状态</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(line, index) in orderLines" :key="line.orderLineId || index">
                      <td>{{ index + 1 }}</td>
                      <td>
                        {{ lineItemName(line) }}
                        <small v-if="line.itemCode" class="pc-sub">{{ line.itemCode }}</small>
                      </td>
                      <td>{{ formatQty(line.orderedQty) }}</td>
                      <td>{{ line.requiredDate || '—' }}</td>
                      <td>{{ line.lineStatus || '—' }}</td>
                    </tr>
                  </tbody>
                </table>
              </section>
            </template>
          </div>

          <footer v-if="orderModalRow.status === '待审核'" class="pc-order-modal-foot">
            <button type="button" class="pc-modal-btn" @click="closeOrderModal">取消</button>
            <button
              type="button"
              class="pc-modal-btn primary"
              :disabled="orderDetailLoading || approveLoading != null"
              @click="confirmApproveOrder"
            >{{ approveLoading === orderModalRow.orderId ? '审核中...' : '确认审核' }}</button>
          </footer>
        </div>
      </div>
    </Teleport>
  </section>
</template>
