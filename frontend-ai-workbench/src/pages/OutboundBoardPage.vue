<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../composables/useSession'
import { useAnyRoleAccess, useDemoRole } from '../composables/useRoleAccess'
import { usePmc } from '../composables/usePmc'
import { useAgentTask } from '../composables/useAgentTask'
import { useCoordination } from '../composables/useCoordination'

const router = useRouter()
const { prompt, userId } = useSession()
const { sendCoordinationNotice, loadMyNotices, myNotices, coordinationError } = useCoordination()
const { demoRoleId } = useDemoRole()
const { allowed } = useAnyRoleAccess(['pmc', 'warehouse'])
const isPmc = computed(() => demoRoleId.value === 'pmc')
const canRunOutbound = computed(() => demoRoleId.value === 'warehouse')
const {
  outboundStats,
  outboundList,
  outboundExceptions,
  selectedOutbound,
  pmcDataLoading,
  pmcDataError,
  currentOutbound,
  loadPmcData,
  outboundStatusClass,
  impactClass
} = usePmc()
const {
  agentTaskId,
  agentTaskNo,
  agentTaskStatus,
  agentSteps,
  agentLogs,
  agentTaskLoading,
  agentTaskError,
  startOutboundAgentTask: runOutboundAgentTask,
  startPmcAgentTask,
  openAgentTrace,
  resolvePlanByNo
} = useAgentTask()

const showGlobalAlerts = ref(false)
const coordFeedback = ref('')

/** UI：Tabs / 筛选 / 分页（仅前端过滤，不改后端数据） */
const activeTab = ref('all')
const filterStatus = ref('')
const filterImpact = ref('')
const filterKeyword = ref('')
const pageSize = ref(5)
const currentPage = ref(1)

const TAB_DEFS = computed(() => [
  { key: 'all', label: '全部计划' },
  { key: 'coord', label: '待协调' },
  { key: 'pending', label: '待出库' },
  { key: 'shortage', label: '缺料阻塞' },
  { key: 'review', label: '复核待跟进' },
  { key: 'done', label: '已完成' }
])

const timelineSteps = computed(() => currentOutbound.value?.timeline ?? [])

function matchesPlan(ex, row) {
  if (!row) return false
  if (ex.planId != null && row.planId != null && Number(ex.planId) === Number(row.planId)) return true
  if (ex.planNo && row.plan && ex.planNo === row.plan) return true
  if (ex.requisitionId != null && row.requisitionId != null && Number(ex.requisitionId) === Number(row.requisitionId)) {
    return true
  }
  return false
}

const planBoundExceptions = computed(() =>
  outboundExceptions.value.filter((ex) => ex.planId != null || ex.planNo || ex.requisitionId)
)

const globalAlerts = computed(() =>
  outboundExceptions.value.filter((ex) => ex.planId == null && !ex.planNo && !ex.requisitionId)
)

const currentExceptions = computed(() => {
  const row = currentOutbound.value
  if (!row) return []
  return planBoundExceptions.value.filter((ex) => matchesPlan(ex, row))
})

function exceptionCountFor(row) {
  if (!row) return 0
  return planBoundExceptions.value.filter((ex) => matchesPlan(ex, row)).length
}

function tabOf(row) {
  if (row.status === '已出库' || row.status === '已完成') return 'done'
  if (row.status === '缺料暂停' || row.impact === '影响开工') return 'shortage'
  if (row.status === '待复核') return 'review'
  if (row.status === '待出库') return 'pending'
  if (exceptionCountFor(row) > 0) return 'coord'
  return 'pending'
}

const tabCounts = computed(() => {
  const counts = { all: outboundList.value.length, coord: 0, pending: 0, shortage: 0, review: 0, done: 0 }
  for (const row of outboundList.value) {
    if (exceptionCountFor(row) > 0) counts.coord += 1
    const t = tabOf(row)
    if (t !== 'all') counts[t] += 1
  }
  return counts
})

const statusOptions = computed(() => {
  const set = new Set()
  for (const row of outboundList.value) {
    if (row.status) set.add(row.status)
  }
  return [...set]
})

const impactOptions = computed(() => {
  const set = new Set()
  for (const row of outboundList.value) {
    if (row.impact) set.add(row.impact)
  }
  return [...set]
})

const filteredRows = computed(() => {
  const kw = filterKeyword.value.trim().toLowerCase()
  return outboundList.value.filter((row) => {
    if (activeTab.value === 'coord') {
      if (exceptionCountFor(row) <= 0) return false
    } else if (activeTab.value !== 'all' && tabOf(row) !== activeTab.value) {
      return false
    }
    if (filterStatus.value && row.status !== filterStatus.value) return false
    if (filterImpact.value && row.impact !== filterImpact.value) return false
    if (kw) {
      const hay = `${row.plan || ''} ${row.req || ''} ${row.out || ''} ${row.product || ''}`.toLowerCase()
      if (!hay.includes(kw)) return false
    }
    return true
  })
})

const totalFiltered = computed(() => filteredRows.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalFiltered.value / pageSize.value)))

const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRows.value.slice(start, start + pageSize.value).map((row) => {
    const absoluteIndex = outboundList.value.indexOf(row)
    return { row, absoluteIndex }
  })
})

const pageNumbers = computed(() => {
  const total = totalPages.value
  const cur = currentPage.value
  if (total <= 5) return Array.from({ length: total }, (_, i) => i + 1)
  if (cur <= 3) return [1, 2, 3, 4, 5]
  if (cur >= total - 2) return [total - 4, total - 3, total - 2, total - 1, total]
  return [cur - 2, cur - 1, cur, cur + 1, cur + 2]
})

watch([activeTab, filterStatus, filterImpact, filterKeyword, pageSize], () => {
  currentPage.value = 1
})

const warehouseSummary = computed(() =>
  (outboundStats.value || []).map((stat, idx) => {
    const tones = ['ok', 'info', 'warn', 'deep', 'danger', 'peach']
    return { label: stat[0], value: String(stat[1]), tone: tones[idx % tones.length] }
  })
)

const boardTitle = computed(() =>
  isPmc.value ? '生产领料出库协同看板' : '生产领料出库进度看板'
)

const boardSubtitle = computed(() =>
  isPmc.value
    ? ''
    : '仓管执行出库 · 生产计划 → 领料单 → 出库单 → 拣货 → 复核 → 出库完成'
)

const currentStepMeta = computed(() => {
  const steps = timelineSteps.value
  const current = steps.find((s) => s.state === 'current')
  const doneCount = steps.filter((s) => s.state === 'done').length
  return {
    current,
    handler: current?.handler || '—',
    phase: current?.name || '—',
    time: current?.time || '—',
    progress: steps.length ? `${doneCount}/${steps.length}` : '—'
  }
})

const primaryException = computed(() => currentExceptions.value[0] || null)

const recentLogs = computed(() => {
  const logs = []
  if (coordFeedback.value) {
    logs.push({ time: '刚刚', text: coordFeedback.value, user: isPmc.value ? '周计划' : '沈砚' })
  }
  for (const step of [...timelineSteps.value].reverse().slice(0, 4)) {
    if (step.state === 'pending') continue
    logs.push({
      time: step.time || '—',
      text: `${step.name} · ${step.state === 'done' ? '已完成' : '进行中'}`,
      user: step.handler || step.agent || '—'
    })
  }
  for (const log of agentLogs.value.slice(-3).reverse()) {
    logs.push({
      time: 'Agent',
      text: log.content,
      user: log.agentLabel || log.agentName || 'Agent'
    })
  }
  return logs.slice(0, 4)
})

onMounted(() => {
  if (!allowed.value) {
    router.replace('/module/' + encodeURIComponent('出库协同'))
    return
  }
  loadPmcData()
  if (isPmc.value) {
    loadMyNotices(userId.value)
  }
})

function selectPlan(index) {
  selectedOutbound.value = index
  coordFeedback.value = ''
}

function jumpToPlanWithException(ex) {
  const idx = outboundList.value.findIndex((row) => matchesPlan(ex, row))
  if (idx >= 0) {
    selectedOutbound.value = idx
    showGlobalAlerts.value = false
  }
}

function triggerOutboundAgentTask(override = {}) {
  const outbound = currentOutbound.value
  const planNo = override.planNo || outbound?.plan
  const planId = override.planId || outbound?.planId
  const requisitionId = override.requisitionId || outbound?.requisitionId
  if (!outbound && !requisitionId) return

  const plan = planNo ? resolvePlanByNo(planNo) : null
  const resolvedReqId = requisitionId ?? plan?.requisitionId
  if (!resolvedReqId) {
    agentTaskError.value = '未找到领料单，请确认 PMC 已生成领料单后再执行出库'
    return
  }
  runOutboundAgentTask({
    requisitionId: resolvedReqId,
    planId: planId ?? plan?.planId,
    planNo: planNo ?? plan?.id,
    taskName: `仓管出库执行-${planNo || resolvedReqId}`,
    promptText:
      override.promptText ||
      `执行 ${planNo || resolvedReqId} 领料出库：生成出库单、拣货复核并确认出库。`
  })
}

async function launchUrgeWarehouse(ex) {
  const row = currentOutbound.value
  const planNo = ex?.planNo || row?.plan
  const title = ex?.title || row?.status || '出库协同'
  const content = `请优先处理计划 ${planNo || '当前计划'}${ex?.impact ? `（${ex.impact}）` : ''}，尽快执行出库。`
  prompt.value = `针对「${title}」生成给仓管员的出库协同通知与处理清单，优先处理计划 ${planNo || ''}。`
  coordFeedback.value = '正在发送催办通知给仓管…'
  try {
    const notice = await sendCoordinationNotice({
      noticeType: 'URGE_OUTBOUND',
      title: `催办出库 · ${planNo || '出库协同'}`,
      content,
      planId: ex?.planId || row?.planId,
      planNo,
      requisitionId: ex?.requisitionId || row?.requisitionId,
      outboundNo: row?.out && row.out !== '—' ? row.out : null,
      exceptionTitle: ex?.title || null,
      createdBy: userId.value
    })
    coordFeedback.value = `已通知仓管（#${notice.noticeId}）：请优先处理 ${planNo || '当前计划'}。`
  } catch {
    coordFeedback.value = coordinationError.value || '催办通知发送失败，请稍后重试。'
  }
  if (canRunOutbound.value) {
    triggerOutboundAgentTask({
      planId: ex?.planId || row?.planId,
      planNo,
      requisitionId: ex?.requisitionId || row?.requisitionId,
      promptText: prompt.value
    })
  }
}

function launchSplitRequisition(ex) {
  const row = currentOutbound.value
  const planNo = ex?.planNo || row?.plan
  const planId = ex?.planId || row?.planId
  prompt.value = `针对「${ex?.title || '缺料'}」拆分领料：先发放齐套物料，短缺项单独挂起并记录采购/调拨建议。计划 ${planNo || ''}。`
  coordFeedback.value = '正在按齐套结果重算领料方案…'
  startPmcAgentTask({
    planId,
    planNo,
    taskName: `PMC拆分领料-${planNo || planId}`,
    promptText: prompt.value
  })
}

async function launchReplenishAdvice(ex) {
  const row = currentOutbound.value
  const planNo = ex?.planNo || row?.plan
  const content = `针对「${ex?.title || '缺料'}」建议补料/库区调拨，保障计划 ${planNo || '当前计划'} 开工。`
  prompt.value = `针对「${ex?.title || '缺料'}」生成补料与库区间调拨建议清单，评估对计划 ${planNo || ''} 开工的影响。`
  coordFeedback.value = '正在提交补料/调拨建议…'
  try {
    const notice = await sendCoordinationNotice({
      noticeType: 'REPLENISH_ADVICE',
      title: `补料/调拨建议 · ${planNo || '缺料'}`,
      content,
      planId: ex?.planId || row?.planId,
      planNo,
      requisitionId: ex?.requisitionId || row?.requisitionId,
      outboundNo: row?.out && row.out !== '—' ? row.out : null,
      exceptionTitle: ex?.title || null,
      createdBy: userId.value
    })
    coordFeedback.value = `补料/调拨建议已提交仓管（#${notice.noticeId}），可在订单计划中心继续跟进。`
  } catch {
    coordFeedback.value = coordinationError.value || '补料/调拨建议提交失败，请稍后重试。'
  }
}

async function launchReviewFollowUp(ex) {
  const row = currentOutbound.value
  const planNo = ex?.planNo || row?.plan
  const content = `请跟进「${ex?.title || '复核异常'}」：核对拣货明细与批次，必要时安排重新拣货。计划 ${planNo || '当前计划'}。`
  prompt.value = `针对「${ex?.title || '复核异常'}」跟进出库复核：核对拣货明细与批次，必要时安排重新拣货。计划 ${planNo || ''}。`
  coordFeedback.value = '正在发送复核跟进通知…'
  try {
    const notice = await sendCoordinationNotice({
      noticeType: 'REVIEW_FOLLOW',
      title: `复核跟进 · ${planNo || '复核异常'}`,
      content,
      planId: ex?.planId || row?.planId,
      planNo,
      requisitionId: ex?.requisitionId || row?.requisitionId,
      outboundNo: ex?.outboundNo || (row?.out && row.out !== '—' ? row.out : null),
      exceptionTitle: ex?.title || null,
      createdBy: userId.value
    })
    coordFeedback.value = `复核跟进已通知仓管（#${notice.noticeId}），请处理 ${planNo || '当前计划'}。`
  } catch {
    coordFeedback.value = coordinationError.value || '复核跟进通知发送失败，请稍后重试。'
  }
  if (canRunOutbound.value) {
    triggerOutboundAgentTask({
      planId: ex?.planId || row?.planId,
      planNo,
      requisitionId: ex?.requisitionId || row?.requisitionId,
      promptText: prompt.value
    })
  }
}

function pmcActionsFor(ex) {
  const type = ex?.type || ''
  const status = currentOutbound.value?.status || ''
  const impact = currentOutbound.value?.impact || ''
  const actions = []

  if (type.includes('缺料') || status === '缺料暂停' || impact === '影响开工') {
    actions.push({ key: 'split', label: '拆分领料', run: () => launchSplitRequisition(ex) })
    actions.push({ key: 'replenish', label: '补料/调拨', run: () => launchReplenishAdvice(ex) })
  }
  if (type.includes('复核') || type.includes('批次') || status === '待复核') {
    actions.push({ key: 'review', label: '复核跟进', run: () => launchReviewFollowUp(ex) })
  }
  if (status === '待出库' || !actions.length) {
    actions.push({ key: 'urge', label: '催仓管出库', run: () => launchUrgeWarehouse(ex) })
  } else if (!actions.some((a) => a.key === 'urge')) {
    actions.push({ key: 'urge', label: '通知仓管', run: () => launchUrgeWarehouse(ex) })
  }
  return actions
}

function defaultPmcActions() {
  const row = currentOutbound.value
  if (!row) return []
  const fake = { title: row.status, planId: row.planId, planNo: row.plan, requisitionId: row.requisitionId, type: '' }
  return pmcActionsFor(fake)
}

function stepClass(step) {
  if (step.state === 'done') return 'done'
  if (step.state === 'current') return 'current'
  return 'pending'
}

function stepLabel(step) {
  if (step.state === 'done') return '已完成'
  if (step.state === 'current') {
    if (currentOutbound.value?.status === '缺料暂停') {
      return `暂停 · ${currentOutbound.value.note || ''}`
    }
    return '进行中'
  }
  return '等待'
}

function readyBarClass(ready) {
  if (ready == null) return 'muted'
  if (ready >= 90) return 'ok'
  if (ready >= 70) return 'mid'
  return 'low'
}

function statusTone(status) {
  return outboundStatusClass(status) || 'warn'
}

function impactTone(impact) {
  return impactClass(impact) || 'warn'
}

function resetFilters() {
  filterStatus.value = ''
  filterImpact.value = ''
  filterKeyword.value = ''
  currentPage.value = 1
}

function goPage(page) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
}

function updateTimeOf(row) {
  const steps = row.timeline || []
  const current = steps.find((s) => s.state === 'current')
  const lastDone = [...steps].reverse().find((s) => s.state === 'done')
  return current?.time || lastDone?.time || '—'
}
</script>

<template>
  <section v-if="allowed" class="ob-board">
    <p v-if="pmcDataLoading && !outboundList.length" class="data-hint">正在加载出库数据...</p>
    <p v-else-if="pmcDataError" class="data-hint danger">{{ pmcDataError }}</p>
    <p v-if="coordFeedback" class="queue-tip">{{ coordFeedback }}</p>
    <p v-if="agentTaskError" class="data-hint danger">{{ agentTaskError }}</p>

    <header class="ob-header">
      <div class="ob-header-copy">
        <h1>{{ boardTitle }}</h1>
        <p v-if="boardSubtitle">{{ boardSubtitle }}</p>
      </div>
      <div v-if="!isPmc" class="ob-stats">
        <div
          v-for="stat in warehouseSummary"
          :key="stat.label"
          class="ob-stat"
          :class="`tone-${stat.tone}`"
        >
          <span class="ob-stat-icon" aria-hidden="true"></span>
          <div>
            <strong>{{ stat.value }}</strong>
            <small>{{ stat.label }}</small>
          </div>
        </div>
      </div>
    </header>

    <div v-if="isPmc && globalAlerts.length" class="ob-global-bar">
      <span>全局物料预警 {{ globalAlerts.length }} 项</span>
      <button type="button" class="ob-link" @click="showGlobalAlerts = !showGlobalAlerts">
        {{ showGlobalAlerts ? '收起' : '查看' }}
      </button>
    </div>
    <div v-if="showGlobalAlerts && globalAlerts.length" class="ob-global-panel">
      <article v-for="ex in globalAlerts" :key="'g-' + ex.title" class="ob-alert-item">
        <strong>{{ ex.title }}</strong>
        <span>{{ ex.type }} · 影响：{{ ex.impact }}</span>
      </article>
    </div>

    <div class="ob-main">
      <div class="ob-left">
        <div class="ob-panel">
          <div class="ob-tabs">
            <button
              v-for="tab in TAB_DEFS"
              :key="tab.key"
              type="button"
              class="ob-tab"
              :class="{ active: activeTab === tab.key }"
              @click="activeTab = tab.key"
            >
              {{ tab.label }}
              <em v-if="tabCounts[tab.key]">{{ tabCounts[tab.key] }}</em>
            </button>
          </div>

          <div class="ob-filters">
            <label class="ob-field">
              <span>出库状态</span>
              <select v-model="filterStatus">
                <option value="">全部状态</option>
                <option v-for="s in statusOptions" :key="s" :value="s">{{ s }}</option>
              </select>
            </label>
            <label class="ob-field">
              <span>影响程度</span>
              <select v-model="filterImpact">
                <option value="">全部影响</option>
                <option v-for="s in impactOptions" :key="s" :value="s">{{ s }}</option>
              </select>
            </label>
            <label class="ob-field ob-field-search">
              <span>搜索</span>
              <div class="ob-search">
                <input v-model="filterKeyword" type="search" placeholder="搜索计划号/领料单/产品" />
                <i aria-hidden="true">⌕</i>
              </div>
            </label>
            <button type="button" class="ob-btn-text" @click="resetFilters">重置</button>
          </div>

          <div class="ob-table-wrap">
            <table class="ob-table">
              <thead>
                <tr>
                  <th>生产计划单</th>
                  <th>领料单</th>
                  <th>产品 / 工单</th>
                  <th>齐套率</th>
                  <th>出库状态</th>
                  <th>影响</th>
                  <th>协同</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="{ row, absoluteIndex } in pagedRows"
                  :key="(row.out || '') + '-' + (row.req || '') + '-' + (row.planId || absoluteIndex)"
                  :class="{ active: absoluteIndex === selectedOutbound }"
                  @click="selectPlan(absoluteIndex)"
                >
                  <td>
                    <button type="button" class="ob-plan-link" @click.stop="selectPlan(absoluteIndex)">
                      {{ row.plan }}
                    </button>
                    <small v-if="row.out && row.out !== '—'" class="ob-sub">出库单 {{ row.out }}</small>
                  </td>
                  <td>{{ row.req }}</td>
                  <td>{{ row.product }}</td>
                  <td>
                    <div class="ob-ready">
                      <em>{{ row.ready }}%</em>
                      <i class="ob-ready-track" :class="readyBarClass(row.ready)">
                        <b :style="{ width: `${Math.min(row.ready || 0, 100)}%` }"></b>
                      </i>
                    </div>
                  </td>
                  <td><i :class="['ob-tag', statusTone(row.status)]">{{ row.status }}</i></td>
                  <td><i :class="['ob-tag', impactTone(row.impact)]">{{ row.impact }}</i></td>
                  <td>
                    <span v-if="exceptionCountFor(row) > 0" class="ob-badge">{{ exceptionCountFor(row) }}</span>
                    <span v-else class="ob-muted">—</span>
                  </td>
                  <td class="ob-time">{{ updateTimeOf(row) }}</td>
                  <td>
                    <button type="button" class="ob-op" @click.stop="selectPlan(absoluteIndex)">查看</button>
                  </td>
                </tr>
                <tr v-if="!pagedRows.length">
                  <td colspan="9" class="ob-empty">当前筛选条件下暂无数据</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="ob-pagination">
            <span class="ob-total">共 {{ totalFiltered }} 条</span>
            <div class="ob-pages">
              <button type="button" :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">‹</button>
              <button
                v-for="n in pageNumbers"
                :key="n"
                type="button"
                :class="{ active: n === currentPage }"
                @click="goPage(n)"
              >{{ n }}</button>
              <button type="button" :disabled="currentPage >= totalPages" @click="goPage(currentPage + 1)">›</button>
              <select v-model.number="pageSize" class="ob-page-size">
                <option :value="5">5 条/页</option>
                <option :value="10">10 条/页</option>
                <option :value="20">20 条/页</option>
              </select>
            </div>
          </div>
        </div>

        <section v-if="currentOutbound" class="ob-flow">
          <div class="ob-flow-head">
            <h2>出库协同流程跟踪 · {{ currentOutbound.plan }}</h2>
            <small>{{ currentOutbound.note }}</small>
          </div>
          <div class="ob-stepper">
            <div
              v-for="(step, idx) in timelineSteps"
              :key="step.name"
              class="ob-step"
              :class="stepClass(step)"
            >
              <div class="ob-step-node">
                <span class="ob-step-dot">
                  <template v-if="step.state === 'done'">✓</template>
                  <template v-else>{{ idx + 1 }}</template>
                </span>
                <i v-if="idx < timelineSteps.length - 1" class="ob-step-line"></i>
              </div>
              <strong>{{ step.name }}</strong>
              <em>{{ stepLabel(step) }}</em>
              <small>{{ step.time || '—' }}</small>
            </div>
            <div v-if="!timelineSteps.length" class="ob-empty soft">暂无时间线数据</div>
          </div>
          <div class="ob-flow-meta">
            <span>当前处理人：{{ currentStepMeta.handler }}</span>
            <span>当前阶段：{{ currentStepMeta.phase }}</span>
            <span>节点时间：{{ currentStepMeta.time }}</span>
            <span>进度：{{ currentStepMeta.progress }}</span>
          </div>
        </section>

        <section v-if="currentOutbound && canRunOutbound" class="ob-launch">
          <div>
            <span class="ob-launch-tag">仓管一键发起出库 Agent</span>
            <strong>执行 {{ currentOutbound.plan }} 领料出库：生成出库单、拣货复核并确认出库。</strong>
          </div>
          <button type="button" :disabled="agentTaskLoading" @click="triggerOutboundAgentTask()">
            {{ agentTaskLoading ? 'Agent 执行中...' : '执行出库 Agent' }}
          </button>
        </section>
      </div>

      <aside class="ob-side">
        <section v-if="agentSteps.length" class="ob-side-card">
          <h3>Agent 执行追踪</h3>
          <p class="ob-side-meta">
            <span>{{ agentTaskNo || '最新任务' }}</span>
            <i :class="['ob-tag', agentTaskStatus === 'SUCCESS' ? 'ok' : agentTaskStatus === 'MANUAL_REQUIRED' ? 'danger' : 'warn']">
              {{ agentTaskStatus || 'RUNNING' }}
            </i>
          </p>
          <div v-for="step in agentSteps" :key="step.stepId" class="ob-agent-step">
            <strong>{{ step.stepNo }}. {{ step.stepName || step.agentLabel }}</strong>
            <small>{{ step.status }} · {{ step.durationMs != null ? `${(step.durationMs / 1000).toFixed(1)}s` : '—' }}</small>
          </div>
          <button v-if="agentTaskId" type="button" class="ob-op accent" @click="openAgentTrace">查看完整 Trace</button>
        </section>

        <section class="ob-side-card">
          <div class="ob-side-head">
            <h3>
              {{ isPmc ? '本计划协同事项' : '本计划异常' }}
              <em v-if="currentExceptions.length" class="ob-badge">{{ currentExceptions.length }}</em>
            </h3>
            <small v-if="currentOutbound">{{ currentOutbound.plan }}</small>
          </div>

          <div v-if="currentOutbound" class="ob-side-status">
            <span>{{ currentOutbound.req || '—' }}</span>
            <i :class="['ob-tag', statusTone(currentOutbound.status)]">{{ currentOutbound.status }}</i>
            <i :class="['ob-tag', impactTone(currentOutbound.impact)]">{{ currentOutbound.impact }}</i>
          </div>

          <div v-if="isPmc && currentOutbound" class="ob-quick">
            <p class="ob-quick-label">快捷操作</p>
            <div class="ob-quick-grid">
              <button
                v-for="action in defaultPmcActions()"
                :key="'d-' + action.key"
                type="button"
                :disabled="agentTaskLoading"
                @click="action.run()"
              >{{ action.label }}</button>
            </div>
          </div>

          <div v-if="primaryException" class="ob-alert-box">
            <strong>{{ primaryException.title }}</strong>
            <p>{{ primaryException.type }} · 影响：{{ primaryException.impact }}</p>
          </div>

          <div v-if="primaryException?.suggestions?.length" class="ob-ai">
            <p class="ob-quick-label">AI 建议</p>
            <ol>
              <li v-for="tip in primaryException.suggestions" :key="tip">{{ tip }}</li>
            </ol>
          </div>

          <article
            v-for="ex in currentExceptions.slice(primaryException ? 1 : 0)"
            :key="(ex.planNo || '') + '-' + ex.title + '-' + (ex.outboundNo || '')"
            class="ob-ex-item"
          >
            <div class="ob-ex-head">
              <span>{{ ex.type }}</span>
              <strong>{{ ex.title }}</strong>
            </div>
            <p>影响：{{ ex.impact }}</p>
            <div class="ob-quick-grid compact">
              <template v-if="isPmc">
                <button
                  v-for="action in pmcActionsFor(ex)"
                  :key="action.key"
                  type="button"
                  :disabled="agentTaskLoading"
                  @click="action.run()"
                >{{ action.label }}</button>
              </template>
              <template v-else>
                <button
                  type="button"
                  :disabled="agentTaskLoading || !ex.requisitionId"
                  @click="launchUrgeWarehouse(ex)"
                >通知仓管员</button>
                <button
                  type="button"
                  :disabled="agentTaskLoading || !ex.requisitionId"
                  @click="triggerOutboundAgentTask({
                    planId: ex.planId,
                    planNo: ex.planNo,
                    requisitionId: ex.requisitionId,
                    promptText: `针对「${ex.title}」执行出库并处理异常`
                  })"
                >执行出库 Agent</button>
              </template>
            </div>
          </article>

          <p v-if="currentOutbound && !currentExceptions.length" class="ob-empty soft">
            本计划暂无待协调事项
            <button
              v-if="planBoundExceptions.length"
              type="button"
              class="ob-link"
              @click="jumpToPlanWithException(planBoundExceptions[0])"
            >跳到有异常的计划</button>
          </p>
          <p v-else-if="!currentOutbound" class="ob-empty soft">请选择左侧生产计划</p>

          <div v-if="isPmc && myNotices.length" class="ob-notices">
            <p class="ob-quick-label">已发送协同通知（{{ myNotices.length }}）</p>
            <ul>
              <li v-for="n in myNotices.slice(0, 6)" :key="n.noticeId">
                <div class="ob-notice-head">
                  <i class="ob-tag info">{{ n.noticeTypeLabel }}</i>
                  <i :class="['ob-tag', n.status === 'DONE' ? 'ok' : n.status === 'READ' ? 'warn' : 'danger']">
                    {{ n.statusLabel }}
                  </i>
                </div>
                <strong>{{ n.title }}</strong>
                <small>{{ n.createdAt }}<template v-if="n.handledBy"> · 处理人 {{ n.handledBy }}</template></small>
              </li>
            </ul>
          </div>

          <div class="ob-logs">
            <p class="ob-quick-label">协同日志（最新 {{ recentLogs.length }} 条）</p>
            <ul>
              <li v-for="(log, idx) in recentLogs" :key="idx">
                <time>{{ log.time }}</time>
                <p>{{ log.text }}</p>
                <em>{{ log.user }}</em>
              </li>
              <li v-if="!recentLogs.length" class="ob-empty soft">暂无协同日志</li>
            </ul>
          </div>
        </section>
      </aside>
    </div>
  </section>
</template>
