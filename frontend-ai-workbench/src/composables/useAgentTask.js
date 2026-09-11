import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet, apiPost } from '../api'
import {
  TERMINAL_STATUS,
  buildEvidenceFromStep,
  getExpectedPipelineAgents,
  mapLogToStreamEntry
} from '../utils/agentStream'
import { getRolePipeline, getRolePipelineAgents, personaNameForAgent } from '../data/executionPipeline'
import { demoPreserveRoute } from '../router/menuRoutes'
import { useSession } from './useSession'
import { useDemoRole, useViewRole } from './useRoleAccess'
import { usePmc } from './usePmc'
import { useWorker } from './useWorker'
import { useWarehouse } from './useWarehouse'
import { useQuality } from './useQuality'
import {
  assistantReply,
  assistantIntent,
  pendingAssistantAction,
  assistantReplyRoleId,
  clearAssistantState
} from './assistantPanelState'

const agentTaskId = ref(null)
const agentTaskNo = ref('')
const agentTaskType = ref('')
const agentTaskStatus = ref('')
const agentSteps = ref([])
const agentLogs = ref([])
const agentTaskLoading = ref(false)
const agentTaskError = ref('')
const agentLiveMode = ref(false)
const agentAnalysis = ref('')
const agentAnalysisLoading = ref(false)
const agentAnalysisError = ref('')
const traceCollapsed = ref(false)
const traceVisible = ref(true)
const tracePosition = reactive({ x: 820, y: 420 })
const dragState = reactive({ active: false, offsetX: 0, offsetY: 0 })

const streamingActive = ref(false)
const streamSettled = ref(false)
const revealedStreamItems = ref([])
const revealedSteps = ref([])
const revealedEvidence = ref([])
const streamStageIndex = ref(0)
const isTaskRunning = ref(false)

let pollTimer = null
let revealTimer = null
const revealQueue = []
const seenLogIds = new Set()
const seenStepIds = new Set()
const seenEvidenceIds = new Set()

const POLL_MS = 600
const REVEAL_MS = 420

function resetStreamState() {
  stopStreaming()
  streamSettled.value = false
  revealedStreamItems.value = []
  revealedSteps.value = []
  revealedEvidence.value = []
  streamStageIndex.value = 0
  isTaskRunning.value = false
  revealQueue.length = 0
  seenLogIds.clear()
  seenStepIds.clear()
  seenEvidenceIds.clear()
  agentSteps.value = []
  agentLogs.value = []
}

function stopStreaming() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  if (revealTimer) {
    clearInterval(revealTimer)
    revealTimer = null
  }
  streamingActive.value = false
}

function enqueueReveal(item) {
  revealQueue.push(item)
  if (!revealTimer) {
    revealTimer = setInterval(processRevealQueue, REVEAL_MS)
  }
}

function processRevealQueue() {
  if (!revealQueue.length) {
    if (revealTimer) {
      clearInterval(revealTimer)
      revealTimer = null
    }
    return
  }

  const item = revealQueue.shift()

  if (item.type === 'log') {
    revealedStreamItems.value = [...revealedStreamItems.value, item.entry]
  }

  if (item.type === 'step') {
    const step = item.step
    revealedSteps.value = [...revealedSteps.value, step]
    const completed = revealedSteps.value.filter((s) => s.status === 'SUCCESS').length
    const runningIdx = revealedSteps.value.findIndex((s) => s.status === 'RUNNING')
    streamStageIndex.value = runningIdx >= 0 ? runningIdx : Math.max(0, completed)

    for (const evidence of buildEvidenceFromStep(step)) {
      if (!seenEvidenceIds.has(evidence.id)) {
        seenEvidenceIds.add(evidence.id)
        revealedEvidence.value = [...revealedEvidence.value, evidence]
      }
    }
  }
}

function ingestPollData(steps, logs) {
  agentSteps.value = steps ?? []
  agentLogs.value = logs ?? []

  for (const log of agentLogs.value) {
    if (seenLogIds.has(log.logId)) continue
    seenLogIds.add(log.logId)
    enqueueReveal({ type: 'log', entry: mapLogToStreamEntry(log) })
  }

  for (const step of agentSteps.value) {
    const prev = revealedSteps.value.find((s) => s.stepId === step.stepId)
    const changed = !prev || prev.status !== step.status
    if (!seenStepIds.has(step.stepId)) {
      seenStepIds.add(step.stepId)
      enqueueReveal({ type: 'step', step })
    } else if (changed) {
      revealedSteps.value = revealedSteps.value.map((s) => (s.stepId === step.stepId ? step : s))
      for (const evidence of buildEvidenceFromStep(step)) {
        if (!seenEvidenceIds.has(evidence.id)) {
          seenEvidenceIds.add(evidence.id)
          revealedEvidence.value = [...revealedEvidence.value, evidence]
        }
      }
      const completed = revealedSteps.value.filter((s) => s.status === 'SUCCESS').length
      streamStageIndex.value = Math.max(streamStageIndex.value, completed)
    }
  }
}

function startStreamingPoll(taskId, onComplete) {
  stopStreaming()
  streamingActive.value = true
  isTaskRunning.value = true

  const poll = async () => {
    try {
      const [detail, steps, logs] = await Promise.all([
        apiGet(`/agent/task/${taskId}`),
        apiGet(`/agent/task/${taskId}/steps`),
        apiGet(`/agent/task/${taskId}/logs`)
      ])
      agentTaskId.value = taskId
      agentTaskNo.value = detail.taskNo ?? ''
      agentTaskStatus.value = detail.status ?? ''
      agentLiveMode.value = true
      ingestPollData(steps, logs)

      if (TERMINAL_STATUS.has(detail.status)) {
        isTaskRunning.value = false
        const waitFinish = setInterval(() => {
          if (!revealQueue.length) {
            clearInterval(waitFinish)
            stopStreaming()
            streamSettled.value = true
            onComplete?.()
          }
        }, REVEAL_MS)
        if (pollTimer) {
          clearInterval(pollTimer)
          pollTimer = null
        }
      }
    } catch (error) {
      agentTaskError.value = error.message || '轮询任务状态失败'
      isTaskRunning.value = false
      stopStreaming()
    }
  }

  poll()
  pollTimer = setInterval(poll, POLL_MS)
}

export function useAgentTask() {
  const router = useRouter()
  const { prompt, activeStageIndex, userId } = useSession()
  const { demoRoleId, isAdminDemo } = useDemoRole()
  const { viewRole } = useViewRole()

  function pushDemoAware(path, extraQuery = {}) {
    router.push(demoPreserveRoute(path, demoRoleId.value, isAdminDemo.value, extraQuery))
  }
  const { productionPlans, loadPmcData } = usePmc()
  const { loadWorkerData, pickingTasks, activeTaskId, activeMaterialId, workerId, syncWorkerIdFromSession } = useWorker()
  const {
    setPendingOutboundRequisition,
    syncOutboundFromServer,
    lastPendingRequisitionNo,
    lastGeneratedOutboundId,
    pendingRequisitions,
    loadWarehouseData
  } = useWarehouse()
  const { loadQualityData } = useQuality()

  const visibleLogs = computed(() => {
    if (streamingActive.value && revealedStreamItems.value.length) {
      return revealedStreamItems.value.map((item) => [item.tag, item.name, item.content, item.agentName])
    }
    if (agentLogs.value.length) {
      return agentLogs.value.map((log) => {
        const entry = mapLogToStreamEntry(log)
        return [entry.tag, entry.name, entry.content, entry.agentName]
      })
    }
    return []
  })

  const currentEvidence = computed(() => revealedEvidence.value)

  const displayStages = computed(() => {
    if (revealedSteps.value.length) {
      return revealedSteps.value.map((step) =>
        personaNameForAgent(step.agentName, step.stepName || step.agentLabel)
      )
    }
    if (agentTaskType.value) {
      return getExpectedPipelineAgents(agentTaskType.value).map((agentName) =>
        personaNameForAgent(agentName)
      )
    }
    return getRolePipeline(demoRoleId.value)
  })

  const pipelineStageIndex = computed(() => {
    if (agentLiveMode.value) return streamStageIndex.value
    return 0
  })

  const displayTaskName = computed(() => {
    if (agentTaskNo.value) return prompt.value || `Agent 任务 ${agentTaskNo.value}`
    return prompt.value || '等待启动 Agent 任务'
  })

  const displayTaskId = computed(() => {
    if (agentTaskId.value) return agentTaskNo.value || `#${agentTaskId.value}`
    return '—'
  })

  const agentProgress = computed(() => {
    if (displayStages.value.length) {
      const done = revealedSteps.value.filter((s) => s.status === 'SUCCESS').length
      if (isTaskRunning.value) {
        return Math.min(95, Math.round(((done + 0.5) / displayStages.value.length) * 100))
      }
      if (revealedSteps.value.length) {
        return Math.round((done / displayStages.value.length) * 100)
      }
    }
    if (agentSteps.value.length) {
      const done = agentSteps.value.filter((step) => step.status === 'SUCCESS').length
      return Math.round((done / agentSteps.value.length) * 100)
    }
    return 0
  })

  const showVerdict = computed(() => streamSettled.value && TERMINAL_STATUS.has(agentTaskStatus.value))

  const showThinking = computed(() => agentLiveMode.value && !streamSettled.value)

  const agentVerdict = computed(() => {
    if (agentAnalysis.value) return agentAnalysis.value
    if (!agentLiveMode.value) return ''
    if (agentAnalysisLoading.value) return 'DeepSeek 正在分析 Agent 执行结果，请稍候...'
    if (agentAnalysisError.value) {
      if (agentTaskStatus.value === 'SUCCESS') return `Agent 任务已完成，但 AI 分析失败：${agentAnalysisError.value}`
      if (agentTaskStatus.value === 'MANUAL_REQUIRED') {
        return `Agent 检测到库存不足或复核异常，任务已暂停。AI 分析失败：${agentAnalysisError.value}`
      }
      return agentAnalysisError.value
    }
    if (agentTaskStatus.value === 'SUCCESS') {
      const outboundStep = agentSteps.value.find((step) => step.agentName === 'OutboundAgent')
      const planStep = agentSteps.value.find((step) => step.agentName === 'OrderPlanAgent')
      const inventoryStep = agentSteps.value.find((step) => step.agentName === 'InventoryAgent')
      let outboundNo = ''
      let planNo = ''
      let kittingRate = null
      let requisitionSkipped = false
      let requisitionNo = ''
      let shortageHint = ''
      if (outboundStep?.outputData) {
        try {
          outboundNo = JSON.parse(outboundStep.outputData).outboundNo ?? ''
        } catch {
          outboundNo = ''
        }
      }
      if (planStep?.outputData) {
        try {
          const out = JSON.parse(planStep.outputData)
          planNo = out.planNo ?? ''
        } catch {
          planNo = ''
        }
      }
      if (inventoryStep?.outputData) {
        try {
          const invOut = JSON.parse(inventoryStep.outputData)
          kittingRate = invOut.kittingRate ?? null
          const shortages = Array.isArray(invOut.shortageAnalysis) ? invOut.shortageAnalysis : []
          if (shortages.length) {
            const typeCount = {}
            for (const row of shortages) {
              const label = row.shortageTypeLabel || row.shortageType || '真实缺料'
              typeCount[label] = (typeCount[label] || 0) + 1
            }
            const typeHint = Object.entries(typeCount)
              .map(([k, v]) => `${k}${v}`)
              .join('、')
            shortageHint = `，缺料 ${shortages.length} 项（${typeHint}）已回写计划`
          }
        } catch {
          kittingRate = null
        }
      }
      const lastPlanStep = [...agentSteps.value].reverse().find((step) => step.agentName === 'OrderPlanAgent')
      if (lastPlanStep?.outputData) {
        try {
          const out = JSON.parse(lastPlanStep.outputData)
          requisitionSkipped = out.requisitionSkipped === true
          if (out.requisitionId) {
            requisitionNo = out.requisitionNo ?? `REQ-${out.requisitionId}`
          }
        } catch {
          requisitionSkipped = false
          requisitionNo = ''
        }
      }
      if (outboundNo) {
        return `Agent 任务已完成，出库单 ${outboundNo} 已生成，请前往拣货任务开始 PDA 扫码拣货。`
      }
      if (planNo || agentTaskType.value === 'ORDER_PLAN_REQUISITION') {
        if (requisitionSkipped) {
          return `Agent 任务已完成，生产计划 ${planNo || ''} 已生成，齐套率 ${kittingRate ?? '—'}% 未达 90%，未生成正式领料单${shortageHint}。`.trim()
        }
        if (requisitionNo) {
          const kitHint = kittingRate != null ? `（齐套率 ${kittingRate}%${shortageHint}）` : ''
          return `Agent 任务已完成，生产计划 ${planNo || ''} 已生成${kitHint}，领料单 ${requisitionNo} 已提交仓管出库。`
        }
        return `Agent 任务已完成${planNo ? `，生产计划 ${planNo} 已生成` : ''}，齐套校验与领料单处理完成${shortageHint}。`
      }
      return 'Agent 任务已完成。'
    }
    if (agentTaskStatus.value === 'MANUAL_REQUIRED') {
      if (agentTaskType.value === 'REQUISITION_OUTBOUND') {
        return '库存不足，出库单未生成。已写入仓管「异常记录」，请补货/调拨后标记处理并重新生成出库单。'
      }
      return 'Agent 检测到库存不足或复核异常，任务已暂停并等待人工介入。'
    }
    if (agentTaskStatus.value === 'FAILED') {
      const failedStep = agentSteps.value.find((step) => step.status === 'FAILED')
      return failedStep?.errorMessage || 'Agent 任务执行失败，请查看步骤详情。'
    }
    return ''
  })

  const decisionLogs = computed(() => {
    const steps = revealedSteps.value.length ? revealedSteps.value : agentSteps.value
    if (!steps.length) return []
    return steps.map((step, index) => {
      const duration =
        step.durationMs != null
          ? `${(step.durationMs / 1000).toFixed(1)}s`
          : step.status === 'RUNNING'
            ? '执行中'
            : '—'
      return [
        String(step.stepNo ?? index + 1),
        personaNameForAgent(step.agentName, step.stepName || step.agentLabel || step.agentName),
        step.status,
        duration
      ]
    })
  })

  const liveExperts = computed(() => {
    if (!agentLiveMode.value) return []
    const steps = revealedSteps.value.length ? revealedSteps.value : agentSteps.value
    const seen = new Set()
    const experts = []

    const orchestratorLog = agentLogs.value.find((log) => log.agentName === 'WmsAgentOrchestrator')
    if (!seen.has('WmsAgentOrchestrator')) {
      seen.add('WmsAgentOrchestrator')
      experts.push({
        agentName: 'WmsAgentOrchestrator',
        agentLabel: personaNameForAgent('WmsAgentOrchestrator', orchestratorLog?.agentLabel)
      })
    }

    for (const step of steps) {
      if (!step.agentName || seen.has(step.agentName)) continue
      seen.add(step.agentName)
      experts.push({
        agentName: step.agentName,
        agentLabel: personaNameForAgent(step.agentName, step.agentLabel || step.stepName)
      })
    }

    if (!steps.length) {
      for (const agentName of getExpectedPipelineAgents(agentTaskType.value)) {
        if (!agentName || seen.has(agentName)) continue
        seen.add(agentName)
        experts.push({
          agentName,
          agentLabel: personaNameForAgent(agentName)
        })
      }
    }

    return experts
  })

  async function loadAgentAnalysis(taskId) {
    agentAnalysisLoading.value = true
    agentAnalysisError.value = ''
    try {
      const data = await apiGet(`/agent/task/${taskId}/analysis`)
      agentAnalysis.value = data.analysis ?? ''
    } catch (error) {
      agentAnalysis.value = ''
      agentAnalysisError.value = error.message || 'AI 分析失败'
    } finally {
      agentAnalysisLoading.value = false
    }
  }

  async function loadAgentTrace(taskId) {
    agentAnalysis.value = ''
    agentAnalysisError.value = ''
    resetStreamState()
    const [detail, steps, logs] = await Promise.all([
      apiGet(`/agent/task/${taskId}`),
      apiGet(`/agent/task/${taskId}/steps`),
      apiGet(`/agent/task/${taskId}/logs`)
    ])
    agentTaskId.value = taskId
    agentTaskNo.value = detail.taskNo ?? ''
    agentTaskStatus.value = detail.status ?? ''
    agentSteps.value = steps ?? []
    agentLogs.value = logs ?? []
    agentLiveMode.value = true
    streamSettled.value = true
    revealedStreamItems.value = logs.map(mapLogToStreamEntry)
    revealedSteps.value = steps ?? []
    revealedEvidence.value = steps.flatMap(buildEvidenceFromStep)
    streamStageIndex.value = Math.max(0, steps.length - 1)
  }

  function resolvePlanByNo(planNo) {
    return productionPlans.value.find((plan) => plan.id === planNo)
  }

  async function startAgentTask(options = {}) {
    const {
      taskType,
      planId,
      planNo,
      orderId,
      requisitionId,
      taskName,
      promptText,
      autoApproveOrder,
      businessNo,
      pickingTaskId,
      pickingLineId,
      barcodeValue,
      workerId,
      exceptionType,
      exceptionNote,
      location,
      operatedBy,
      receiptId,
      receiptNo,
      receiptLines,
      warehouseId
    } = options
    agentTaskLoading.value = true
    agentTaskError.value = ''
    agentAnalysis.value = ''
    resetStreamState()
    try {
      if (promptText) prompt.value = promptText
      const actorId = Number(operatedBy ?? workerId ?? userId.value ?? 1) || 1
      const data = {
        requestedBy: actorId,
        requisitionDept: 'PMC计划部',
        warehouseId: 1,
        operatedBy: actorId,
        reviewedBy: actorId
      }
      if (planId) data.planId = planId
      if (orderId) data.orderId = orderId
      if (requisitionId) data.requisitionId = requisitionId
      if (receiptId) data.receiptId = receiptId
      if (receiptLines?.length) data.receiptLines = receiptLines
      if (warehouseId) data.warehouseId = warehouseId
      if (autoApproveOrder) data.autoApproveOrder = true
      if (promptText) data.promptText = promptText
      // 拣货执行人由仓管员分配，出库 Agent 默认不写死 assignedTo
      if (options.assignedTo != null) data.assignedTo = options.assignedTo
      if (options.pickingTaskId) data.pickingTaskId = options.pickingTaskId
      if (options.pickingLineId) data.pickingLineId = options.pickingLineId
      if (options.barcodeValue) data.barcodeValue = options.barcodeValue
      if (options.workerId) data.workerId = options.workerId
      if (options.exceptionType) data.exceptionType = options.exceptionType
      if (options.exceptionNote) data.exceptionNote = options.exceptionNote
      if (options.location) data.location = options.location
      if (options.pushToErp != null) data.pushToErp = options.pushToErp

      const result = await apiPost('/agent/task/start', {
        taskType,
        taskName,
        businessNo: businessNo ?? planNo ?? receiptNo ?? String(planId ?? requisitionId ?? orderId ?? receiptId ?? pickingTaskId ?? ''),
        createdBy: actorId,
        data
      })

      agentTaskId.value = result.taskId
      agentTaskNo.value = result.taskNo ?? ''
      agentTaskType.value = taskType
      agentTaskStatus.value = result.status ?? 'RUNNING'
      agentLiveMode.value = true
      activeStageIndex.value = 0
      traceVisible.value = true
      traceCollapsed.value = false
      pushDemoAware('/execution')

      startStreamingPoll(result.taskId, async () => {
        activeStageIndex.value = Math.max(0, revealedSteps.value.length - 1)
        loadAgentAnalysis(result.taskId)
        if (taskType === 'REQUISITION_OUTBOUND' && agentTaskStatus.value === 'SUCCESS') {
          await syncOutboundFromAgentResult()
        }
        if (demoRoleId.value === 'pmc' || demoRoleId.value === 'warehouse' || demoRoleId.value === 'worker') {
          await loadPmcData()
        }
        // 出库生成成功/缺料人工介入后都刷新仓管数据（异常栏会收录 MANUAL_REQUIRED）
        if (
          demoRoleId.value === 'warehouse' ||
          taskType === 'ORDER_PLAN_REQUISITION' ||
          taskType === 'REQUISITION_OUTBOUND'
        ) {
          await loadWarehouseData(true)
        }
        if (demoRoleId.value === 'worker') {
          await loadWorkerData()
        }
        if (demoRoleId.value === 'quality') {
          await loadQualityData(true)
        }
      })
    } catch (error) {
      agentTaskError.value = error.message || 'Agent 任务启动失败'
    } finally {
      agentTaskLoading.value = false
    }
  }

  async function startPmcAgentTask(options = {}) {
    const { planId, planNo, orderId, taskName, promptText, autoApproveOrder } = options
    return startAgentTask({
      taskType: 'ORDER_PLAN_REQUISITION',
      planId,
      planNo,
      orderId,
      taskName: taskName ?? `PMC计划排产-${planNo ?? planId ?? orderId}`,
      promptText,
      autoApproveOrder
    })
  }

  async function startOutboundAgentTask(options = {}) {
    const { requisitionId, planId, planNo, taskName, promptText, pendingRequisitionNo, businessNo } = options
    if (pendingRequisitionNo || businessNo) {
      setPendingOutboundRequisition(pendingRequisitionNo || businessNo)
    }
    return startAgentTask({
      taskType: 'REQUISITION_OUTBOUND',
      requisitionId,
      planId,
      planNo,
      businessNo: businessNo ?? pendingRequisitionNo ?? planNo,
      taskName: taskName ?? `仓管出库执行-${planNo ?? requisitionId}`,
      promptText
    })
  }

  async function startWorkerScanAgentTask(options = {}) {
    const { pickingTaskId, pickingLineId, barcodeValue, workOrder, taskName, promptText, workerUserId } = options
    return startAgentTask({
      taskType: 'WORKER_PICKING_SCAN',
      taskName: taskName ?? `工人扫码确认-${workOrder ?? pickingTaskId}`,
      businessNo: workOrder ?? String(pickingTaskId ?? ''),
      promptText,
      pickingTaskId,
      pickingLineId,
      barcodeValue,
      workerId: workerUserId ?? 1,
      operatedBy: workerUserId ?? 1
    })
  }

  async function startWorkerExceptionAgentTask(options = {}) {
    const { pickingTaskId, pickingLineId, workOrder, exceptionType, exceptionNote, location, taskName, promptText, workerUserId } = options
    return startAgentTask({
      taskType: 'WORKER_EXCEPTION_FEEDBACK',
      taskName: taskName ?? `工人异常反馈-${workOrder ?? pickingTaskId}`,
      businessNo: workOrder ?? String(pickingTaskId ?? ''),
      promptText,
      pickingTaskId,
      pickingLineId,
      exceptionType,
      exceptionNote,
      location,
      workerId: workerUserId ?? 1,
      operatedBy: workerUserId ?? 1
    })
  }

  async function startQualityAgentTask(options = {}) {
    const { receiptId, receiptNo, receiptLines, warehouseId, batchNo, taskName, promptText } = options
    return startAgentTask({
      taskType: 'RECEIPT_INSPECTION_INBOUND',
      receiptId,
      receiptNo,
      receiptLines,
      warehouseId: warehouseId ?? 1,
      taskName: taskName ?? `质检判定-${receiptNo ?? batchNo ?? receiptId}`,
      promptText
    })
  }

  async function openAgentTrace() {
    if (!agentTaskId.value) return
    await loadAgentTrace(agentTaskId.value)
    traceVisible.value = true
    traceCollapsed.value = false
    pushDemoAware('/execution')
  }

  function extractOutboundNoFromSteps() {
    const outboundStep = agentSteps.value.find((step) => step.agentName === 'OutboundAgent')
    if (!outboundStep?.outputData) return ''
    try {
      return JSON.parse(outboundStep.outputData).outboundNo ?? ''
    } catch {
      return ''
    }
  }

  async function syncOutboundFromAgentResult() {
    const reqNo = lastPendingRequisitionNo.value
    if (!reqNo) {
      await loadWarehouseData(true)
      return null
    }
    return syncOutboundFromServer(reqNo)
  }

  function goToGeneratedOutbound() {
    const pushOutbound = (path) => pushDemoAware(path)
    if (!lastGeneratedOutboundId.value && lastPendingRequisitionNo.value) {
      syncOutboundFromAgentResult().then(() => {
        if (lastGeneratedOutboundId.value) {
          pushOutbound(`/outbound-orders/${lastGeneratedOutboundId.value}`)
        } else {
          pushOutbound('/outbound-orders')
        }
      })
      return
    }
    if (lastGeneratedOutboundId.value) {
      pushOutbound(`/outbound-orders/${lastGeneratedOutboundId.value}`)
      return
    }
    pushOutbound('/outbound-orders')
  }

  function clearAssistantPanel() {
    clearAssistantState()
  }

  async function askWorkbenchAssistant(message) {
    agentTaskLoading.value = true
    agentTaskError.value = ''
    clearAssistantState()
    try {
      const role = viewRole.value?.id || demoRoleId.value || 'pmc'
      const payload = { role, message }
      if (role === 'worker') {
        syncWorkerIdFromSession()
        payload.workerId = workerId.value || userId.value || 1
      }
      const res = await apiPost('/agent/assistant/chat', payload)
      assistantIntent.value = res.intent || 'answer'
      assistantReply.value = res.reply || ''
      assistantReplyRoleId.value = role
      if (res.intent === 'action' && res.action) {
        pendingAssistantAction.value = {
          action: res.action,
          params: res.params || {},
          confirmMessage: res.confirmMessage || res.reply || '确认启动 Agent 流水线？',
          promptText: message
        }
      }
      return res
    } catch (error) {
      agentTaskError.value = error.message || '工作台助手请求失败'
      return null
    } finally {
      agentTaskLoading.value = false
    }
  }

  /** @deprecated 使用 askWorkbenchAssistant；保留别名兼容旧调用 */
  async function askPmcAssistant(message) {
    return askWorkbenchAssistant(message)
  }

  async function confirmAssistantAction() {
    const pending = pendingAssistantAction.value
    if (!pending?.params && !pending?.action) {
      agentTaskError.value = '没有待确认的执行动作'
      return
    }
    const { action, params, promptText } = pending
    pendingAssistantAction.value = null
    assistantIntent.value = ''
    const taskType = action || params.taskType

    if (taskType === 'ORDER_PLAN_REQUISITION') {
      return startPmcAgentTask({
        orderId: params.orderId,
        planId: params.planId,
        planNo: params.planNo,
        taskName: params.taskName,
        promptText: promptText || params.taskName,
        autoApproveOrder: Boolean(params.autoApproveOrder)
      })
    }
    if (taskType === 'REQUISITION_OUTBOUND') {
      return startOutboundAgentTask({
        requisitionId: params.requisitionId,
        planId: params.planId,
        planNo: params.planNo,
        taskName: params.taskName,
        promptText: promptText || params.taskName,
        businessNo: params.businessNo,
        pendingRequisitionNo: params.businessNo
      })
    }
    if (taskType === 'WORKER_PICKING_SCAN') {
      return startWorkerScanAgentTask({
        pickingTaskId: params.pickingTaskId,
        pickingLineId: params.pickingLineId,
        barcodeValue: params.barcodeValue,
        workOrder: params.workOrder,
        taskName: params.taskName,
        promptText: promptText || params.taskName,
        workerUserId: params.workerId || workerId.value
      })
    }
    if (taskType === 'WORKER_EXCEPTION_FEEDBACK') {
      return startWorkerExceptionAgentTask({
        pickingTaskId: params.pickingTaskId,
        pickingLineId: params.pickingLineId,
        workOrder: params.workOrder,
        exceptionType: params.exceptionType,
        exceptionNote: params.exceptionNote,
        location: params.location,
        taskName: params.taskName,
        promptText: promptText || params.taskName,
        workerUserId: params.workerId || workerId.value
      })
    }
    if (taskType === 'RECEIPT_INSPECTION_INBOUND') {
      return startQualityAgentTask({
        receiptId: params.receiptId,
        receiptNo: params.receiptNo,
        receiptLines: params.receiptLines || params.lines,
        warehouseId: params.warehouseId,
        taskName: params.taskName,
        promptText: promptText || params.taskName
      })
    }
    // 通用：安全库存 / 盘点 / 其它任务类型直接走 Orchestrator
    return startAgentTask({
      taskType,
      taskName: params.taskName || `${taskType}任务`,
      businessNo: params.businessNo || params.planNo || '',
      promptText: promptText || params.taskName,
      planId: params.planId,
      orderId: params.orderId,
      requisitionId: params.requisitionId,
      ...params
    })
  }

  async function confirmPmcAssistantAction() {
    return confirmAssistantAction()
  }

  function cancelPmcAssistantAction() {
    pendingAssistantAction.value = null
    assistantIntent.value = assistantIntent.value === 'action' ? 'answer' : assistantIntent.value
    if (!assistantReply.value) {
      assistantReply.value = '已取消执行。你可以继续提问，或重新发起动作指令。'
    } else {
      assistantReply.value = `${assistantReply.value}\n\n（已取消本次执行）`
    }
  }

  async function startTask() {
    agentTaskError.value = ''
    const text = prompt.value?.trim() || ''
    if (!text) {
      agentTaskError.value = '请先输入问题或指令'
      return
    }
    // 全角色统一：先判意图再分流；问答留在工作台，动作确认后启动 Orchestrator 真链
    await askWorkbenchAssistant(text)
  }

  function goHome() {
    stopStreaming()
    activeStageIndex.value = null
    agentLiveMode.value = false
    pushDemoAware('/workbench')
  }

  function startTraceDrag(event) {
    if (event.target.closest('button')) return
    dragState.active = true
    dragState.offsetX = event.clientX - tracePosition.x
    dragState.offsetY = event.clientY - tracePosition.y
    window.addEventListener('pointermove', onTraceDrag)
    window.addEventListener('pointerup', stopTraceDrag, { once: true })
  }

  function onTraceDrag(event) {
    if (!dragState.active) return
    const maxX = window.innerWidth - 80
    const maxY = window.innerHeight - 48
    tracePosition.x = Math.min(Math.max(16, event.clientX - dragState.offsetX), maxX)
    tracePosition.y = Math.min(Math.max(72, event.clientY - dragState.offsetY), maxY)
  }

  function stopTraceDrag() {
    dragState.active = false
    window.removeEventListener('pointermove', onTraceDrag)
  }

  return {
    agentTaskId,
    agentTaskNo,
    agentTaskType,
    agentTaskStatus,
    agentSteps,
    agentLogs,
    agentTaskLoading,
    agentTaskError,
    assistantReply,
    assistantIntent,
    pendingAssistantAction,
    askPmcAssistant,
    askWorkbenchAssistant,
    confirmPmcAssistantAction,
    confirmAssistantAction,
    cancelPmcAssistantAction,
    clearAssistantState: clearAssistantPanel,
    assistantReplyRoleId,
    agentLiveMode,
    agentAnalysis,
    agentAnalysisLoading,
    agentAnalysisError,
    streamingActive,
    streamSettled,
    isTaskRunning,
    showThinking,
    pipelineStageIndex,
    liveExperts,
    revealedEvidence,
    revealedSteps,
    traceCollapsed,
    traceVisible,
    tracePosition,
    dragState,
    visibleLogs,
    currentEvidence,
    displayStages,
    displayTaskName,
    displayTaskId,
    agentProgress,
    showVerdict,
    agentVerdict,
    decisionLogs,
    startPmcAgentTask,
    startOutboundAgentTask,
    startWorkerScanAgentTask,
    startWorkerExceptionAgentTask,
    startQualityAgentTask,
    openAgentTrace,
    startTask,
    goToGeneratedOutbound,
    lastGeneratedOutboundId,
    goHome,
    startTraceDrag,
    resolvePlanByNo
  }
}
