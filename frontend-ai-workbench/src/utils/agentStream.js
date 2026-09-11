import { formatPlanNo } from './renderMarkdown'

/** 任务类型 → 智能体 className 执行链（与后端 Orchestrator 一致） */
const PMC_PIPELINE_AGENTS = ['OrderPlanAgent', 'InventoryAgent', 'OrderPlanAgent', 'AuditAgent']
const OUTBOUND_PIPELINE_AGENTS = [
  'OutboundAgent',
  'InventoryAgent',
  'InventoryAgent',
  'OutboundAgent',
  'OutboundAgent',
  'AuditAgent'
]
const WORKER_SCAN_PIPELINE_AGENTS = ['SmartWarehouseAgent', 'InventoryAgent', 'AuditAgent']
const WORKER_EXCEPTION_PIPELINE_AGENTS = [
  'WorkerFeedbackAgent',
  'OutboundAgent',
  'InventoryAgent',
  'AuditAgent'
]

const PIPELINE_AGENTS_BY_TASK = {
  ORDER_PLAN_REQUISITION: PMC_PIPELINE_AGENTS,
  REQUISITION_OUTBOUND: OUTBOUND_PIPELINE_AGENTS,
  ORDER_REQUISITION_OUTBOUND: ['OrderPlanAgent', 'OutboundAgent', 'InventoryAgent', 'AuditAgent'],
  WORKER_PICKING_SCAN: WORKER_SCAN_PIPELINE_AGENTS,
  WORKER_EXCEPTION_FEEDBACK: WORKER_EXCEPTION_PIPELINE_AGENTS
}

const LOG_TAG_MAP = {
  INFO: '执行',
  DECISION: '派遣',
  WARN: '发现',
  ERROR: '异常',
  SERVICE_CALL: '服务'
}

const TERMINAL_STATUS = new Set(['SUCCESS', 'FAILED', 'MANUAL_REQUIRED'])

export function mapLogToStreamEntry(log) {
  const tag = LOG_TAG_MAP[log.logType] ?? '发现'
  const name = log.agentLabel || log.agentName
  return {
    id: `log-${log.logId}`,
    tag,
    name,
    agentName: log.agentName,
    content: log.content,
    type: 'log'
  }
}

export function buildEvidenceFromStep(step) {
  const items = []
  const label = step.agentLabel || step.agentName || 'Agent'
  const base = { source: label, credibility: step.status === 'SUCCESS' ? '可信度 92' : '需关注' }

  if (step.outputData) {
    try {
      const out = JSON.parse(step.outputData)

      if (out.processingContent) {
        items.push({
          ...base,
          id: `proc-${step.stepId}`,
          tag: '处理',
          title: out.processingContent,
          detail: `${label} 本步处理内容`
        })
      }

      if (out.conclusion) {
        items.push({
          ...base,
          id: `concl-${step.stepId}`,
          tag: '结论',
          title: out.conclusion,
          detail: out.nextFlow && out.nextFlow !== 'END'
            ? `下一步流转 → ${out.nextFlow}`
            : '流程本步结束或进入收尾'
        })
      }

      if (Array.isArray(out.dbEvidence) && out.dbEvidence.length) {
        items.push({
          ...base,
          id: `db-${step.stepId}`,
          tag: '依据',
          title: `数据库依据 ${out.dbEvidence.length} 项`,
          detail: out.dbEvidence.join(' · '),
          hoverType: 'db-evidence',
          raw: out.dbEvidence
        })
      }

      if (out.kittingRate != null) {
        items.push({
          ...base,
          id: `kit-${step.stepId}`,
          tag: '齐套',
          title: `齐套率 ${out.kittingRate}%`,
          detail: out.kittingComplete
            ? `InventoryAgent 已完成 ${out.inventorySummary?.length ?? '-'} 项物料齐套校验${
                out.shortageCount != null ? `，缺料 ${out.shortageCount} 项` : ''
              }，结果已回写生产计划`
            : '齐套校验结果已写入任务上下文'
        })
      }
      if (Array.isArray(out.shortageAnalysis) && out.shortageAnalysis.length) {
        const typeCount = {}
        for (const row of out.shortageAnalysis) {
          const typeLabel = row.shortageTypeLabel || row.shortageType || '真实缺料'
          typeCount[typeLabel] = (typeCount[typeLabel] || 0) + 1
          items.push({
            ...base,
            id: `short-${step.stepId}-${row.itemId ?? items.length}`,
            tag: '缺料',
            title: `物料 ${row.itemId ?? '—'} · ${typeLabel}`,
            detail: `需 ${row.requiredQty ?? '—'} / 可用 ${row.availableQty ?? '—'} / 缺 ${row.shortageQty ?? '—'}`,
            hoverType: 'shortage',
            shortageType: row.shortageType,
            shortageTypeLabel: typeLabel,
            raw: row
          })
        }
        const summary = Object.entries(typeCount)
          .map(([k, v]) => `${k} ${v}`)
          .join(' · ')
        items.push({
          ...base,
          id: `short-sum-${step.stepId}`,
          tag: '分型',
          title: `缺料分型汇总 · ${out.shortageAnalysis.length} 项`,
          detail: summary || 'InventoryAgent 已输出四类缺料分型',
          hoverType: 'shortage-summary',
          raw: { typeCount, rows: out.shortageAnalysis }
        })
      }
      if (out.requisitionId) {
        items.push({
          ...base,
          id: `req-${out.requisitionId}`,
          tag: '领料',
          title: `领料单已生成 · REQ-${out.requisitionId}`,
          detail: `领料单 ID ${out.requisitionId}，齐套校验通过后由 OrderPlanAgent 生成`,
          hoverType: 'requisition',
          requisitionId: out.requisitionId
        })
      }
      if (out.planNo || out.planId) {
        if (!out.requisitionId) {
          const displayPlanNo = formatPlanNo(out.planNo, out.planId)
          items.push({
            ...base,
            id: `plan-${displayPlanNo}`,
            tag: '计划',
            title: `生产计划 ${displayPlanNo}`,
            detail: `计划 ID ${out.planId ?? '—'}，OrderPlanAgent 根据订单 BOM 生成，尚未生成领料单`,
            hoverType: 'plan',
            planId: out.planId,
            planNo: displayPlanNo
          })
        }
      }
      if (out.outboundNo) {
        items.push({
          ...base,
          id: `out-${out.outboundNo}`,
          tag: '出库',
          title: `出库单 ${out.outboundNo}`,
          detail: `OutboundAgent 完成拣货复核，共 ${out.pickLineCount ?? '-'} 个拣货行`
        })
      }
      if (out.inventorySummary && out.kittingRate == null) {
        items.push({
          ...base,
          id: `inv-${step.stepId}`,
          tag: '库存',
          title: `库存核对 ${out.inventorySummary.length} 条`,
          detail: 'InventoryAgent 库存余额快照已写入任务上下文'
        })
      }
    } catch {
      // ignore parse errors
    }
  }

  if (step.errorMessage) {
    let raw = {}
    if (step.outputData) {
      try {
        raw = JSON.parse(step.outputData)
      } catch {
        raw = {}
      }
    }
    items.push({
      ...base,
      id: `err-${step.stepId}`,
      tag: '异常',
      title: step.errorMessage,
      detail: `${label} 步骤状态 ${step.status}`,
      credibility: '需人工',
      hoverType: 'error',
      raw
    })
  }

  if (!items.length && step.status === 'SUCCESS') {
    items.push({
      ...base,
      id: `step-${step.stepId}`,
      tag: '步骤',
      title: `${step.stepName || label} 执行完成`,
      detail: step.nextAgent ? `下一步 → ${step.nextAgent}` : '流程结束'
    })
  }

  return items
}

export function getExpectedPipelineAgents(taskType = 'ORDER_PLAN_REQUISITION') {
  return [...(PIPELINE_AGENTS_BY_TASK[taskType] ?? PMC_PIPELINE_AGENTS)]
}

export function getExpectedPipeline(taskType = 'ORDER_PLAN_REQUISITION') {
  return getExpectedPipelineAgents(taskType)
}

export {
  PMC_PIPELINE_AGENTS,
  OUTBOUND_PIPELINE_AGENTS,
  PIPELINE_AGENTS_BY_TASK,
  TERMINAL_STATUS
}
