export const ISSUE_STAGE = {
  PRE_PUTAWAY: 'PRE_PUTAWAY',
  SHELVED: 'SHELVED'
}

/** 未入库：来料检不合格，禁止上架场景 */
export const prePutawayIssueActions = [
  {
    key: 'return',
    label: '生成供应商退货申请',
    desc: '提交给采购/QE 评审后发起来料退货（RTV）',
    icon: '↩'
  },
  {
    key: 'repair',
    label: '生成返修工单申请',
    desc: '提交给 QE/生产确认返修方案后再处置',
    icon: '🔧'
  },
  {
    key: 'isolate',
    label: '禁止上架并隔离',
    desc: '通知仓管员将待退物料转入隔离区，禁止入库上架',
    icon: '🚫'
  }
]

/** 已入库：在库批次需冻结后再处置 */
export const shelvedIssueActions = [
  {
    key: 'freeze',
    label: '生成库存冻结申请',
    desc: '提交给仓管员复核并冻结在库可用数量',
    icon: '❄'
  },
  {
    key: 'return',
    label: '生成供应商退货申请',
    desc: '冻结后提交给采购/QE 评审并发起退货',
    icon: '↩'
  },
  {
    key: 'repair',
    label: '生成返修工单申请',
    desc: '冻结后提交给 QE/生产确认返修方案',
    icon: '🔧'
  }
]

/** @deprecated 请使用 getIssueActionsForStage */
export const issueActions = shelvedIssueActions

export function getIssueActionsForStage(stage) {
  return stage === ISSUE_STAGE.SHELVED ? shelvedIssueActions : prePutawayIssueActions
}

export function getIssueStageHint(stage) {
  if (stage === ISSUE_STAGE.SHELVED) {
    return '该批次已入库，建议先冻结在库库存，再选择退货或返修。'
  }
  return '该批次尚未入库上架，无需冻结库存。请选择退货、返修或禁止上架隔离。'
}

/** 标准化缺陷代码（ISO / 厂内缺陷库） */
export const DEFECT_CODES = [
  { code: 'SCR', label: '划伤', category: '外观', severity: 'minor' },
  { code: 'BUR', label: '毛刺', category: '外观', severity: 'minor' },
  { code: 'CRK', label: '裂纹', category: '外观', severity: 'critical' },
  { code: 'RST', label: '锈蚀', category: '外观', severity: 'major' },
  { code: 'DFT', label: '变形', category: '外观', severity: 'major' },
  { code: 'DIM', label: '尺寸超差', category: '尺寸', severity: 'major' },
  { code: 'THR', label: '螺纹不合格', category: '尺寸', severity: 'critical' },
  { code: 'SRF', label: '表面粗糙度超差', category: '尺寸', severity: 'minor' },
  { code: 'PRF', label: '性能不达标', category: '性能', severity: 'critical' },
  { code: 'PKG', label: '包装破损', category: '包装', severity: 'minor' },
  { code: 'IDN', label: '标识缺失/错误', category: '标识', severity: 'major' },
  { code: 'OTH', label: '其他', category: '其他', severity: 'minor' }
]

/** AQL 抽样等级说明 */
export const AQL_LEVELS = {
  II: { label: '一般检验水平 II', desc: '正常抽检' },
  I: { label: '一般检验水平 I', desc: '放宽抽检' },
  III: { label: '一般检验水平 III', desc: '加严抽检' },
  S4: { label: '特殊检验水平 S-4', desc: '破坏性/高成本检验' }
}

/**
 * 按批量与检验水平估算样本量（演示用简化表，非完整 GB/T 2828.1）
 */
export function calcAqlSampleSize(lotSize, level = 'II', aql = 1.5) {
  const n = Number(lotSize) || 0
  let sampleSize
  if (level === 'III' || level === 'S4') {
    if (n <= 50) sampleSize = Math.min(n, 13)
    else if (n <= 150) sampleSize = 32
    else if (n <= 500) sampleSize = 50
    else sampleSize = 80
  } else if (level === 'I') {
    if (n <= 50) sampleSize = Math.min(n, 5)
    else if (n <= 150) sampleSize = 13
    else if (n <= 500) sampleSize = 20
    else sampleSize = 32
  } else {
    if (n <= 50) sampleSize = Math.min(n, 8)
    else if (n <= 150) sampleSize = 20
    else if (n <= 500) sampleSize = 32
    else sampleSize = 50
  }
  const accept = aql <= 1.0 ? 0 : aql <= 2.5 ? 1 : 2
  const reject = accept + 1
  return { sampleSize, acceptNumber: accept, rejectNumber: reject, aql, level }
}
