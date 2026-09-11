import { apiGet } from '../api'

const cache = new Map()

function stripDecimal(value) {
  if (value == null) return '—'
  const num = Number(value)
  return Number.isFinite(num) ? String(num).replace(/\.0+$/, '').replace(/(\.\d*?)0+$/, '$1') : String(value)
}

function formatRequisitionDetail(data) {
  const req = data?.requisition ?? {}
  const lines = data?.lines ?? []
  const rows = lines.map(
    (line, index) =>
      `${index + 1}. 物料 ${line.itemId} · 需求 ${stripDecimal(line.requiredQty)} · 状态 ${line.lineStatus ?? 'OPEN'}`
  )
  return [
    `领料单号：${req.requisitionNo ?? '—'}`,
    `状态：${req.requisitionStatus ?? '—'}`,
    `申请部门：${req.requisitionDept ?? '—'}`,
    `来源计划 ID：${req.sourcePlanId ?? '—'}`,
    `明细 ${lines.length} 行：`,
    ...(rows.length ? rows : ['（暂无明细行）'])
  ].join('\n')
}

function formatPlanDetail(data) {
  const plan = data?.plan ?? {}
  const lines = data?.lines ?? []
  const rows = lines.map(
    (line, index) =>
      `${index + 1}. 物料 ${line.itemId} · 需求 ${stripDecimal(line.requiredQty)} · 交期 ${line.dueDate ?? '—'}`
  )
  return [
    `计划编号：${plan.planNo ?? '—'}`,
    `状态：${plan.planStatus ?? '—'}`,
    `计划开工：${plan.plannedStartDate ?? '—'}`,
    `计划完工：${plan.plannedEndDate ?? '—'}`,
    `明细 ${lines.length} 行：`,
    ...(rows.length ? rows : ['（暂无明细行）'])
  ].join('\n')
}

function formatErrorDetail(item) {
  const lines = [item.title || '异常信息']
  if (item.detail) lines.push(item.detail)
  if (item.raw?.shortItemId) lines.push(`短缺物料 ID：${item.raw.shortItemId}`)
  if (item.raw?.outboundId) lines.push(`出库单 ID：${item.raw.outboundId}`)
  return lines.join('\n')
}

function formatShortageDetail(item) {
  const raw = item?.raw || {}
  const lines = [
    item.title || '缺料明细',
    `分型：${item.shortageTypeLabel || raw.shortageTypeLabel || raw.shortageType || '—'}`,
    `需求数量：${stripDecimal(raw.requiredQty)}`,
    `齐套可用：${stripDecimal(raw.availableQty)}`,
    `缺料数量：${stripDecimal(raw.shortageQty)}`
  ]
  if (raw.pendingInspectionQty != null) lines.push(`待检未放行：${stripDecimal(raw.pendingInspectionQty)}`)
  if (raw.qualityBlockedQty != null) lines.push(`质量异常量：${stripDecimal(raw.qualityBlockedQty)}`)
  if (raw.locationUnavailableQty != null) lines.push(`库位不可用量：${stripDecimal(raw.locationUnavailableQty)}`)
  if (raw.onhandQty != null) lines.push(`账面在库：${stripDecimal(raw.onhandQty)}`)
  return lines.join('\n')
}

function formatShortageSummary(item) {
  const raw = item?.raw || {}
  const lines = [item.title || '缺料分型汇总', item.detail || '']
  const rows = raw.rows || []
  rows.slice(0, 8).forEach((row, index) => {
    lines.push(
      `${index + 1}. 物料 ${row.itemId} · ${row.shortageTypeLabel || row.shortageType} · 需 ${stripDecimal(row.requiredQty)} / 缺 ${stripDecimal(row.shortageQty)}`
    )
  })
  if (rows.length > 8) lines.push(`…其余 ${rows.length - 8} 项`)
  return lines.filter(Boolean).join('\n')
}

export async function loadEvidenceHoverDetail(item) {
  if (!item?.id) return ''
  if (cache.has(item.id)) return cache.get(item.id)

  try {
    if (item.hoverType === 'requisition' && item.requisitionId) {
      const data = await apiGet(`/requisition/detail?requisitionId=${item.requisitionId}`)
      const text = formatRequisitionDetail(data)
      cache.set(item.id, text)
      return text
    }
    if (item.hoverType === 'plan' && item.planId) {
      const data = await apiGet(`/plan/detail?planId=${item.planId}`)
      const text = formatPlanDetail(data)
      cache.set(item.id, text)
      return text
    }
    if (item.hoverType === 'shortage') {
      const text = formatShortageDetail(item)
      cache.set(item.id, text)
      return text
    }
    if (item.hoverType === 'shortage-summary') {
      const text = formatShortageSummary(item)
      cache.set(item.id, text)
      return text
    }
    if (item.hoverType === 'error') {
      const text = formatErrorDetail(item)
      cache.set(item.id, text)
      return text
    }
  } catch (error) {
    const text = item.detail || error.message || '暂无详情'
    cache.set(item.id, text)
    return text
  }

  const fallback = item.detail || '暂无业务详情'
  cache.set(item.id, fallback)
  return fallback
}
