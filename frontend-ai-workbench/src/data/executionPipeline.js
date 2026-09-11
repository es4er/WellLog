import { labelToAgentName, resolveAgentPhoto } from './agentProfiles'
import { getExpectedPipelineAgents } from '../utils/agentStream'

const AGENT_LABELS = {
  WmsAgentOrchestrator: '总控智能体',
  OrderPlanAgent: '订单计划智能体',
  OutboundAgent: '出库智能体',
  InventoryAgent: '库存智能体',
  AuditAgent: '审计智能体',
  ReceivingAgent: '收货智能体',
  QualityAgent: '质检智能体',
  InboundAgent: '入库智能体',
  StocktakeAgent: '盘点智能体',
  TransferAgent: '移库智能体',
  IntegrationAgent: '集成智能体',
  SmartWarehouseAgent: '智能仓储智能体',
  UserAgent: '用户权限智能体',
  WorkerFeedbackAgent: '异常反馈智能体'
}

/** 智能体 className → 流水线展示用人名 */
const AGENT_PERSONA_BY_ID = {
  WmsAgentOrchestrator: '程哲',
  OrderPlanAgent: '周计划',
  OutboundAgent: '余晴',
  InventoryAgent: '韩库存',
  AuditAgent: '林审计',
  ReceivingAgent: '沈砚',
  QualityAgent: '程哲',
  InboundAgent: '苏明哲',
  IntegrationAgent: '集成专员',
  StocktakeAgent: '韩库存',
  TransferAgent: '韩库存',
  SmartWarehouseAgent: 'IoT专员',
  UserAgent: '林管理员',
  WorkerFeedbackAgent: '赵工'
}

const ROLE_PIPELINE_AGENTS = {
  pmc: ['OrderPlanAgent', 'InventoryAgent', 'OrderPlanAgent', 'AuditAgent'],
  warehouse: [
    'OutboundAgent',
    'InventoryAgent',
    'InventoryAgent',
    'OutboundAgent',
    'OutboundAgent',
    'AuditAgent'
  ],
  quality: ['QualityAgent', 'InventoryAgent', 'AuditAgent'],
  inventory: ['InventoryAgent', 'StocktakeAgent', 'AuditAgent'],
  admin: ['WmsAgentOrchestrator', 'UserAgent', 'IntegrationAgent', 'AuditAgent'],
  worker: ['OrderPlanAgent', 'OutboundAgent', 'OutboundAgent']
}

const AGENT_DISPLAY_NAMES = {
  ...AGENT_PERSONA_BY_ID,
  总控智能体: '程哲',
  计划智能体: '周计划',
  订单计划智能体: '周计划',
  出库智能体: '余晴',
  库存智能体: '韩库存',
  收货智能体: '沈砚',
  质检智能体: '程哲',
  入库智能体: '苏明哲',
  审计智能体: '林审计',
  集成智能体: '集成专员',
  盘点智能体: '韩库存',
  权限智能体: '林管理员',
  领料智能体: '赵工',
  智能仓储智能体: 'IoT专员'
}

export function personaNameForAgent(agentName, fallbackLabel) {
  if (agentName && AGENT_PERSONA_BY_ID[agentName]) {
    return AGENT_PERSONA_BY_ID[agentName]
  }
  if (fallbackLabel) {
    const fromLabel = AGENT_DISPLAY_NAMES[String(fallbackLabel).trim()]
    if (fromLabel) return fromLabel
    const aliasId = labelToAgentName(fallbackLabel)
    if (aliasId && AGENT_PERSONA_BY_ID[aliasId]) return AGENT_PERSONA_BY_ID[aliasId]
  }
  if (agentName) {
    return AGENT_DISPLAY_NAMES[labelForAgent(agentName)] || agentName
  }
  return fallbackLabel || 'Agent'
}

/** 任务流水线展示：出库智能体 / 库存智能体 等 */
export function pipelineLabelForAgent(agentName, fallbackLabel) {
  if (agentName) {
    const label = labelForAgent(agentName)
    if (label && !label.endsWith('Agent')) return label
  }
  if (fallbackLabel) {
    const text = String(fallbackLabel).trim()
    if (text.endsWith('智能体')) return text.split('·')[0]
    const aliasId = labelToAgentName(text)
    if (aliasId) return labelForAgent(aliasId)
  }
  return labelForAgent(agentName) || fallbackLabel || '智能体'
}

export function agentPhoto(name = 'Agent') {
  return resolveAgentPhoto(name)
}

export function resolveAgentDisplay(agentKey, fallbackPersona, agentName) {
  const photoKey = agentName || agentKey || fallbackPersona || 'Agent'
  const labelKey = agentKey || (agentName ? labelForAgent(agentName) : null)
  let name = labelKey ? AGENT_DISPLAY_NAMES[labelKey] : null
  if (!name && labelKey?.includes('·')) {
    const base = labelKey.split('·')[0]
    name = AGENT_DISPLAY_NAMES[`${base}智能体`] || AGENT_DISPLAY_NAMES[base]
  }
  if (!name && agentName) {
    name = AGENT_DISPLAY_NAMES[labelForAgent(agentName)]
  }
  name = name || labelKey?.replace(/智能体$/, '') || fallbackPersona || 'Agent'
  return { name, photo: resolveAgentPhoto(photoKey), agentKey: photoKey }
}

export function buildExpertAvatars(experts, fallbackPersona) {
  const seen = new Set()
  const avatars = []
  for (const expert of experts) {
    if (!expert?.agentName) continue
    if (seen.has(expert.agentName)) continue
    seen.add(expert.agentName)
    avatars.push(
      resolveAgentDisplay(
        expert.agentLabel || labelForAgent(expert.agentName),
        fallbackPersona,
        expert.agentName
      )
    )
  }
  return avatars
}

export function resolveExpertFromStep(step, fallbackPersona) {
  if (!step?.agentName) {
    return resolveAgentDisplay(step?.agentLabel || step?.stepName, fallbackPersona)
  }
  return resolveAgentDisplay(
    step.agentLabel || step.stepName || labelForAgent(step.agentName),
    fallbackPersona,
    step.agentName
  )
}

export function labelForAgent(agentName) {
  return AGENT_LABELS[agentName] || agentName || '智能体'
}

export function getRolePipelineAgents(roleId) {
  return ROLE_PIPELINE_AGENTS[roleId] ?? ROLE_PIPELINE_AGENTS.pmc
}

export function getRolePipeline(roleId) {
  return getRolePipelineAgents(roleId).map((agentName) => pipelineLabelForAgent(agentName))
}

const ORCHESTRATOR_AGENT = 'WmsAgentOrchestrator'

function orchestratorStageStatus({ agentLiveMode, steps, taskStatus }) {
  if (!agentLiveMode) return null
  if (steps?.length || taskStatus === 'SUCCESS' || taskStatus === 'FAILED' || taskStatus === 'MANUAL_REQUIRED') {
    return 'SUCCESS'
  }
  if (taskStatus === 'RUNNING') return 'RUNNING'
  return 'PENDING'
}

function mergeStageStatuses(...statuses) {
  const list = statuses.filter((s) => s != null && s !== '')
  if (!list.length) return null
  if (list.some((s) => s === 'FAILED')) return 'FAILED'
  if (list.some((s) => s === 'MANUAL_REQUIRED')) return 'MANUAL_REQUIRED'
  if (list.some((s) => s === 'RUNNING')) return 'RUNNING'
  if (list.every((s) => s === 'SUCCESS')) return 'SUCCESS'
  if (list.some((s) => s === 'SUCCESS')) return 'RUNNING'
  return 'PENDING'
}

/** 相邻相同智能体合并为一步（如连续两次库存智能体 / 出库智能体） */
function mergeAdjacentDomainStages(stages) {
  if (!stages?.length) return []
  const merged = []
  for (let i = 0; i < stages.length; i++) {
    const stage = stages[i]
    const last = merged[merged.length - 1]
    if (last && last.agentName === stage.agentName) {
      last.domainEndIndex = i
      last.status = mergeStageStatuses(last.status, stage.status)
    } else {
      merged.push({
        ...stage,
        domainStartIndex: i,
        domainEndIndex: i
      })
    }
  }
  return merged
}

function resolveDomainStepIndex(steps, taskStatus) {
  if (taskStatus === 'SUCCESS') return steps.length
  const runningIdx = steps.findIndex((step) => step.status === 'RUNNING')
  if (runningIdx >= 0) return runningIdx
  const failIdx = steps.findIndex(
    (step) => step.status === 'MANUAL_REQUIRED' || step.status === 'FAILED'
  )
  if (failIdx >= 0) return failIdx
  const successCount = steps.filter((step) => step.status === 'SUCCESS').length
  if (successCount === steps.length) return steps.length
  const nextIdx = steps.findIndex((step) => step.status !== 'SUCCESS')
  return nextIdx >= 0 ? nextIdx : successCount
}

function prependOrchestratorStage(stages, context) {
  const { agentLiveMode, steps, taskStatus } = context
  if (!agentLiveMode && !stages.length) return stages
  if (stages[0]?.agentName === ORCHESTRATOR_AGENT) return stages
  return [
    {
      label: pipelineLabelForAgent(ORCHESTRATOR_AGENT),
      agentLabel: labelForAgent(ORCHESTRATOR_AGENT),
      agentName: ORCHESTRATOR_AGENT,
      status: orchestratorStageStatus({ agentLiveMode, steps, taskStatus }),
      isOrchestrator: true
    },
    ...stages
  ]
}

export function buildPipelineStages({ agentLiveMode, revealedSteps, agentSteps, roleId, taskType, taskStatus }) {
  const steps = revealedSteps?.length ? revealedSteps : agentSteps
  const terminalSuccess = taskStatus === 'SUCCESS'
  const terminalDone =
    taskStatus === 'SUCCESS' || taskStatus === 'FAILED' || taskStatus === 'MANUAL_REQUIRED'
  const stageContext = { agentLiveMode, steps, taskStatus }

  // 有后端真实步骤时，以步骤为唯一数据源（避免骨架索引错位导致未点亮）
  if (steps?.length && (agentLiveMode || terminalDone)) {
    const domainStages = steps.map((step) => {
      let status = step.status
      if (!status && terminalSuccess) status = 'SUCCESS'
      return {
        label: pipelineLabelForAgent(step.agentName, step.agentLabel || step.stepName),
        agentLabel: step.agentLabel || labelForAgent(step.agentName),
        agentName: step.agentName,
        status
      }
    })
    return prependOrchestratorStage(mergeAdjacentDomainStages(domainStages), stageContext)
  }

  const expectedAgents = taskType
    ? getExpectedPipelineAgents(taskType)
    : getRolePipelineAgents(roleId)

  const skeletonStages = expectedAgents.map((agentName, index) => {
    const step = steps?.[index]
    let status = step?.status || null
    if (!status && terminalSuccess && steps?.length) {
      status = index < steps.length ? 'SUCCESS' : null
    } else if (!status && agentLiveMode && !terminalDone) {
      status = 'PENDING'
    }
    return {
      label: step
        ? pipelineLabelForAgent(step.agentName, step.stepName)
        : pipelineLabelForAgent(agentName),
      agentLabel: labelForAgent(agentName),
      agentName: step?.agentName || agentName,
      status
    }
  })
  return prependOrchestratorStage(mergeAdjacentDomainStages(skeletonStages), stageContext)
}

export function resolvePipelineIndex({ agentLiveMode, stages, revealedSteps, agentSteps, taskStatus }) {
  const steps = revealedSteps?.length ? revealedSteps : agentSteps
  const stageCount = stages?.length ?? 0

  if (agentLiveMode || steps?.length) {
    if (taskStatus === 'SUCCESS') return stageCount

    if (!steps?.length) return 0

    const domainIdx = resolveDomainStepIndex(steps, taskStatus)

    for (let i = 0; i < stageCount; i++) {
      const stage = stages[i]
      if (stage.isOrchestrator) {
        if (domainIdx === 0 && taskStatus === 'RUNNING') return i
        continue
      }
      if (
        stage.domainStartIndex != null &&
        stage.domainEndIndex != null &&
        domainIdx >= stage.domainStartIndex &&
        domainIdx <= stage.domainEndIndex
      ) {
        return i
      }
    }

    if (domainIdx >= steps.length) return stageCount
    return Math.max(0, stageCount - 1)
  }
  return 0
}

export function stageStatus(index, pipelineIndex, stage) {
  if (stage?.status === 'FAILED' || stage?.status === 'MANUAL_REQUIRED') return 'error'
  if (stage?.status === 'SUCCESS' || index < pipelineIndex) return 'done'
  if (stage?.status === 'RUNNING' || index === pipelineIndex) return 'current'
  return 'pending'
}

const LEGACY_STEP_LABELS = {
  'AuditAgent·审计记录': 'AuditAgent',
  'OutboundAgent·接收领料单': 'OutboundAgent',
  'InventoryAgent·库存校验': 'InventoryAgent',
  'InventoryAgent·批次库位推荐': 'InventoryAgent',
  'OutboundAgent·生成出库单': 'OutboundAgent',
  'OutboundAgent·生成出库明细': 'OutboundAgent',
  '出库智能体·接收领料单': 'OutboundAgent',
  '库存智能体·库存校验': 'InventoryAgent',
  '库存智能体·批次库位推荐': 'InventoryAgent',
  '出库智能体·生成出库单': 'OutboundAgent',
  '出库智能体·生成出库明细': 'OutboundAgent',
  '订单计划·生成计划': 'OrderPlanAgent',
  '订单计划·生成领料单': 'OrderPlanAgent',
  '库存·齐套校验': 'InventoryAgent'
}

export function formatPipelineLabel(label = '') {
  const text = String(label).trim()
  const agentName = LEGACY_STEP_LABELS[text] || labelToAgentName(text)
  if (agentName) return pipelineLabelForAgent(agentName, text)
  return AGENT_DISPLAY_NAMES[text] || text
}
