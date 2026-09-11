import { computed, ref } from 'vue'
import { apiGet, apiPost, apiPostQuery } from '../api'
import { useSession } from './useSession'

const productionPlans = ref([])
const planShortages = ref([])
const pendingReview = ref(0)
const inboxHint = ref('')
const lastIntegrationSyncAt = ref('')
const lastIntegrationSyncCount = ref(0)
const syncLoading = ref(false)
const syncMessage = ref('')
const syncError = ref('')
const outboundStats = ref([
  ['今日待出库领料单', '0'],
  ['已生成出库单', '0'],
  ['拣货中', '0'],
  ['复核异常', '0'],
  ['影响生产计划', '0'],
  ['平均出库耗时', '—']
])
const outboundList = ref([])
const outboundExceptions = ref([])
const readyTrend = ref([])
const dataSources = ref([])
const analysisReports = ref([])
const trendAlert = ref('')
const shortageAlert = ref('')
const pmcKpis = ref([
  ['今日生产计划', '0', 'plan', '↑ +0', '较昨日'],
  ['可正常执行', '0', 'ok', '↑ +0', '较昨日'],
  ['高风险计划', '0', 'danger', '→ 0', '较昨日'],
  ['平均齐套率', '0%', 'info', '→ 0%', '较昨日'],
  ['今日领料完成率', '0%', 'cyan', '→ 0%', '较昨日'],
  ['出库完成率', '0%', 'orange', '→ 0%', '较昨日']
])
const shortageTop = ref([])
const planFunnel = ref([
  ['订单', 0],
  ['计划', 0],
  ['已齐套', 0],
  ['已领料', 0],
  ['生产', 0],
  ['出库', 0]
])
const riskSummary = ref([])
const pmcDataLoading = ref(false)
const pmcDataError = ref('')
const lastSyncAt = ref('')
const selectedOutbound = ref(0)
const selectedShortage = ref(0)
const expandedTracePlan = ref('')
const simQty = ref(100)
const simRunning = ref(false)
const simResult = ref(null)

let pollTimer = null

const ADVICE_MAP = {
  采购: '采购',
  采购补料: '采购',
  调拨: '调拨',
  替代料: '替代料',
  借料: '借料',
  催检: '催检',
  关注: '关注'
}

function normalizeAdvice(raw) {
  const text = String(raw || '')
  for (const key of Object.keys(ADVICE_MAP)) {
    if (text.includes(key)) return ADVICE_MAP[key]
  }
  if (text.includes('质量')) return '催检'
  if (text.includes('库位') || text.includes('批次')) return '调拨'
  return text ? '采购' : '关注'
}

function enrichShortageRow(row) {
  const stock = Number(row.stock ?? 0)
  const safe = Number(row.safe ?? 0)
  const times = Number(row.times ?? 0)
  const plans = Number(row.plans ?? 0)
  const belowSafe = stock < safe
  const impactScore =
    row.impactScore != null
      ? Number(row.impactScore)
      : Math.min(100, times * 12 + plans * 18 + (belowSafe ? 28 : 0) + (stock === 0 ? 20 : 0))
  const stockTrend =
    row.stockTrend ||
    (stock === 0 ? 'down2' : belowSafe ? 'down' : stock >= safe * 1.2 ? 'up' : 'flat')
  const eta = row.eta || (stock === 0 ? '未知' : belowSafe ? '2天' : '今天')
  const aiAdvice = row.aiAdvice || normalizeAdvice(row.advice)
  const detail = row.detail && typeof row.detail === 'object' ? { ...row.detail } : {}
  if (!Array.isArray(detail.planList) && typeof detail.plans === 'string' && detail.plans !== '暂无影响计划') {
    detail.planList = detail.plans.split(/[、,，]/).map((s) => s.trim()).filter(Boolean)
  }
  if (!Array.isArray(detail.reqList) && typeof detail.reqs === 'string' && detail.reqs !== '暂无关联领料单') {
    detail.reqList = detail.reqs.split(/[、,，]/).map((s) => s.trim()).filter(Boolean)
  }
  return {
    ...row,
    impactScore,
    stockTrend,
    eta,
    aiAdvice,
    advice: aiAdvice,
    level: row.level || (impactScore >= 70 ? 'danger' : belowSafe ? 'warn' : 'ok'),
    detail
  }
}

function riskLevelOfPlan(plan) {
  const ready = Number(plan.ready ?? 100)
  if (plan.status === '缺料' || ready < 50) return '高风险'
  if (ready < 80 || (plan.shortage && plan.shortage > 0)) return '中风险'
  return '低风险'
}

function remainHoursOfPlan(plan) {
  if (!plan.finish && !plan.start) return '—'
  const raw = plan.finish || plan.start
  const end = new Date(String(raw).replace(/-/g, '/'))
  if (Number.isNaN(end.getTime())) return '—'
  const diff = Math.round((end.getTime() - Date.now()) / 3600000)
  if (diff < 0) return '已逾期'
  if (diff < 24) return `${diff}h`
  return `${Math.ceil(diff / 24)}天`
}

export function usePmc() {
  const { activeRoleId } = useSession()

  const planStats = computed(() => ({
    total: productionPlans.value.length,
    pendingOrders: productionPlans.value.filter((row) => row.hasPlan === false).length,
    withPlan: productionPlans.value.filter((row) => row.hasPlan !== false).length,
    waiting: productionPlans.value.filter((plan) => plan.status === '待领料').length,
    shortage: productionPlans.value.filter((plan) => plan.status === '缺料').length
  }))

  const currentOutbound = computed(() => outboundList.value[selectedOutbound.value] ?? outboundList.value[0] ?? null)
  const currentShortage = computed(() => shortageTop.value[selectedShortage.value] ?? shortageTop.value[0] ?? null)

  const cockpitMeta = computed(() => {
    const now = new Date()
    const hour = now.getHours()
    const shift = hour < 8 ? '夜班' : hour < 16 ? '白班' : '晚班'
    const date = now.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      weekday: 'short'
    })
    return { date, shift, refreshedAt: lastSyncAt.value || '—' }
  })

  const cockpitKpis = computed(() =>
    pmcKpis.value.map((kpi) => {
      const label = kpi[0]
      const value = kpi[1]
      const tone = kpi[2] || ''
      const delta = kpi[3] || ''
      const compare = kpi[4] || '较昨日'
      const up = String(delta).includes('↑') || String(delta).startsWith('+')
      const down = String(delta).includes('↓') || String(delta).startsWith('-')
      return { label, value, tone, delta, compare, up, down }
    })
  )

  const priorityPlans = computed(() => {
    const shortageByPlan = {}
    for (const row of planShortages.value) {
      const key = row.planNo || row.planId
      if (!key || key === '—') continue
      if (!shortageByPlan[key]) shortageByPlan[key] = row.itemName
    }
    return productionPlans.value
      .filter((p) => p.hasPlan !== false && p.id)
      .map((p) => {
        const risk = riskLevelOfPlan(p)
        const shortageItem = shortageByPlan[p.id] || (p.shortage > 0 ? `缺料 ${p.shortage} 项` : '—')
        return {
          id: p.id,
          planId: p.planId,
          product: p.product || '—',
          ready: p.ready ?? 0,
          risk,
          riskTone: risk === '高风险' ? 'danger' : risk === '中风险' ? 'warn' : 'ok',
          shortageItem,
          remain: remainHoursOfPlan(p),
          status: p.status
        }
      })
      .sort((a, b) => {
        const rank = { 高风险: 0, 中风险: 1, 低风险: 2 }
        if (rank[a.risk] !== rank[b.risk]) return rank[a.risk] - rank[b.risk]
        return a.ready - b.ready
      })
      .slice(0, 6)
  })

  const aiDecision = computed(() => {
    const highRisk = priorityPlans.value.filter((p) => p.risk === '高风险').length
    const top = shortageTop.value[0]
    const advice = top?.aiAdvice || top?.advice || '优先处理高风险计划'
    return {
      highRisk,
      shortageMaterial: top?.name || '暂无',
      advice: top ? `优先${advice}「${top.name}」` : advice,
      risks: riskSummary.value
    }
  })

  const funnelStages = computed(() => {
    const rows = planFunnel.value.map((row) => ({
      label: Array.isArray(row) ? row[0] : row.label,
      value: Number(Array.isArray(row) ? row[1] : row.value) || 0
    }))
    const max = Math.max(...rows.map((r) => r.value), 1)
    return rows.map((row, index) => ({
      ...row,
      width: Math.max(8, Math.round((row.value / max) * 100)),
      drop: index === 0 ? 0 : rows[index - 1].value - row.value
    }))
  })

  const riskHeatmap = computed(() => {
    const days = ['今日', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri']
    const dims = ['采购', '库存', '生产', '物流']
    const baseReady = readyTrend.value.length
      ? readyTrend.value[readyTrend.value.length - 1].ready
      : 75
    const shortPressure = shortageTop.value.filter((s) => Number(s.stock) < Number(s.safe)).length
    const levelsFor = (dimIndex) =>
      days.map((_, dayIndex) => {
        let score = baseReady - dayIndex * 3 - dimIndex * 4 - shortPressure * 5
        if (dimIndex === 2) score += 12
        if (score >= 78) return 'ok'
        if (score >= 62) return 'warn'
        return 'danger'
      })
    return dims.map((dim, i) => ({ dim, cells: levelsFor(i) }))
  })

  const forecast7d = computed(() => {
    const last = readyTrend.value.length
      ? readyTrend.value[readyTrend.value.length - 1]
      : { ready: 76, short: 3 }
    const labels = ['D+1', 'D+2', 'D+3', 'D+4', 'D+5', 'D+6', 'D+7']
    const pressure = shortageTop.value.filter((s) => Number(s.stock) < Number(s.safe)).length
    return labels.map((day, i) => {
      const ready = Math.max(
        55,
        Math.min(95, Math.round(last.ready - i * (1.2 + pressure * 0.4) + (i === 3 ? -2 : 0)))
      )
      const short = Math.max(0, Math.round((last.short || 2) + i * 0.6 + pressure * 0.3))
      return { day, ready, short }
    })
  })

  const simBaseline = computed(() => {
    const avgReady = productionPlans.value.length
      ? Math.round(
          productionPlans.value.reduce((sum, p) => sum + (Number(p.ready) || 0), 0) /
            productionPlans.value.length
        )
      : Number(String(pmcKpis.value[3]?.[1] || '0').replace('%', '')) || 61
    const delayPlans = productionPlans.value.filter(
      (p) => p.status === '缺料' || (p.ready != null && p.ready < 70)
    ).length
    const material = shortageTop.value[0]?.name || '防爆接插件'
    return { avgReady, delayPlans, material }
  })

  const traceChain = computed(() => {
    const item = currentShortage.value
    if (!item) return []
    const detail = item.detail || {}
    const plans = detail.planList?.length
      ? detail.planList
      : typeof detail.plans === 'string' && detail.plans !== '暂无影响计划'
        ? detail.plans.split(/[、,，]/).map((s) => s.trim()).filter(Boolean)
        : []
    return [
      { stage: '影响计划', value: plans.length ? plans.join('、') : '—', items: plans },
      { stage: '订单', value: plans[0] ? `关联订单 · ${plans[0]}` : '—', items: [] },
      { stage: '采购', value: detail.purchase || `建议${item.aiAdvice || '采购'}`, items: [] },
      { stage: '库存', value: detail.inventory || `${item.stock} / ${item.safe}`, items: [] },
      { stage: '领料', value: detail.reqs || '—', items: detail.reqList || [] },
      { stage: '出库', value: detail.lastIn || '待出库联动', items: [] }
    ]
  })

  async function loadPmcData(silent = false) {
    if (!silent) pmcDataLoading.value = true
    pmcDataError.value = ''
    try {
      const data = await apiGet('/pmc/workbench/overview')
      productionPlans.value = data.plans ?? []
      planShortages.value = normalizeShortages(data.shortages)
      pendingReview.value = data.pendingReview ?? 0
      inboxHint.value = data.inboxHint ?? ''
      lastIntegrationSyncAt.value = data.lastIntegrationSyncAt ?? ''
      lastIntegrationSyncCount.value = data.lastIntegrationSyncCount ?? 0
      outboundStats.value = data.outboundStats ?? outboundStats.value
      outboundList.value = data.outboundList ?? []
      outboundExceptions.value = data.outboundExceptions ?? []
      readyTrend.value = (data.readyTrend ?? []).map((row) => ({
        day: row.day,
        ready: row.ready ?? 0,
        short: row.shortCount ?? row.short ?? 0,
        delay: row.delay ?? 0
      }))
      dataSources.value = data.dataSources ?? []
      analysisReports.value = data.analysisReports ?? []
      trendAlert.value = data.trendAlert ?? ''
      shortageAlert.value = data.shortageAlert ?? ''
      pmcKpis.value = data.kpis ?? pmcKpis.value
      shortageTop.value = (data.shortageTop ?? []).map(enrichShortageRow)
      planFunnel.value = data.planFunnel ?? planFunnel.value
      riskSummary.value = data.riskSummary ?? []
      lastSyncAt.value = new Date().toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      })
      if (selectedOutbound.value >= outboundList.value.length) {
        selectedOutbound.value = 0
      }
      if (selectedShortage.value >= shortageTop.value.length) {
        selectedShortage.value = 0
      }
      simResult.value = null
    } catch (error) {
      pmcDataError.value = error.message || '加载 PMC 数据失败'
    } finally {
      if (!silent) pmcDataLoading.value = false
    }
  }

  function runSimulation() {
    simRunning.value = true
    const base = simBaseline.value
    const qty = Math.max(1, Number(simQty.value) || 100)
    const boost = Math.min(28, Math.round(qty / 8) + 8)
    const nextReady = Math.min(98, base.avgReady + boost)
    const reduced = Math.min(
      Math.max(base.delayPlans, 1),
      Math.max(1, Math.round(qty / 20) + Math.floor(base.delayPlans * 0.4))
    )
    window.setTimeout(() => {
      simResult.value = {
        material: base.material,
        qty,
        fromReady: base.avgReady,
        toReady: nextReady,
        reducedDelay: reduced
      }
      simRunning.value = false
    }, 480)
  }

  function normalizeShortages(raw) {
    if (!Array.isArray(raw)) return []
    return raw.map((row) => {
      if (row && typeof row === 'object' && !Array.isArray(row)) {
        return {
          planId: row.planId ?? null,
          orderId: row.orderId ?? null,
          itemId: row.itemId ?? null,
          orderNo: row.orderNo || '—',
          planNo: row.planNo || '—',
          itemName: row.itemName || '未知物料',
          shortageType: row.shortageType || 'REAL_SHORTAGE',
          shortageTypeLabel: row.shortageTypeLabel || '真实缺料',
          requiredQty: row.requiredQty ?? '',
          shortageQty: row.shortageQty ?? '',
          availableQty: row.availableQty ?? '',
          gapText: row.gapText || '',
          advice: row.advice || '',
          source: row.source || 'LIVE'
        }
      }
      const text = String(row?.[2] ?? '')
      let shortageType = 'REAL_SHORTAGE'
      let shortageTypeLabel = '真实缺料'
      if (text.includes('质量未放行')) {
        shortageType = 'QUALITY_PENDING'
        shortageTypeLabel = '质量未放行'
      } else if (text.includes('质量异常')) {
        shortageType = 'QUALITY_ABNORMAL'
        shortageTypeLabel = '质量异常'
      } else if (text.includes('库位') || text.includes('批次不可用')) {
        shortageType = 'LOCATION_UNAVAILABLE'
        shortageTypeLabel = '库位/批次不可用'
      }
      return {
        planId: null,
        orderId: null,
        itemId: null,
        orderNo: '—',
        planNo: row?.[1] || '—',
        itemName: row?.[0] || '未知物料',
        shortageType,
        shortageTypeLabel,
        requiredQty: '',
        shortageQty: '',
        availableQty: '',
        gapText: text.replace(/^[^·]*·\s*/, '') || text,
        advice: row?.[3] || '',
        source: 'LIVE'
      }
    })
  }

  function shortageTypeClass(type) {
    switch (type) {
      case 'QUALITY_PENDING':
        return 'type-pending'
      case 'QUALITY_ABNORMAL':
        return 'type-abnormal'
      case 'LOCATION_UNAVAILABLE':
        return 'type-location'
      default:
        return 'type-real'
    }
  }

  async function syncOrdersFromErpMes() {
    syncLoading.value = true
    syncError.value = ''
    syncMessage.value = ''
    try {
      const result = await apiPost('/pmc/integration/sync-orders', {})
      syncMessage.value = ''
      pendingReview.value = result.pendingReview ?? pendingReview.value
      lastIntegrationSyncAt.value = result.syncedAt ?? lastIntegrationSyncAt.value
      lastIntegrationSyncCount.value = result.syncedCount ?? 0
      inboxHint.value = pendingReview.value > 0
        ? `你有 ${pendingReview.value} 条新订单 / 计划待审核。`
        : '暂无待审核订单。'
      await loadPmcData(true)
      return result
    } catch (error) {
      syncError.value = error.message || 'ERP/MES 同步失败'
      throw error
    } finally {
      syncLoading.value = false
    }
  }

  /** PMC 独立审核客户订单（与生成生产计划分离） */
  async function approveCustomerOrder(orderId, approvedBy = 1) {
    if (!orderId) throw new Error('缺少订单 ID')
    await apiPostQuery('/order/approve', { orderId, approvedBy })
    const idx = productionPlans.value.findIndex((row) => row.orderId === orderId)
    if (idx >= 0) {
      productionPlans.value[idx] = {
        ...productionPlans.value[idx],
        status: '待排产'
      }
    }
    if (pendingReview.value > 0) {
      pendingReview.value -= 1
    }
    await loadPmcData(true)
    syncMessage.value = `订单已审核通过，可生成生产计划`
    return true
  }

  function startPmcPolling(intervalMs = 8000) {
    stopPmcPolling()
    pollTimer = setInterval(() => loadPmcData(true), intervalMs)
  }

  function stopPmcPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  function statusClass(status) {
    if (status === '可生产') return 'ok'
    if (status === '缺料') return 'danger'
    if (status === '待排产' || status === '待审核') return 'warn'
    return 'warn'
  }

  function outboundStatusClass(status) {
    if (status === '缺料暂停') return 'danger'
    if (status === '待复核' || status === '拣货中' || status === '待出库') return 'warn'
    return 'ok'
  }

  function impactClass(impact) {
    if (impact === '影响开工') return 'danger'
    if (impact === '待确认') return 'warn'
    return 'ok'
  }

  function stockTrendLabel(trend) {
    if (trend === 'down2') return '↓↓'
    if (trend === 'down') return '↓'
    if (trend === 'up') return '↑'
    return '→'
  }

  return {
    activeRoleId,
    productionPlans,
    planShortages,
    pendingReview,
    inboxHint,
    lastIntegrationSyncAt,
    lastIntegrationSyncCount,
    syncLoading,
    syncMessage,
    syncError,
    outboundStats,
    outboundList,
    outboundExceptions,
    readyTrend,
    dataSources,
    analysisReports,
    trendAlert,
    shortageAlert,
    pmcKpis,
    shortageTop,
    planFunnel,
    riskSummary,
    pmcDataLoading,
    pmcDataError,
    lastSyncAt,
    selectedOutbound,
    selectedShortage,
    expandedTracePlan,
    simQty,
    simRunning,
    simResult,
    planStats,
    currentOutbound,
    currentShortage,
    cockpitMeta,
    cockpitKpis,
    priorityPlans,
    aiDecision,
    funnelStages,
    riskHeatmap,
    forecast7d,
    simBaseline,
    traceChain,
    loadPmcData,
    runSimulation,
    syncOrdersFromErpMes,
    approveCustomerOrder,
    shortageTypeClass,
    stockTrendLabel,
    startPmcPolling,
    stopPmcPolling,
    statusClass,
    outboundStatusClass,
    impactClass
  }
}
