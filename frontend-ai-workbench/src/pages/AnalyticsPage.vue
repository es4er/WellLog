<script setup>
import { reactive, computed, onMounted, onActivated, onBeforeUnmount, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { useDemoRole } from '../composables/useRoleAccess'
import { menuToPath } from '../router/menuRoutes'
import { apiGet, getAuthToken } from '../api'
import { useInventory } from '../composables/useInventory'

const router = useRouter()
const { demoRoleId } = useDemoRole()
const isPmc = computed(() => demoRoleId.value === 'pmc')
const isInventory = computed(() => demoRoleId.value === 'inventory')

// ── PMC：生产计划甘特图 ──
const ganttChartRef = ref(null)
let ganttChartInstance = null

/** 时间轴起点（本地演示日） */
const GANTT_ORIGIN = new Date(2026, 6, 14) // 2026-07-14
const GANTT_DAY_MS = 24 * 60 * 60 * 1000
const ganttDates = ['07-14', '07-15', '07-16', '07-17', '07-18']

const STAGE_COLORS = {
  ok: '#5a8f72',
  active: '#4b7a5f',
  planned: '#9bb5a6',
  wait: '#d4a017',
  risk: '#c45c45',
  done: '#7dad8e'
}

const ganttOrders = ref([
  {
    id: 'WO20260708001',
    label: 'WO001',
    product: '电缆接头组件',
    currentStage: '装配',
    planFinish: '07-16',
    risk: '防爆接插件库存不足',
    impact: '延期2天',
    advice: '提前采购',
    delayDays: 2,
    level: 'risk',
    stages: [
      { name: '装配', start: 0, end: 2, status: 'active' },
      { name: '测试', start: 2, end: 3.2, status: 'planned' },
      { name: '入库', start: 3.2, end: 4.2, status: 'planned' }
    ]
  },
  {
    id: 'WO20260708002',
    label: 'WO002',
    product: '高压密封组件',
    currentStage: '加工',
    planFinish: '07-17',
    risk: '',
    impact: '按期',
    advice: '保持节奏',
    delayDays: 0,
    level: 'ok',
    stages: [
      { name: '加工', start: 1, end: 3.2, status: 'active' },
      { name: '检验', start: 3.2, end: 4.5, status: 'planned' }
    ]
  },
  {
    id: 'WO20260708003',
    label: 'WO003',
    product: '井下探头壳体',
    currentStage: '等待物料',
    planFinish: '07-16',
    risk: '密封圈批次未到货',
    impact: '延期3天 · 影响交付',
    advice: '催采购 / 启用替代料',
    delayDays: 3,
    level: 'wait',
    stages: [
      { name: '等待物料', start: 1.5, end: 3.5, status: 'wait' }
    ]
  },
  {
    id: 'WO20260709004',
    label: 'WO004',
    product: '通讯板总成',
    currentStage: '测试',
    planFinish: '07-15',
    risk: '测试工位排队',
    impact: '延期1天',
    advice: '协调加开夜班工位',
    delayDays: 1,
    level: 'warn',
    stages: [
      { name: '装配', start: 0, end: 1.2, status: 'done' },
      { name: '测试', start: 1.2, end: 2.8, status: 'active' },
      { name: '入库', start: 2.8, end: 3.5, status: 'planned' }
    ]
  },
  {
    id: 'WO20260709005',
    label: 'WO005',
    product: '耐磨套组件',
    currentStage: '入库',
    planFinish: '07-18',
    risk: '',
    impact: '按期',
    advice: '正常推进',
    delayDays: 0,
    level: 'ok',
    stages: [
      { name: '加工', start: 0.5, end: 2, status: 'done' },
      { name: '检验', start: 2, end: 3.5, status: 'active' },
      { name: '入库', start: 3.5, end: 4.8, status: 'planned' }
    ]
  },
  {
    id: 'WO20260710006',
    label: 'WO006',
    product: '防爆接插件总成',
    currentStage: '等待物料',
    planFinish: '07-17',
    risk: '防爆接插件库存不足',
    impact: '延期2天 · 阻塞 WO001',
    advice: '优先补料并锁定库存',
    delayDays: 2,
    level: 'risk',
    stages: [
      { name: '领料', start: 2, end: 2.8, status: 'wait' },
      { name: '装配', start: 2.8, end: 4.2, status: 'planned' }
    ]
  }
])

const selectedOrderId = ref('')
const selectedOrder = computed(() =>
  ganttOrders.value.find((o) => o.id === selectedOrderId.value) || null
)

const ganttSummary = computed(() => {
  const list = ganttOrders.value
  return {
    total: list.length,
    delayed: list.filter((o) => o.delayDays > 0).length,
    materialWait: list.filter((o) => o.level === 'wait' || o.currentStage.includes('等待')).length,
    deliveryRisk: list.filter((o) => o.impact.includes('交付') || o.delayDays >= 2).length
  }
})

// ── 甘特图下方：辅助可视化 ──

/** 各阶段在制工单分布 */
const STAGE_ORDER = ['等待物料', '领料', '加工', '装配', '测试', '检验', '入库']
const stageDistribution = computed(() => {
  const map = new Map()
  ganttOrders.value.forEach((o) => {
    const key = o.currentStage
    map.set(key, (map.get(key) || 0) + 1)
  })
  const total = ganttOrders.value.length || 1
  return STAGE_ORDER
    .filter((s) => map.has(s))
    .map((stage) => {
      const count = map.get(stage)
      const isWait = stage.includes('等待')
      return {
        stage,
        count,
        pct: Math.round((count / total) * 100),
        tone: isWait ? 'wait' : stage === '入库' ? 'done' : 'active'
      }
    })
})

/** 交付风险排行（按延期天数降序） */
const delayRanking = computed(() =>
  ganttOrders.value
    .filter((o) => o.delayDays > 0)
    .slice()
    .sort((a, b) => b.delayDays - a.delayDays)
)
const maxDelay = computed(() =>
  Math.max(1, ...delayRanking.value.map((o) => o.delayDays))
)

/** 关键物料缺口（演示数据，对齐工单风险） */
const materialGaps = ref([
  { code: 'M-HV-CONN', name: '防爆接插件', need: 120, stock: 45, eta: '07-17', impact: 2 },
  { code: 'M-SEAL-RING', name: '高压密封圈', need: 200, stock: 60, eta: '07-18', impact: 1 },
  { code: 'M-PROBE-SHELL', name: '探头壳体', need: 80, stock: 12, eta: '待定', impact: 1 }
])
function gapPct(m) {
  const need = Number(m.need) || 1
  return Math.min(100, Math.round((Number(m.stock) / need) * 100))
}
function gapLevel(m) {
  const pct = gapPct(m)
  if (pct < 30) return 'risk'
  if (pct < 60) return 'warn'
  return 'ok'
}

/** 每日在制负荷（同一天有多少道工序进行中） */
const dailyLoad = computed(() => {
  return ganttDates.map((label, dayIdx) => {
    let count = 0
    ganttOrders.value.forEach((o) => {
      o.stages.forEach((st) => {
        if (st.start < dayIdx + 1 && st.end > dayIdx) count += 1
      })
    })
    return { label, count }
  })
})
const maxDailyLoad = computed(() =>
  Math.max(1, ...dailyLoad.value.map((d) => d.count))
)

function dayToTime(dayOffset) {
  return GANTT_ORIGIN.getTime() + dayOffset * GANTT_DAY_MS
}

function levelLabel(level) {
  if (level === 'risk') return '高风险'
  if (level === 'wait') return '等料'
  if (level === 'warn') return '关注'
  return '正常'
}

function selectOrder(order) {
  selectedOrderId.value = order?.id || ''
  highlightGanttRow()
}

function closeOrderPanel() {
  selectedOrderId.value = ''
  highlightGanttRow()
}

function buildGanttSeriesData() {
  const categories = ganttOrders.value.map((o) => o.label)
  const data = []
  ganttOrders.value.forEach((order, yIndex) => {
    order.stages.forEach((stage, sIndex) => {
      data.push({
        name: stage.name,
        value: [
          yIndex,
          dayToTime(stage.start),
          dayToTime(stage.end),
          stage.name,
          order.id,
          stage.status
        ],
        itemStyle: {
          color: STAGE_COLORS[stage.status] || STAGE_COLORS.planned,
          borderRadius: 4
        },
        orderId: order.id,
        stageIndex: sIndex
      })
    })
  })
  return { categories, data }
}

function renderGanttChart() {
  const el = ganttChartRef.value
  if (!el) return

  ganttChartInstance = echarts.getInstanceByDom(el) || echarts.init(el)
  const { categories, data } = buildGanttSeriesData()
  const start = dayToTime(0)
  const end = dayToTime(ganttDates.length)

  // 进行中 / 等待物料的工序 → 涟漪动画点
  const pulseData = []
  ganttOrders.value.forEach((order) => {
    order.stages.forEach((st) => {
      if (st.status === 'active') {
        pulseData.push({
          value: [dayToTime(st.end), order.label],
          itemStyle: { color: '#4b7a5f' }
        })
      } else if (st.status === 'wait') {
        pulseData.push({
          value: [dayToTime((st.start + st.end) / 2), order.label],
          itemStyle: { color: '#c45c45' }
        })
      }
    })
  })

  ganttChartInstance.setOption({
    animationDuration: 420,
    animationEasing: 'cubicOut',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(42, 58, 51, 0.92)',
      borderWidth: 0,
      textStyle: { color: '#fff', fontSize: 12 },
      formatter(params) {
        const v = params.value || []
        const order = ganttOrders.value[v[0]]
        if (!order) return ''
        const riskLine = order.risk ? `<br/>风险：⚠ ${order.risk}` : ''
        return `<b>${order.id}</b><br/>${order.product}<br/>阶段：${v[3]}${riskLine}`
      }
    },
    grid: {
      left: 80,
      right: selectedOrderId.value ? 12 : 24,
      top: 28,
      bottom: 22,
      containLabel: false
    },
    xAxis: {
      type: 'time',
      min: start,
      max: end,
      interval: GANTT_DAY_MS,
      axisLine: { lineStyle: { color: '#d5ddd8' } },
      axisTick: { show: false },
      splitLine: {
        show: true,
        lineStyle: { color: '#eef2ef', type: 'dashed' }
      },
      axisLabel: {
        color: '#7d8983',
        fontSize: 11,
        formatter(value) {
          const d = new Date(value)
          const mm = String(d.getMonth() + 1).padStart(2, '0')
          const dd = String(d.getDate()).padStart(2, '0')
          return `${mm}-${dd}`
        }
      }
    },
    yAxis: {
      type: 'category',
      data: categories,
      inverse: true,
      triggerEvent: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: '#31433c',
        fontSize: 12,
        fontWeight: 600,
        formatter(value) {
          const order = ganttOrders.value.find((o) => o.label === value)
          if (!order) return value
          if (order.level === 'risk' || order.level === 'wait') return `{warn|${value}}`
          return value
        },
        rich: {
          warn: { color: '#b4553f', fontWeight: 700, fontSize: 12 }
        }
      }
    },
    series: [
      {
        type: 'custom',
        renderItem(params, api) {
          const categoryIndex = api.value(0)
          const startVal = api.coord([api.value(1), categoryIndex])
          const endVal = api.coord([api.value(2), categoryIndex])
          const barHeight = Math.min(14, api.size([0, 1])[1] * 0.24)
          const x = startVal[0]
          const y = startVal[1] - barHeight / 2
          const width = Math.max(endVal[0] - startVal[0], 4)
          const status = api.value(5)
          const stageName = api.value(3)
          const fill = STAGE_COLORS[status] || STAGE_COLORS.planned
          const isWait = status === 'wait' || status === 'risk'

          return {
            type: 'group',
            children: [
              {
                type: 'rect',
                shape: { x, y, width, height: barHeight, r: 3 },
                style: {
                  fill,
                  cursor: 'pointer',
                  shadowBlur: isWait ? 6 : 0,
                  shadowColor: isWait ? 'rgba(196, 92, 69, 0.26)' : 'transparent'
                },
                emphasis: {
                  style: { fill, shadowBlur: 8, shadowColor: 'rgba(48, 76, 64, 0.22)' }
                }
              },
              {
                type: 'text',
                style: {
                  text: isWait ? `${stageName} ⚠` : stageName,
                  x: x + width / 2,
                  y: y + barHeight / 2 + 8,
                  fill: isWait ? '#b4553f' : '#8b958f',
                  fontSize: 10,
                  align: 'center',
                  verticalAlign: 'top'
                }
              }
            ]
          }
        },
        encode: { x: [1, 2], y: 0 },
        data,
        clip: true
      },
      {
        type: 'effectScatter',
        coordinateSystem: 'cartesian2d',
        symbolSize: 7,
        showEffectOn: 'render',
        rippleEffect: { scale: 3.2, brushType: 'stroke', period: 3 },
        zlevel: 1,
        silent: true,
        tooltip: { show: false },
        data: pulseData
      }
    ]
  }, true)

  ganttChartInstance.off('click')
  ganttChartInstance.on('click', (params) => {
    if (params?.componentType === 'yAxis') {
      const order = ganttOrders.value.find((o) => o.label === params.value)
      if (order) selectOrder(order)
      return
    }
    const orderId = params?.data?.orderId || params?.value?.[4]
    if (!orderId) return
    const order = ganttOrders.value.find((o) => o.id === orderId)
    if (order) selectOrder(order)
  })

  ganttChartInstance.resize()
  highlightGanttRow()
}

function highlightGanttRow() {
  if (!ganttChartInstance) return
  const idx = ganttOrders.value.findIndex((o) => o.id === selectedOrderId.value)
  ganttChartInstance.dispatchAction({ type: 'downplay', seriesIndex: 0 })
  if (idx >= 0) {
    const indexes = []
    let cursor = 0
    ganttOrders.value.forEach((order, oi) => {
      order.stages.forEach(() => {
        if (oi === idx) indexes.push(cursor)
        cursor += 1
      })
    })
    indexes.forEach((dataIndex) => {
      ganttChartInstance.dispatchAction({ type: 'highlight', seriesIndex: 0, dataIndex })
    })
  }
  ganttChartInstance.setOption({
    grid: { right: selectedOrderId.value ? 12 : 28 }
  })
  nextTick(() => ganttChartInstance?.resize())
}

watch(selectedOrderId, () => {
  nextTick(() => highlightGanttRow())
})

function resizeGantt() {
  ganttChartInstance?.resize()
}

// ── Inventory analytics: data from API ──
const { doLogin } = useInventory()

const filterWarehouse = ref('')
const filterTimeRange = ref('7d')

const kpi = reactive({
  itemCount: 0,
  totalValue: 0,
  totalValueAvailable: false,
  turnoverRate7d: 0,
  frozenRatio: 0,
  safetyComplianceRate: 0,
  stocktakeDifferenceCount: 0,
  pendingAdjustmentCount: 0,
})

const turnoverTrend = ref([])
const slowMovingList = ref([])
const safetyComplianceList = ref([])
const zoneAccuracyList = ref([])
const adjustmentTrend = ref([])

const warehouseFilters = ref([])
const loading = ref(false)
const loadError = ref('')

const invReports = ref([
  ['生成库存分析报告', '生成库存周转与结构分析报告。'],
  ['生成安全库存预警草稿', '生成安全库存预警与补货建议草稿，确认后可创建正式预警。'],
  ['生成盘点建议', '基于差异率与准确率生成重点盘点建议。'],
  ['导出库存数据', '导出库存明细与库位占用汇总数据。'],
])

async function loadAnalytics() {
  loading.value = true
  loadError.value = ''

  if (!getAuthToken()) {
    try { await doLogin('admin', 'admin123') } catch { loading.value = false; return }
  }

  try {
    const payload = await apiGet('/inventory/analytics/dashboard', {
      warehouseId: filterWarehouse.value || undefined
    })

    Object.assign(kpi, payload.kpi ?? {})

    turnoverTrend.value = Array.isArray(payload.turnoverTrend) ? payload.turnoverTrend : []

    slowMovingList.value = Array.isArray(payload.slowMoving) ? payload.slowMoving : []

    safetyComplianceList.value = Array.isArray(payload.safetyCompliance) ? payload.safetyCompliance : []

    zoneAccuracyList.value = Array.isArray(payload.zoneAccuracy) ? payload.zoneAccuracy : []

    adjustmentTrend.value = Array.isArray(payload.adjustmentTrend) ? payload.adjustmentTrend : []

    const warehouseFilter = (Array.isArray(payload.filters) ? payload.filters : []).find(f => f.field === 'warehouseId')
    warehouseFilters.value = warehouseFilter?.options ?? []

    console.log('库存分析数据：', payload)
    console.log('异常物料：', slowMovingList.value)
    console.log('安全库存：', safetyComplianceList.value)
    console.log('库位准确率：', zoneAccuracyList.value)

    await nextTick()
    requestAnimationFrame(() => {
      renderTurnoverChart()
      renderAdjustmentChart()
    })
  } catch (e) {
    console.error('库存分析加载失败：', e)
    loadError.value = e?.message || '库存分析数据加载失败'
  } finally {
    loading.value = false
  }
}

// ── Report dialog ──
const reportDialogVisible = ref(false)
const reportDialogTitle = ref('')
const reportDialogContent = ref('')
const reportLoading = ref({ report: false, alert: false, suggestion: false, export: false })

const reportContents = computed(() => ({
  report: [
    '一、库存总览',
    `库存物料数：${kpi.itemCount || '—'} 种`,
    `近7日平均周转率：${kpi.turnoverRate7d || '—'}`,
    `冻结占比：${kpi.frozenRatio || '—'}% | 安全库存达标率：${kpi.safetyComplianceRate || '—'}%`,
    '',
    '二、库区准确率',
    ...zoneAccuracyList.value.map(z => `${z.zone} ${z.name}：${z.pct}%（${z.accurate}/${z.total}）`),
    '',
    '三、慢动物料',
    ...slowMovingList.value.map(s => `${s.itemName}（${s.locationArea || '?'}区）库存 ${s.quantity}，${s.stallDays >= 999 ? '>30' : s.stallDays}天未出库 [${s.anomalyType}]`),
    '',
    '四、AI 分析建议',
  ],
  alert: [
    '一、安全库存概览',
    `达标率：${kpi.safetyComplianceRate || '—'}%`,
    '',
    '二、风险库区',
    ...safetyComplianceList.value.filter(c => c.pct < 90).map(c => `${c.zone} ${c.name} 达标率仅 ${c.pct}%`),
    '',
    '三、AI 预警建议（草稿，需确认后生效）',
    '1. 建议对低达标率库区全面复盘',
    '2. 高周转物料建议提高安全库存系数',
    '3. 低周转备件按季度评估是否继续备库',
  ],
  suggestion: [
    '一、重点盘点库区（建议）',
    ...zoneAccuracyList.value.filter(z => z.pct < 95).map(z => `${z.zone} ${z.name}（准确率 ${z.pct}%，差异 ${z.total - z.accurate} 项）`),
    '',
    '二、建议盘点物料',
    ...slowMovingList.value.map(s => `${s.itemName} — ${s.anomalyType}，建议核实实物状态`),
    '',
    '三、盘点计划建议',
    '1. 低准确率库区：建议本周全盘',
    '2. 高价值物料：建议重点抽盘',
    '3. 盘点计划确认后请前往「库存盘点」页面创建正式盘点任务',
  ]
}))

function openReport(type) {
  const key = type === 'stock' ? 'report' : type === 'alert' ? 'alert' : 'suggestion'
  const loadingKey = type === 'stock' ? 'report' : type === 'alert' ? 'alert' : 'suggestion'
  reportLoading.value[loadingKey] = true
  setTimeout(() => {
    reportDialogTitle.value = invReports[type === 'stock' ? 0 : type === 'alert' ? 1 : 2][0]
    reportDialogContent.value = reportContents[key].join('\n')
    reportLoading.value[loadingKey] = false
    reportDialogVisible.value = true
  }, 600)
}

function exportStockData() {
  reportLoading.value.export = true
  setTimeout(() => {
    const rows = [['物料编码', '物料名称', '异常类型', '库存', '停滞天数', '建议动作']]
    slowMovingList.value.forEach(s => {
      rows.push([s.itemCode, s.itemName, s.anomalyType, String(s.quantity), String(s.stallDays >= 999 ? '>30' : s.stallDays), s.suggestion])
    })
    const csv = rows.map(r => r.join(',')).join('\n')
    const bom = '\uFEFF'
    const blob = new Blob([bom + csv], { type: 'text/csv;charset=utf-8;' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `库存数据_${new Date().toLocaleDateString('zh-CN')}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
    reportLoading.value.export = false
  }, 500)
}

function closeReportDialog() {
  reportDialogVisible.value = false
}

// ── Inventory ECharts ──
const turnoverChartRef = ref(null)
const adjustmentChartRef = ref(null)
let turnoverChartInstance = null
let adjustmentChartInstance = null

function renderTurnoverChart() {
  const el = turnoverChartRef.value
  if (!el) { console.warn('库存周转率图表容器不存在'); return }

  turnoverChartInstance = echarts.getInstanceByDom(el) || echarts.init(el)

  const source = Array.isArray(turnoverTrend.value) ? turnoverTrend.value : []

  turnoverChartInstance.setOption({
    color: ['#5470c6', '#91cc75'],
    tooltip: { trigger: 'axis' },
    legend: {
      top: 4, right: 24, itemWidth: 22, itemHeight: 10, itemGap: 18,
      textStyle: { fontSize: 12, color: '#5f7268' },
      data: ['日周转率', '出库动销'],
    },
    grid: { left: 18, right: 28, top: 48, bottom: 28, containLabel: true },
    xAxis: {
      type: 'category', data: source.map(item => item.day),
      axisTick: { alignWithLabel: true },
      axisLine: { lineStyle: { color: '#d5ddd8' } },
      axisLabel: { color: '#7d8983', fontSize: 11 },
    },
    yAxis: [
      {
        type: 'value', name: '周转率', nameLocation: 'end', nameGap: 10, min: 0,
        nameTextStyle: { color: '#8b958f', fontSize: 11 },
        axisLabel: { formatter: value => Number(value).toFixed(3), color: '#8b958f', fontSize: 11 },
        splitLine: { lineStyle: { color: '#eef2ef' } },
      },
      {
        type: 'value', name: '', min: 0, minInterval: 10,
        axisLabel: { formatter: '{value}', color: '#8b958f', fontSize: 11 },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '日周转率', type: 'line', smooth: true,
        symbol: 'circle', symbolSize: 7,
        lineStyle: { width: 2 },
        data: source.map(item => Number(item.rate ?? 0)),
      },
      {
        name: '出库动销', type: 'bar', yAxisIndex: 1, barWidth: 22,
        itemStyle: { borderRadius: [3, 3, 0, 0] },
        data: source.map(item => Number(item.move ?? 0)),
      },
    ],
  }, true)

  turnoverChartInstance.resize()
}

function renderAdjustmentChart() {
  const el = adjustmentChartRef.value
  if (!el) { console.warn('月度调整单图表容器不存在'); return }

  console.log('调整图表容器尺寸：', el.clientWidth, el.clientHeight)

  adjustmentChartInstance = echarts.getInstanceByDom(el) || echarts.init(el)

  const source = Array.isArray(adjustmentTrend.value) ? adjustmentTrend.value : []

  adjustmentChartInstance.setOption({
    color: ['#5470c6'],
    tooltip: { trigger: 'axis' },
    grid: { left: 24, right: 24, top: 28, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: source.map(item => item.month),
      axisLine: { lineStyle: { color: '#d5ddd8' } },
      axisLabel: { color: '#7d8983', fontSize: 12 },
    },
    yAxis: {
      type: 'value', min: 0, minInterval: 1,
      axisLabel: { color: '#8b958f', fontSize: 11 },
      splitLine: { lineStyle: { color: '#eef2ef' } },
    },
    series: [{
      name: '调整单数量',
      type: 'bar',
      barMaxWidth: 56,
      itemStyle: { borderRadius: [4, 4, 0, 0] },
      data: source.map(item => Number(item.count ?? 0)),
    }],
  }, true)

  adjustmentChartInstance.resize()
}

function resizeCharts() {
  turnoverChartInstance?.resize()
  adjustmentChartInstance?.resize()
  resizeGantt()
}

onMounted(async () => {
  if (isPmc.value) {
    await nextTick()
    requestAnimationFrame(() => renderGanttChart())
    window.addEventListener('resize', resizeGantt)
    return
  }
  if (!isInventory.value) {
    router.replace('/module/' + encodeURIComponent('数据分析'))
    return
  }
  await loadAnalytics()
  window.addEventListener('resize', resizeCharts)
})

onActivated(() => {
  nextTick(() => {
    if (isPmc.value) {
      renderGanttChart()
      resizeGantt()
      return
    }
    renderTurnoverChart()
    renderAdjustmentChart()
    resizeCharts()
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  window.removeEventListener('resize', resizeGantt)
  turnoverChartInstance?.dispose()
  adjustmentChartInstance?.dispose()
  ganttChartInstance?.dispose()
  turnoverChartInstance = null
  adjustmentChartInstance = null
  ganttChartInstance = null
})

// ── Helper functions ──
const lastUpdatedTime = ref('')

function formatQuantity(value) {
  const number = Number(value ?? 0)
  if (!Number.isFinite(number)) return '-'
  return Number.isInteger(number) ? String(number) : number.toFixed(2)
}

function formatPercent(value) {
  const number = Number(value ?? 0)
  if (!Number.isFinite(number)) return '0.0%'
  return `${number.toFixed(1)}%`
}

function getRateClass(value) {
  const number = Number(value ?? 0)
  if (number >= 80) return 'rate-good'
  if (number >= 50) return 'rate-warning'
  return 'rate-danger'
}

function getRateWidth(value) {
  const number = Number(value ?? 0)
  if (!Number.isFinite(number)) return '0%'
  return `${Math.max(0, Math.min(number, 100))}%`
}

function getAnomalyClass(item) {
  const type = item.anomalyType || ''
  if (type.includes('冻结')) return 'status-tag danger'
  if (type.includes('慢动') || type.includes('零库存')) return 'status-tag warning'
  return 'status-tag normal'
}

function needsReplenish(item) {
  const type = item.anomalyType || ''
  const qty = Number(item.quantity)
  return type.includes('零库存') || (Number.isFinite(qty) && qty <= 0)
}

function createReplenishFromAnalytics(item = null, extra = {}) {
  const query = {
    openCreate: '1',
    from: 'analytics',
    reason: '数据分析触发补货',
    ...extra,
  }
  if (item) {
    query.itemCode = item.itemCode || ''
    query.batchNo = item.batchNo || ''
    const qty = Number(item.quantity)
    const suggest = Number.isFinite(qty) && qty > 0 ? Math.max(10, Math.ceil(qty * 0.5)) : 20
    query.suggestQty = String(suggest)
    query.availableQty = String(Number.isFinite(qty) ? Math.max(0, qty) : 0)
  }
  router.push({ path: menuToPath('补货管理'), query })
}

function createReplenishFromSafety(zoneItem) {
  const gap = Math.max(0, Number(zoneItem?.total || 0) - Number(zoneItem?.compliant || 0))
  createReplenishFromAnalytics(null, {
    reason: `安全库存未达标（${zoneItem?.zone || zoneItem?.name || '仓库'}）`,
    suggestQty: String(gap > 0 ? gap : 10),
  })
}

function formatMoney(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '—'
  return `¥${number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function exportInventoryData() { exportStockData() }
function generateReport() { openReport('stock') }
function exportAnomalyData() { exportStockData() }
</script>

<template>
  <!-- ── PMC：生产计划甘特图 ── -->
  <section v-if="isPmc" class="pmc-gantt-page">
    <header class="plan-header">
      <div>
        <h1>数据分析</h1>
      </div>
      <div class="gantt-legend">
        <span class="lg-active">进行中</span>
        <span class="lg-planned">待执行</span>
        <span class="lg-wait">等待物料</span>
        <span class="lg-risk">高风险</span>
      </div>
    </header>

    <div class="gantt-meta">
      <span>工单 {{ ganttSummary.total }}</span>
      <span class="warn">延期 {{ ganttSummary.delayed }}</span>
      <span class="warn">等料 {{ ganttSummary.materialWait }}</span>
      <span class="danger">影响交付 {{ ganttSummary.deliveryRisk }}</span>
      <em>点击工单条查看详情</em>
    </div>

    <div :class="['gantt-layout', { 'has-panel': !!selectedOrder }]">
      <section class="gantt-card">
        <div class="gantt-card-head">
          <h2>生产计划甘特图</h2>
          <small>{{ ganttDates[0] }} — {{ ganttDates[ganttDates.length - 1] }}</small>
        </div>
        <div ref="ganttChartRef" class="gantt-chart"></div>
        <ul class="gantt-wo-fallback">
          <li
            v-for="order in ganttOrders"
            :key="order.id"
            :class="['gantt-wo-chip', order.level, { active: order.id === selectedOrderId }]"
            @click="selectOrder(order)"
          >
            <strong>{{ order.label }}</strong>
            <span>{{ order.product }}</span>
            <i v-if="order.delayDays > 0">延期{{ order.delayDays }}天</i>
          </li>
        </ul>
      </section>

      <aside v-if="selectedOrder" class="gantt-detail-panel">
        <button type="button" class="gantt-detail-close" aria-label="关闭" @click="closeOrderPanel">&times;</button>
        <p class="gantt-detail-id">{{ selectedOrder.id }}</p>
        <dl class="gantt-detail-dl">
          <div>
            <dt>产品</dt>
            <dd>{{ selectedOrder.product }}</dd>
          </div>
          <div>
            <dt>当前阶段</dt>
            <dd>
              <em :class="['stage-pill', selectedOrder.level]">{{ selectedOrder.currentStage }}</em>
            </dd>
          </div>
          <div>
            <dt>计划完成</dt>
            <dd>{{ selectedOrder.planFinish }}</dd>
          </div>
          <div>
            <dt>风险</dt>
            <dd :class="{ danger: !!selectedOrder.risk }">
              <template v-if="selectedOrder.risk">⚠ {{ selectedOrder.risk }}</template>
              <template v-else>暂无</template>
            </dd>
          </div>
          <div>
            <dt>影响</dt>
            <dd :class="{ danger: selectedOrder.delayDays > 0 }">{{ selectedOrder.impact }}</dd>
          </div>
          <div>
            <dt>建议</dt>
            <dd class="advice">{{ selectedOrder.advice }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd><i :class="['level-tag', selectedOrder.level]">{{ levelLabel(selectedOrder.level) }}</i></dd>
          </div>
        </dl>
        <div class="gantt-stage-list">
          <span class="gantt-stage-title">阶段节奏</span>
          <div v-for="stage in selectedOrder.stages" :key="stage.name" class="gantt-stage-row">
            <i :style="{ background: STAGE_COLORS[stage.status] || STAGE_COLORS.planned }"></i>
            <strong>{{ stage.name }}</strong>
            <em>{{ stage.status === 'wait' ? '等待中' : stage.status === 'active' ? '进行中' : stage.status === 'done' ? '已完成' : '待执行' }}</em>
          </div>
        </div>
      </aside>
    </div>

    <!-- ── 甘特图下方：辅助可视化 ── -->
    <div class="gantt-insights">
      <!-- 交付风险排行 -->
      <section class="insight-card span-5">
        <div class="insight-head">
          <h3>交付风险排行</h3>
          <small>按延期天数</small>
        </div>
        <ul class="delay-rank">
          <li
            v-for="order in delayRanking"
            :key="order.id"
            :class="['delay-row', order.level, { active: order.id === selectedOrderId }]"
            @click="selectOrder(order)"
          >
            <span class="delay-wo">
              <strong>{{ order.label }}</strong>
              <em>{{ order.product }}</em>
            </span>
            <span class="delay-bar-wrap">
              <i class="delay-bar" :style="{ width: `${(order.delayDays / maxDelay) * 100}%` }"></i>
            </span>
            <b class="delay-days">+{{ order.delayDays }}天</b>
          </li>
          <li v-if="!delayRanking.length" class="delay-empty">当前无延期工单</li>
        </ul>
      </section>

      <!-- 在制阶段分布 -->
      <section class="insight-card span-4">
        <div class="insight-head">
          <h3>在制阶段分布</h3>
          <small>共 {{ ganttSummary.total }} 单</small>
        </div>
        <div class="stage-dist">
          <div v-for="s in stageDistribution" :key="s.stage" class="stage-dist-row">
            <span class="stage-dist-name">{{ s.stage }}</span>
            <span class="stage-dist-track">
              <i :class="['stage-dist-fill', s.tone]" :style="{ width: `${s.pct}%` }"></i>
            </span>
            <b class="stage-dist-count">{{ s.count }}</b>
          </div>
        </div>
        <div class="daily-load">
          <span class="daily-load-title">每日在制工序负荷</span>
          <div class="daily-load-cols">
            <div v-for="d in dailyLoad" :key="d.label" class="daily-load-col">
              <i
                class="daily-load-bar"
                :style="{ height: `${Math.max(6, (d.count / maxDailyLoad) * 46)}px` }"
              ></i>
              <b>{{ d.count }}</b>
              <em>{{ d.label }}</em>
            </div>
          </div>
        </div>
      </section>

      <!-- 关键物料缺口 -->
      <section class="insight-card span-3">
        <div class="insight-head">
          <h3>关键物料缺口</h3>
          <small>影响齐套</small>
        </div>
        <ul class="mat-gap">
          <li v-for="m in materialGaps" :key="m.code" :class="['mat-gap-row', gapLevel(m)]">
            <div class="mat-gap-top">
              <strong>{{ m.name }}</strong>
              <span class="mat-gap-code">{{ m.code }}</span>
            </div>
            <div class="mat-gap-track">
              <i :class="['mat-gap-fill', gapLevel(m)]" :style="{ width: `${gapPct(m)}%` }"></i>
            </div>
            <div class="mat-gap-foot">
              <span>{{ m.stock }} / {{ m.need }}</span>
              <em>到货 {{ m.eta }} · 影响 {{ m.impact }} 单</em>
            </div>
          </li>
        </ul>
      </section>
    </div>
  </section>

  <!-- ── Inventory analytics ── -->
  <section v-else-if="isInventory" class="inv-analytics">
    <div class="inv-dashboard">
      <!-- Header -->
      <header class="inv-header-new">
        <div class="inv-header-left">
          <h1>库存数据分析</h1>
          <p>库存结构、周转效率、库位准确率与安全库存合规分析</p>
        </div>
        <div class="inv-header-right">
          <span class="inv-updated-badge">更新于 {{ lastUpdatedTime }}</span>
          <button class="inv-refresh-btn" @click="loadAnalytics" :disabled="loading">刷新</button>
        </div>
      </header>

      <!-- Filters -->
      <div class="inv-filters">
        <div class="inv-filter-item">
          <label>仓库</label>
          <select v-model="filterWarehouse" @change="loadAnalytics">
            <option value="">全部仓库</option>
            <option v-for="w in warehouseFilters" :key="w.value" :value="w.value">{{ w.label }}</option>
          </select>
        </div>
        <div class="inv-filter-item">
          <label>时间范围</label>
          <select v-model="filterTimeRange">
            <option value="7d">近 7 天</option>
            <option value="30d">近 30 天</option>
            <option value="month">本月</option>
          </select>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="inv-loading">
        <div class="inv-loading-spinner"></div>
        <span>正在加载分析数据…</span>
      </div>

      <!-- Error -->
      <div v-else-if="loadError" class="inv-loading">
        <span style="color:#b4553f">{{ loadError }}</span>
      </div>

      <template v-else>
        <!-- KPI 卡片 -->
        <div class="inv-kpis">
          <div class="inv-kpi-card">
            <div class="inv-kpi-value">{{ kpi.itemCount.toLocaleString() }}<span class="inv-kpi-unit">种</span></div>
            <div class="inv-kpi-label">库存物料数</div>
          </div>
          <div class="inv-kpi-card">
            <div class="inv-kpi-value">{{ kpi.totalValue == null ? '—' : formatMoney(kpi.totalValue) }}</div>
            <div class="inv-kpi-label">库存总价值</div>
          </div>
          <div class="inv-kpi-card">
            <div class="inv-kpi-value">{{ kpi.turnoverRate7d }}</div>
            <div class="inv-kpi-label">近7日平均周转率</div>
          </div>
          <div :class="['inv-kpi-card', kpi.frozenRatio > 5 ? 'warn' : '']">
            <div class="inv-kpi-value">{{ kpi.frozenRatio }}<span class="inv-kpi-unit">%</span></div>
            <div class="inv-kpi-label">冻结占比</div>
          </div>
          <div :class="['inv-kpi-card', kpi.safetyComplianceRate < 90 ? 'warn' : '']">
            <div class="inv-kpi-value">{{ kpi.safetyComplianceRate }}<span class="inv-kpi-unit">%</span></div>
            <div class="inv-kpi-label">安全库存达标率</div>
          </div>
          <div :class="['inv-kpi-card', kpi.stocktakeDifferenceCount > 0 ? 'warn' : '']">
            <div class="inv-kpi-value">{{ kpi.stocktakeDifferenceCount }}<span class="inv-kpi-unit">项</span></div>
            <div class="inv-kpi-label">盘点差异数</div>
          </div>
          <div :class="['inv-kpi-card', kpi.pendingAdjustmentCount > 0 ? 'danger' : '']">
            <div class="inv-kpi-value">{{ kpi.pendingAdjustmentCount }}<span class="inv-kpi-unit">单</span></div>
            <div class="inv-kpi-label">待处理调整单</div>
          </div>
        </div>

        <!-- 主体：网格布局 -->
        <section class="dashboard-grid">
          <!-- 库存周转率 -->
          <article class="dashboard-card">
            <div class="card-header">
              <div>
                <h2>库存周转率趋势</h2>
                <p>近7天库存周转变化</p>
              </div>
            </div>

            <div class="analytics-chart-body">
              <div ref="turnoverChartRef" class="analytics-chart"></div>
            </div>
          </article>

          <!-- 慢动 / 异常物料 -->
          <article class="dashboard-card">
            <div class="card-header">
              <div>
                <h2>慢动 / 异常物料</h2>
                <p>冻结、慢动及零库存物料</p>
              </div>

              <div class="card-actions">
                <button type="button" @click="generateReport">生成报告</button>
                <button type="button" @click="exportAnomalyData">导出</button>
              </div>
            </div>

            <div class="card-scroll">
              <table class="dashboard-table anomaly-table">
                <thead>
                  <tr>
                    <th>物料编码</th>
                    <th>物料名称</th>
                    <th>异常类型</th>
                    <th class="number-cell">库存</th>
                    <th class="number-cell">停滞</th>
                    <th>建议</th>
                    <th>操作</th>
                  </tr>
                </thead>

                <tbody>
                  <tr v-for="item in slowMovingList" :key="`${item.itemCode}-${item.batchNo}`">
                    <td>{{ item.itemCode || '-' }}</td>
                    <td>{{ item.itemName || '-' }}</td>
                    <td>
                      <span class="status-tag" :class="getAnomalyClass(item)">
                        {{ item.anomalyType || '-' }}
                      </span>
                    </td>
                    <td class="number-cell">{{ formatQuantity(item.quantity) }}</td>
                    <td class="number-cell">{{ item.stallDays ?? 0 }} 天</td>
                    <td class="suggestion-cell">{{ item.suggestion || '-' }}</td>
                    <td>
                      <button
                        v-if="needsReplenish(item)"
                        type="button"
                        class="inline-action-btn"
                        @click="createReplenishFromAnalytics(item)"
                      >创建补货任务</button>
                      <span v-else class="muted-dash">—</span>
                    </td>
                  </tr>

                  <tr v-if="slowMovingList.length === 0">
                    <td colspan="7" class="empty-cell">暂无异常物料</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </article>

          <!-- 安全库存达标率 -->
          <article class="dashboard-card">
            <div class="card-header">
              <div>
                <h2>安全库存达标率</h2>
                <p>按仓库统计安全库存执行情况</p>
              </div>
              <div v-if="kpi.safetyComplianceRate < 90" class="card-actions">
                <button type="button" @click="createReplenishFromAnalytics(null, { reason: '安全库存达标率偏低', suggestQty: '20' })">
                  创建补货任务
                </button>
              </div>
            </div>

            <div class="card-scroll">
              <table class="dashboard-table">
                <thead>
                  <tr>
                    <th>仓库</th>
                    <th class="number-cell">物料总数</th>
                    <th class="number-cell">达标数</th>
                    <th>达标率</th>
                    <th>操作</th>
                  </tr>
                </thead>

                <tbody>
                  <tr v-for="item in safetyComplianceList" :key="item.zone">
                    <td>
                      <strong>{{ item.zone || '-' }}</strong>
                      <span class="sub-name">{{ item.name || '-' }}</span>
                    </td>
                    <td class="number-cell">{{ item.total ?? 0 }}</td>
                    <td class="number-cell">{{ item.compliant ?? 0 }}</td>
                    <td>
                      <div class="rate-cell">
                        <div class="rate-track">
                          <span :class="getRateClass(item.pct)" :style="{ width: getRateWidth(item.pct) }"></span>
                        </div>
                        <strong>{{ formatPercent(item.pct) }}</strong>
                      </div>
                    </td>
                    <td>
                      <button
                        v-if="Number(item.pct) < 90"
                        type="button"
                        class="inline-action-btn"
                        @click="createReplenishFromSafety(item)"
                      >创建补货任务</button>
                      <span v-else class="muted-dash">—</span>
                    </td>
                  </tr>

                  <tr v-if="safetyComplianceList.length === 0">
                    <td colspan="5" class="empty-cell">暂无安全库存数据</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </article>

          <!-- 库位账实准确率 -->
          <article class="dashboard-card">
            <div class="card-header">
              <div>
                <h2>库位账实准确率</h2>
                <p>依据盘点结果统计各库区准确率</p>
              </div>
            </div>

            <div class="card-scroll">
              <table class="dashboard-table">
                <thead>
                  <tr>
                    <th>库区</th>
                    <th class="number-cell">已盘点</th>
                    <th class="number-cell">准确</th>
                    <th>准确率</th>
                  </tr>
                </thead>

                <tbody>
                  <tr v-for="item in zoneAccuracyList" :key="item.zone">
                    <td>
                      <strong>{{ item.zone || '-' }}</strong>
                      <span class="sub-name">{{ item.name || '-' }}</span>
                    </td>
                    <td class="number-cell">{{ item.total ?? 0 }}</td>
                    <td class="number-cell">{{ item.accurate ?? 0 }}</td>
                    <td>
                      <div class="rate-cell">
                        <div class="rate-track">
                          <span :class="getRateClass(item.pct)" :style="{ width: getRateWidth(item.pct) }"></span>
                        </div>
                        <strong>{{ formatPercent(item.pct) }}</strong>
                      </div>
                    </td>
                  </tr>

                  <tr v-if="zoneAccuracyList.length === 0">
                    <td colspan="4" class="empty-cell">暂无库位准确率数据</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </article>

          <!-- 月度调整单趋势 -->
          <article class="dashboard-card">
            <div class="card-header">
              <div>
                <h2>月度调整单趋势</h2>
                <p>盘盈、盘亏及库存修正变化</p>
              </div>
            </div>

            <div class="analytics-chart-body">
              <div ref="adjustmentChartRef" class="analytics-chart"></div>
            </div>
          </article>
        </section>

        <!-- Report dialog -->
        <div v-if="reportDialogVisible" class="inv-report-overlay" @click.self="closeReportDialog">
          <div class="inv-report-dialog">
            <div class="inv-report-dialog-header">
              <h3>{{ reportDialogTitle }}</h3>
              <button class="inv-report-dialog-close" @click="closeReportDialog">&times;</button>
            </div>
            <div class="inv-report-dialog-body">
              <pre class="inv-report-content">{{ reportDialogContent }}</pre>
            </div>
            <div class="inv-report-dialog-footer">
              <button class="inv-report-dialog-btn" @click="closeReportDialog">关闭</button>
            </div>
          </div>
        </div>
      </template>
    </div>
  </section>

  <!-- ── Fallback ── -->
  <section v-else class="data-hint">当前角色无数据分析权限</section>
</template>

<style scoped>
.pmc-gantt-page,
.inv-analytics {
  width: 100%;
  max-width: none;
  margin: 0;
  font-family: inherit;
}

/* ── common ── */
.data-hint {
  padding: 40px;
  text-align: center;
  color: #8b958f;
  font-size: 14px;
}
.data-hint.danger { color: #b4553f; }

/* ══════ PMC 甘特图 ══════ */
.pmc-gantt-page {
  width: 100%;
  padding-bottom: 24px;
}

.plan-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.plan-header h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(22px, 2.6vw, 30px);
  font-weight: 600;
  color: #2a3a33;
}
.plan-header p {
  margin: 6px 0 0;
  color: #7d8983;
  font-size: 14px;
}

.gantt-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding-top: 6px;
  font-size: 12px;
  color: #6c7d75;
}
.gantt-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.gantt-legend span::before {
  content: '';
  width: 10px;
  height: 10px;
  border-radius: 3px;
}
.lg-active::before { background: #4b7a5f; }
.lg-planned::before { background: #9bb5a6; }
.lg-wait::before { background: #d4a017; }
.lg-risk::before { background: #c45c45; }

.gantt-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 16px;
  margin-bottom: 14px;
  font-size: 13px;
  color: #5f7268;
}
.gantt-meta .warn { color: #a97e2c; font-weight: 600; }
.gantt-meta .danger { color: #b4553f; font-weight: 600; }
.gantt-meta em {
  margin-left: auto;
  font-style: normal;
  color: #98a29c;
  font-size: 12px;
}

.gantt-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  align-items: start;
  transition: grid-template-columns 0.25s ease;
}
.gantt-layout.has-panel {
  grid-template-columns: minmax(0, 1fr) 300px;
}

.gantt-card {
  min-width: 0;
  background: #fff;
  border: 1px solid #e2eae5;
  border-radius: 12px;
  padding: 16px 16px 12px;
  box-shadow: 0 1px 3px rgba(48, 76, 64, 0.04);
}
.gantt-card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 4px;
}
.gantt-card-head h2 {
  margin: 0;
  font-size: 15px;
  color: #2c3b34;
  font-weight: 600;
}
.gantt-card-head small {
  color: #8b958f;
  font-size: 12px;
}
.gantt-chart {
  width: 100%;
  height: 300px;
  min-height: 240px;
}

.gantt-wo-fallback {
  list-style: none;
  margin: 8px 0 0;
  padding: 10px 0 0;
  border-top: 1px solid #eef2ef;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.gantt-wo-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid #e2eae5;
  background: #f7faf8;
  cursor: pointer;
  font-size: 12px;
  color: #5f7268;
  transition: background 0.15s, border-color 0.15s;
}
.gantt-wo-chip strong {
  color: #31433c;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.gantt-wo-chip i {
  font-style: normal;
  color: #b4553f;
  font-weight: 600;
}
.gantt-wo-chip:hover,
.gantt-wo-chip.active {
  background: #e8f1ec;
  border-color: #b7cfc2;
}
.gantt-wo-chip.risk,
.gantt-wo-chip.wait {
  border-color: #ecd0c9;
  background: #fdf6f4;
}
.gantt-wo-chip.warn {
  border-color: #ecd9c9;
  background: #fdf8ee;
}

.gantt-detail-panel {
  position: relative;
  background: #fff;
  border: 1px solid #e2eae5;
  border-radius: 12px;
  padding: 20px 18px 18px;
  box-shadow: 0 8px 28px rgba(48, 76, 64, 0.08);
  animation: gantt-panel-in 0.22s ease;
}
@keyframes gantt-panel-in {
  from { opacity: 0; transform: translateX(12px); }
  to { opacity: 1; transform: translateX(0); }
}
.gantt-detail-close {
  position: absolute;
  top: 10px;
  right: 12px;
  border: 0;
  background: transparent;
  font-size: 22px;
  color: #8b958f;
  cursor: pointer;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 4px;
}
.gantt-detail-close:hover {
  background: #f0f5f2;
  color: #33473f;
}
.gantt-detail-id {
  margin: 0 28px 16px 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 16px;
  font-weight: 700;
  color: #2a3a33;
  letter-spacing: 0.02em;
}
.gantt-detail-dl {
  margin: 0;
  display: grid;
  gap: 14px;
}
.gantt-detail-dl > div {
  display: grid;
  gap: 4px;
}
.gantt-detail-dl dt {
  font-size: 12px;
  color: #8b958f;
}
.gantt-detail-dl dd {
  margin: 0;
  font-size: 14px;
  color: #31433c;
  line-height: 1.45;
  font-weight: 600;
}
.gantt-detail-dl dd.danger { color: #b4553f; }
.gantt-detail-dl dd.advice { color: #4b7a5f; }

.stage-pill {
  display: inline-block;
  font-style: normal;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: #e8f1ec;
  color: #4b7a5f;
}
.stage-pill.risk,
.stage-pill.wait {
  background: #f6e0d9;
  color: #b4553f;
}
.stage-pill.warn {
  background: #f5edd7;
  color: #a97e2c;
}

.level-tag {
  font-style: normal;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}
.level-tag.ok { background: #e2eae5; color: #4b7a5f; }
.level-tag.warn { background: #f5edd7; color: #a97e2c; }
.level-tag.wait,
.level-tag.risk { background: #f6e0d9; color: #b4553f; }

.gantt-stage-list {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid #eef2ef;
  display: grid;
  gap: 8px;
}
.gantt-stage-title {
  font-size: 12px;
  color: #8b958f;
  margin-bottom: 2px;
}
.gantt-stage-row {
  display: grid;
  grid-template-columns: 10px 1fr auto;
  gap: 8px;
  align-items: center;
  font-size: 13px;
}
.gantt-stage-row i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}
.gantt-stage-row strong {
  color: #31433c;
  font-weight: 600;
}
.gantt-stage-row em {
  font-style: normal;
  color: #8b958f;
  font-size: 12px;
}

/* ── 甘特图下方辅助可视化 ── */
@keyframes insight-grow-x {
  from { transform: scaleX(0); }
  to { transform: scaleX(1); }
}
@keyframes insight-grow-y {
  from { transform: scaleY(0.05); }
  to { transform: scaleY(1); }
}
@keyframes insight-shimmer {
  from { transform: translateX(-100%); }
  to { transform: translateX(260%); }
}
@keyframes insight-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}
@keyframes load-bar-breathe {
  0%, 100% { transform: scaleY(1); }
  50% { transform: scaleY(0.82); }
}

.gantt-insights {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 14px;
  align-items: stretch;
}
.insight-card {
  min-width: 0;
  background: #fff;
  border: 1px solid #e2eae5;
  border-radius: 12px;
  padding: 14px 16px 16px;
  box-shadow: 0 1px 3px rgba(48, 76, 64, 0.04);
  display: flex;
  flex-direction: column;
}
.insight-card.span-5 { grid-column: span 5; }
.insight-card.span-4 { grid-column: span 4; }
.insight-card.span-3 { grid-column: span 3; }
.insight-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}
.insight-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #2c3b34;
}
.insight-head small {
  color: #98a29c;
  font-size: 12px;
}

/* 交付风险排行 */
.delay-rank {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}
.delay-row {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1.4fr) 52px;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border-radius: 8px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.delay-row:hover { background: #f4f8f6; }
.delay-row.active { background: #eef4f0; border-color: #b7cfc2; }
.delay-wo { display: flex; flex-direction: column; min-width: 0; }
.delay-wo strong {
  font-size: 13px;
  color: #31433c;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.delay-wo em {
  font-style: normal;
  font-size: 11px;
  color: #98a29c;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.delay-bar-wrap {
  position: relative;
  height: 8px;
  border-radius: 999px;
  background: #eef2ef;
  overflow: hidden;
}
.delay-bar {
  position: relative;
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #d4a017, #c45c45);
  transform-origin: left center;
  animation: insight-grow-x 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) both;
  overflow: hidden;
}
.delay-bar::after {
  content: '';
  position: absolute;
  inset: 0;
  width: 40%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.55), transparent);
  animation: insight-shimmer 2.4s ease-in-out infinite;
}
.delay-row.warn .delay-bar { background: linear-gradient(90deg, #e2c072, #d4a017); }
.delay-days {
  text-align: right;
  font-size: 13px;
  font-weight: 700;
  color: #b4553f;
}
.delay-row.warn .delay-days { color: #a97e2c; }
.delay-empty {
  padding: 22px 0;
  text-align: center;
  color: #98a29c;
  font-size: 13px;
}

/* 在制阶段分布 */
.stage-dist { display: grid; gap: 9px; }
.stage-dist-row {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) 22px;
  align-items: center;
  gap: 10px;
}
.stage-dist-name { font-size: 12px; color: #5b6a63; }
.stage-dist-track {
  height: 8px;
  border-radius: 999px;
  background: #eef2ef;
  overflow: hidden;
}
.stage-dist-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #4b7a5f;
  transform-origin: left center;
  animation: insight-grow-x 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) both;
}
.stage-dist-fill.wait { background: #d4a017; }
.stage-dist-fill.done { background: #7dad8e; }
.stage-dist-count {
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  color: #33473f;
}
.daily-load {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #eef2ef;
}
.daily-load-title {
  font-size: 12px;
  color: #8b958f;
}
.daily-load-cols {
  margin-top: 10px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
  height: 72px;
}
.daily-load-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 3px;
}
.daily-load-bar {
  width: 60%;
  max-width: 26px;
  border-radius: 4px 4px 0 0;
  background: linear-gradient(180deg, #7dad8e, #4b7a5f);
  transform-origin: bottom center;
  animation: insight-grow-y 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) both,
    load-bar-breathe 3.2s ease-in-out infinite 0.7s;
}
.daily-load-col b { font-size: 12px; color: #33473f; }
.daily-load-col em { font-style: normal; font-size: 10px; color: #9caaa3; }

/* 关键物料缺口 */
.mat-gap { list-style: none; margin: 0; padding: 0; display: grid; gap: 12px; }
.mat-gap-row {
  padding: 10px 12px;
  border-radius: 10px;
  background: #f7faf8;
  border-left: 3px solid #7dad8e;
}
.mat-gap-row.warn { border-left-color: #d4a017; background: #fdf8ee; }
.mat-gap-row.risk { border-left-color: #c45c45; background: #fdf6f4; }
.mat-gap-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.mat-gap-top strong { font-size: 13px; color: #31433c; }
.mat-gap-code {
  font-size: 11px;
  color: #98a29c;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.mat-gap-track {
  height: 6px;
  border-radius: 999px;
  background: #e6ece9;
  overflow: hidden;
  margin: 8px 0 6px;
}
.mat-gap-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #4d936a;
  transform-origin: left center;
  animation: insight-grow-x 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) both;
}
.mat-gap-fill.warn { background: #d59431; }
.mat-gap-fill.risk {
  background: #c85a45;
  animation: insight-grow-x 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) both,
    insight-pulse 1.8s ease-in-out infinite 0.7s;
}
.mat-gap-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: #5b6a63;
}
.mat-gap-foot em { font-style: normal; color: #98a29c; font-size: 11px; }

@media (max-width: 1080px) {
  .insight-card.span-5,
  .insight-card.span-4,
  .insight-card.span-3 {
    grid-column: span 12;
  }
}

@media (max-width: 960px) {
  .gantt-layout.has-panel {
    grid-template-columns: 1fr;
  }
  .gantt-detail-panel {
    order: -1;
  }
  .gantt-meta em {
    margin-left: 0;
    width: 100%;
  }
}

/* ══════ Inventory styles（对齐截图） ══════ */
.inv-dashboard {
  width: 100%;
  height: auto;
  padding: 0 0 24px;
  box-sizing: border-box;
  overflow: visible;
  background: transparent;
}

/* ── Header ── */
.inv-header-new {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 4px;
  margin-bottom: 12px;
}
.inv-header-left h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(24px, 2.8vw, 32px);
  font-weight: 600;
  color: #2a3a33;
}
.inv-header-left p {
  margin: 6px 0 0;
  color: #7d8983;
  font-size: 14px;
}
.inv-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 4px;
}
.inv-updated-badge {
  padding: 6px 14px;
  border-radius: 999px;
  background: #eaf3ed;
  color: #4b7a5f;
  font-size: 12px;
  white-space: nowrap;
}
.inv-refresh-btn {
  border: 1px solid #cddbd3;
  border-radius: 8px;
  padding: 6px 14px;
  background: #fff;
  color: #567566;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.12s;
}
.inv-refresh-btn:hover { background: #eef4f0; }
.inv-refresh-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* ── Filters（截图：简洁行内筛选） ── */
.inv-filters {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 14px;
  padding: 0;
  flex-wrap: wrap;
  background: transparent;
  border: 0;
  border-radius: 0;
}
.inv-filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.inv-filter-item label {
  font-size: 13px;
  color: #5f7268;
  font-weight: 500;
  white-space: nowrap;
}
.inv-filter-item select {
  border: 1px solid #d5ddd8;
  border-radius: 8px;
  padding: 6px 12px;
  min-width: 120px;
  background: #fff;
  color: #46544f;
  font-size: 13px;
  outline: 0;
  cursor: pointer;
}
.inv-filter-item select:focus { border-color: #6b8c7b; }

/* ── Loading ── */
.inv-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 0;
  color: #8b958f;
  font-size: 14px;
}
.inv-loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #dde5df;
  border-top-color: #587766;
  border-radius: 50%;
  animation: inv-spin 0.8s linear infinite;
}
@keyframes inv-spin { to { transform: rotate(360deg); } }

/* ── KPI：截图为一行 7 卡 ── */
.inv-kpis {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}
.inv-kpi-card {
  min-width: 0;
  padding: 16px 10px 14px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e2eae5;
  text-align: center;
  box-shadow: 0 1px 3px rgba(48, 76, 64, 0.04);
  transition: box-shadow 0.15s;
}
.inv-kpi-card:hover { box-shadow: 0 2px 10px rgba(48, 76, 64, 0.06); }
.inv-kpi-value {
  font-size: clamp(15px, 1.35vw, 20px);
  font-weight: 700;
  color: #33473f;
  line-height: 1.25;
  font-variant-numeric: tabular-nums;
  word-break: break-all;
}
.inv-kpi-unit {
  font-size: 12px;
  color: #8b958f;
  font-weight: 400;
  margin-left: 2px;
}
.inv-kpi-label {
  margin-top: 6px;
  color: #8b958f;
  font-size: 12px;
  line-height: 1.35;
}
.inv-kpi-card.warn .inv-kpi-value { color: #c98a2e; }
.inv-kpi-card.danger .inv-kpi-value { color: #b4553f; }

/* ── Dashboard：2×2 + 底部通栏 ── */
.dashboard-grid {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  align-items: stretch;
}

.dashboard-card {
  height: 400px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #e2eae5;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(48, 76, 64, 0.04);
}

.dashboard-card:last-child {
  grid-column: 1 / -1;
  height: 320px;
}

.card-header {
  flex: 0 0 auto;
  min-height: 68px;
  padding: 14px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  box-sizing: border-box;
  border-bottom: 1px solid #eef2ef;
  background: #fff;
}

.card-header h2 {
  margin: 0;
  color: #2c3b34;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
}

.card-header p {
  margin: 4px 0 0;
  color: #8b9992;
  font-size: 12px;
}

.card-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.card-actions button {
  border: 1px solid #cddbd3;
  border-radius: 6px;
  padding: 5px 12px;
  background: #fff;
  color: #567566;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.12s;
}

.card-actions button:hover {
  background: #eef4f0;
}

.card-scroll {
  flex: 1;
  min-height: 0;
  width: 100%;
  overflow-x: auto;
  overflow-y: auto;
  box-sizing: border-box;
  scrollbar-width: thin;
  scrollbar-color: #cbd8d1 transparent;
}

.card-scroll::-webkit-scrollbar {
  width: 7px;
  height: 7px;
}

.card-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.card-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #cbd8d1;
}

.card-scroll::-webkit-scrollbar-thumb:hover {
  background: #aabdb3;
}

.analytics-chart-body {
  flex: 1;
  min-height: 0;
  padding: 8px 14px 14px;
  overflow: hidden;
}

.analytics-chart {
  width: 100%;
  height: 100%;
  min-height: 240px;
}

/* ── Dashboard table ── */
.dashboard-table {
  width: 100%;
  min-width: 0;
  table-layout: fixed;
  border-collapse: collapse;
}

.dashboard-table.anomaly-table {
  min-width: 560px;
}

.dashboard-table th,
.dashboard-table td {
  height: 44px;
  padding: 0 14px;
  box-sizing: border-box;
  border-bottom: 1px solid #eef2ef;
  color: #40564d;
  font-size: 13px;
  vertical-align: middle;
}

.dashboard-table th {
  position: sticky;
  top: 0;
  z-index: 3;
  color: #5d7569;
  background: #f4f8f6;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
  font-size: 12px;
}

.dashboard-table td {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-table tbody tr:hover {
  background: #fafcfb;
}

.dashboard-table td small,
.dashboard-table td .sub-name {
  display: block;
  margin-top: 2px;
  color: #8e9c95;
  font-size: 12px;
}

.number-cell {
  text-align: right !important;
  font-variant-numeric: tabular-nums;
}

.suggestion-cell {
  white-space: normal !important;
  line-height: 1.45;
}

.inline-action-btn {
  appearance: none;
  border: 1px solid #c8d9d0;
  border-radius: 6px;
  padding: 4px 10px;
  background: #fff;
  color: #4f7061;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.inline-action-btn:hover {
  border-color: #88a394;
  background: #f1f6f3;
}

.muted-dash {
  color: #bccbc2;
}

.empty-cell {
  height: 110px !important;
  text-align: center !important;
  color: #97a39d !important;
}

/* ── Status tags (anomaly) ── */
.status-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  text-align: center;
}
.status-tag.danger { background: #f6e0d9; color: #b4553f; }
.status-tag.warning { background: #f5edd7; color: #a97e2c; }
.status-tag.normal { background: #e2eae5; color: #5f7268; }

/* ── Rate progress bar ── */
.rate-cell {
  display: grid;
  grid-template-columns: minmax(70px, 1fr) 52px;
  align-items: center;
  gap: 9px;
}

.rate-track {
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #e4ece8;
}

.rate-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.rate-good {
  background: #4d936a;
}

.rate-warning {
  background: #d59431;
}

.rate-danger {
  background: #c85a45;
}

/* ── Media queries ── */
@media (max-width: 1280px) {
  .inv-kpis {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 1050px) {
  .inv-kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-card,
  .dashboard-card:last-child {
    grid-column: auto;
    height: 380px;
  }
}

@media (max-width: 700px) {
  .inv-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-card,
  .dashboard-card:last-child {
    height: 340px;
  }
}

/* ── Empty / fallback ── */
.inv-empty {
  height: 140px;
  display: grid;
  place-items: center;
  color: #8b958f;
  font-size: 13px;
}

/* ── Report dialog ── */
.inv-report-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.35);
  display: grid;
  place-items: center;
  z-index: 1000;
}
.inv-report-dialog {
  background: #fff;
  border-radius: 12px;
  width: min(600px, 90vw);
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 32px rgba(0,0,0,0.15);
}
.inv-report-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #ecf1ee;
}
.inv-report-dialog-header h3 {
  margin: 0;
  font-size: 16px;
  color: #2a3a33;
}
.inv-report-dialog-close {
  border: 0;
  background: transparent;
  font-size: 22px;
  color: #8b958f;
  cursor: pointer;
  padding: 0 4px;
  border-radius: 4px;
  line-height: 1;
}
.inv-report-dialog-close:hover {
  background: #f0f5f2;
  color: #33473f;
}
.inv-report-dialog-body {
  padding: 16px 20px;
  overflow-y: auto;
  flex: 1;
}
.inv-report-content {
  margin: 0;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.8;
  color: #3f554b;
  white-space: pre-wrap;
}
.inv-report-dialog-footer {
  padding: 12px 20px;
  border-top: 1px solid #ecf1ee;
  display: flex;
  justify-content: flex-end;
}
.inv-report-dialog-btn {
  border: 0;
  border-radius: 8px;
  padding: 7px 18px;
  background: #587766;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.inv-report-dialog-btn:hover {
  background: #4a6859;
}
</style>
