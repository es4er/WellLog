import { computed, ref } from 'vue'
import { apiGet, apiPost } from '../api'
import {
  enrichInspectionDetail,
  getMockInspectionDetail,
  mockSubmitInspection
} from '../data/qualityMockData'
import { useSession } from './useSession'

const inspectionTasks = ref([])
const inspectionDetail = ref(null)
const inspectionResult = ref(null)
const qualityIssues = ref([])
const issueDetail = ref(null)
const issueAnalysis = ref(null)
const agentReport = ref('')

const pendingInspection = ref(0)
const inspectingCount = ref(0)
const todayCompleted = ref(0)
const passRatePercent = ref(0)
const openIssues = ref(0)
const strictBatches = ref(0)

const dashboardKpis = ref([
  ['待检测任务', '0', 'warn'],
  ['检测中任务', '0', ''],
  ['今日完成检测', '0', 'ok'],
  ['质量合格率', '0%', ''],
  ['异常数量', '0', '']
])
const inspectionStats = ref([])
const qualityKpis = ref([])
const strictBatchList = ref([])
const qualityFunnel = ref([])
const passRateTrend = ref([])
const riskSummary = ref([])
const traceChain = ref([])

const qualityDataLoading = ref(false)
const qualityDataError = ref('')
const qualityDemoMode = ref(false)
const lastSyncAt = ref('')

const selectedTask = ref(0)
const selectedIssue = ref(0)
const traceBatchNo = ref('')
const traceItemCode = ref('')
const traceInspectionNo = ref('')

let pollTimer = null

export function useQuality() {
  const { activeRoleId, userId } = useSession()

  const taskStats = computed(() => ({
    total: inspectionTasks.value.length,
    pending: inspectionTasks.value.filter((row) => row.status === '待检测').length,
    inspecting: inspectionTasks.value.filter((row) => row.status === '检测中').length,
    completed: inspectionTasks.value.filter((row) => row.status === '完成').length,
    strict: inspectionTasks.value.filter((row) => row.strict && !row.hasInspection).length
  }))

  const currentTask = computed(() => inspectionTasks.value[selectedTask.value] ?? inspectionTasks.value[0] ?? null)
  const currentIssue = computed(() => qualityIssues.value[selectedIssue.value] ?? qualityIssues.value[0] ?? null)

  function applyOverview(data) {
    inspectionTasks.value = data.tasks ?? []
    qualityIssues.value = data.issues ?? []
    pendingInspection.value = data.pendingInspection ?? 0
    inspectingCount.value = data.inspectingCount ?? 0
    todayCompleted.value = data.todayCompleted ?? 0
    passRatePercent.value = data.passRatePercent ?? 0
    openIssues.value = data.openIssues ?? 0
    strictBatches.value = data.strictBatches ?? 0
    dashboardKpis.value = data.dashboardKpis ?? dashboardKpis.value
    inspectionStats.value = data.inspectionStats ?? []
    qualityKpis.value = data.kpis ?? []
    strictBatchList.value = data.strictBatchList ?? []
    qualityFunnel.value = data.qualityFunnel ?? []
    passRateTrend.value = data.passRateTrend ?? []
    riskSummary.value = data.riskSummary ?? []
    lastSyncAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    if (selectedTask.value >= inspectionTasks.value.length) selectedTask.value = 0
    if (selectedIssue.value >= qualityIssues.value.length) selectedIssue.value = 0
  }

  async function loadQualityData(silent = false) {
    if (!silent) qualityDataLoading.value = true
    qualityDataError.value = ''
    try {
      const data = await apiGet('/quality/workbench')
      qualityDemoMode.value = false
      applyOverview(data)
    } catch (error) {
      if (!silent) {
        inspectionTasks.value = []
        qualityIssues.value = []
        qualityDataError.value = error.message || '加载质检工作台失败，请确认后端已启动且数据库可连'
      }
    } finally {
      if (!silent) qualityDataLoading.value = false
    }
  }

  async function loadTaskList(filters = {}) {
    qualityDataError.value = ''
    try {
      const query = new URLSearchParams()
      if (filters.status) query.set('status', filters.status)
      if (filters.batchNo) query.set('batchNo', filters.batchNo)
      if (filters.keyword) query.set('keyword', filters.keyword)
      const qs = query.toString()
      inspectionTasks.value = await apiGet(`/quality/task/list${qs ? `?${qs}` : ''}`)
      qualityDemoMode.value = false
    } catch (error) {
      inspectionTasks.value = []
      qualityDataError.value = error.message || '加载待检任务失败'
    }
  }

  async function createInspectionTask(receiptId) {
    return await apiPost(`/quality/inspection/create?receiptId=${receiptId}`, {})
  }

  async function loadInspectionDetail(receiptId) {
    qualityDataLoading.value = true
    qualityDataError.value = ''
    try {
      const raw = await apiGet(`/quality/task/detail?receiptId=${receiptId}`)
      inspectionDetail.value = enrichInspectionDetail(raw)
      qualityDemoMode.value = false
      return inspectionDetail.value
    } catch (error) {
      const mock = getMockInspectionDetail(receiptId)
      if (mock) {
        inspectionDetail.value = enrichInspectionDetail(mock)
        qualityDemoMode.value = true
        qualityDataError.value = ''
        return inspectionDetail.value
      }
      inspectionDetail.value = null
      qualityDataError.value = error.message || '加载检测详情失败'
      throw error
    } finally {
      qualityDataLoading.value = false
    }
  }

  async function submitInspectionResult(payload) {
    const body = {
      ...payload,
      inspectedBy: payload.inspectedBy ?? userId.value ?? 1
    }
    try {
      const result = await apiPost('/quality/result/save', body)
      inspectionResult.value = result
      qualityDemoMode.value = false
      await loadQualityData(true)
      return result
    } catch (error) {
      if (qualityDemoMode.value) {
        const tasks = inspectionTasks.value
        const issues = qualityIssues.value
        const result = mockSubmitInspection(body, tasks, issues)
        inspectionResult.value = result
        return result
      }
      throw error
    }
  }

  async function loadTrace(params = {}) {
    const query = new URLSearchParams()
    const batchNo = (params.batchNo ?? traceBatchNo.value).trim()
    const itemCode = (params.itemCode ?? traceItemCode.value).trim()
    const inspectionNo = (params.inspectionNo ?? traceInspectionNo.value).trim()
    if (batchNo) query.set('batchNo', batchNo)
    if (itemCode) query.set('itemCode', itemCode)
    if (inspectionNo) query.set('inspectionNo', inspectionNo)
    if (!batchNo && !itemCode && !inspectionNo) return
    traceBatchNo.value = batchNo
    traceItemCode.value = itemCode
    traceInspectionNo.value = inspectionNo
    traceChain.value = await apiGet(`/quality/trace/query?${query.toString()}`)
  }

  async function loadIssueDetail(issueId) {
    qualityDataError.value = ''
    try {
      issueDetail.value = await apiGet(`/quality/issue/detail?issueId=${issueId}`)
      if (issueDetail.value?.analysis) {
        issueAnalysis.value = issueDetail.value.analysis
        agentReport.value = issueDetail.value.analysis.analysisReport ?? ''
      } else {
        issueAnalysis.value = null
        agentReport.value = ''
      }
      qualityDemoMode.value = false
      return issueDetail.value
    } catch (error) {
      issueDetail.value = null
      qualityDataError.value = error.message || '加载异常详情失败'
      throw error
    }
  }

  async function analyzeIssue(issueId) {
    const data = await apiGet(`/quality/agent/analyze?issueId=${issueId}`)
    issueAnalysis.value = data
    agentReport.value = data.analysisReport ?? ''
    await loadQualityData(true)
    await loadIssueDetail(issueId)
    return data
  }

  async function executeIssueAction(action, issueId) {
    let result
    const op = userId.value ?? 1
    if (action === 'freeze') {
      result = await apiPost(`/quality/issue/freeze?issueId=${issueId}&operatedBy=${op}`, {})
    } else if (action === 'return') {
      result = await apiPost(`/quality/issue/return?issueId=${issueId}&operatedBy=${op}`, {})
    } else if (action === 'repair') {
      result = await apiPost(`/quality/issue/repair?issueId=${issueId}`, {})
    } else if (action === 'isolate') {
      result = await apiPost(`/quality/issue/isolate?issueId=${issueId}&operatedBy=${op}`, {})
    } else if (action === 'close') {
      result = await apiPost(`/quality/issue/close?issueId=${issueId}`, {})
    }
    await loadQualityData(true)
    await loadIssueDetail(issueId)
    return result
  }

  async function createIssue(payload) {
    const issue = await apiPost('/quality/issue/create', payload)
    await loadQualityData(true)
    return issue
  }

  async function closeIssue(issueId) {
    await executeIssueAction('close', issueId)
  }

  function startQualityPolling(intervalMs = 8000) {
    stopQualityPolling()
    pollTimer = setInterval(() => loadQualityData(true), intervalMs)
  }

  function stopQualityPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  function statusClass(status) {
    if (status === '完成') return 'ok'
    if (status === '不合格' || status === '部分不合格') return 'danger'
    if (status === '待检测' || status === '检测中') return 'warn'
    return ''
  }

  function issueLevelClass(level) {
    if (level === 'danger' || level === '高') return 'danger'
    if (level === 'warn' || level === '中') return 'warn'
    return 'ok'
  }

  function issueStatusClass(status) {
    if (status === '待分析') return 'warn'
    if (status === '分析完成') return 'ok'
    if (status === '处理中') return 'warn'
    if (status === '已关闭') return ''
    return ''
  }

  function selectTaskByReceiptId(receiptId) {
    const index = inspectionTasks.value.findIndex((t) => t.receiptId === receiptId)
    if (index >= 0) selectedTask.value = index
  }

  function selectIssueById(issueId) {
    const index = qualityIssues.value.findIndex((i) => i.issueId === issueId)
    if (index >= 0) selectedIssue.value = index
  }

  return {
    activeRoleId,
    inspectionTasks,
    inspectionDetail,
    inspectionResult,
    qualityIssues,
    issueDetail,
    issueAnalysis,
    agentReport,
    pendingInspection,
    inspectingCount,
    todayCompleted,
    passRatePercent,
    openIssues,
    strictBatches,
    dashboardKpis,
    inspectionStats,
    qualityKpis,
    strictBatchList,
    qualityFunnel,
    passRateTrend,
    riskSummary,
    traceChain,
    traceBatchNo,
    traceItemCode,
    traceInspectionNo,
    qualityDataLoading,
    qualityDataError,
    qualityDemoMode,
    lastSyncAt,
    selectedTask,
    selectedIssue,
    taskStats,
    currentTask,
    currentIssue,
    loadQualityData,
    loadTaskList,
    createInspectionTask,
    loadInspectionDetail,
    submitInspectionResult,
    loadTrace,
    loadIssueDetail,
    analyzeIssue,
    executeIssueAction,
    createIssue,
    closeIssue,
    startQualityPolling,
    stopQualityPolling,
    statusClass,
    issueLevelClass,
    issueStatusClass,
    selectTaskByReceiptId,
    selectIssueById
  }
}
