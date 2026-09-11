const SECTION_DEFS = [
  { id: 'summary', no: 1, title: '执行摘要 · 核心判断', keys: ['执行摘要', '摘要', '核心判断'] },
  { id: 'task', no: 2, title: '用户任务与任务类型', keys: ['用户任务', '任务类型'] },
  { id: 'steps', no: 3, title: '参与智能体与执行步骤', keys: ['参与智能体', '执行步骤'] },
  { id: 'evidence', no: 4, title: '数据库依据', keys: ['数据库依据', '数据依据'] },
  { id: 'decisions', no: 5, title: '关键决策', keys: ['关键决策', '关键发现', '发现的问题', '主要发现'] },
  { id: 'risks', no: 6, title: '风险点', keys: ['风险点', '风险提示', '风险', '异常'] },
  { id: 'result', no: 7, title: '最终结果', keys: ['最终结果', '结果'] },
  { id: 'actions', no: 8, title: '建议措施', keys: ['建议措施', '处理建议', '改进建议', '可执行改进建议', '后续建议'] }
]

function normalizeLine(line = '') {
  return line.trim().replace(/^[-*•\d.、)\]]+\s*/, '')
}

function splitItems(text = '') {
  return text
    .split(/\n+/)
    .map(normalizeLine)
    .filter(Boolean)
}

function matchSectionTitle(line) {
  const cleaned = line.replace(/^#+\s*/, '').replace(/[:：]\s*$/, '').trim()
  for (const def of SECTION_DEFS) {
    if (def.keys.some((key) => cleaned.includes(key))) {
      return def
    }
  }
  return null
}

export function parseAgentReport(text = '') {
  const sections = SECTION_DEFS.map((def) => ({ ...def, content: '', items: [] }))
  const lines = text.split(/\n/)
  let current = sections[0]
  const preamble = []

  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) continue

    const matched = matchSectionTitle(line)
    if (matched) {
      current = sections.find((item) => item.id === matched.id) ?? current
      const inline = line.replace(/^#+\s*/, '').split(/[:：]/).slice(1).join('：').trim()
      if (inline) current.content = inline
      continue
    }

    if (current.content || current.items.length) {
      current.items.push(...splitItems(line))
    } else if (current.id === sections[0].id && !sections.some((s) => s.content || s.items.length)) {
      preamble.push(line)
    } else {
      current.items.push(...splitItems(line))
    }
  }

  if (preamble.length && !sections[0].content && !sections[0].items.length) {
    sections[0].content = preamble.join('\n')
  }

  for (const section of sections) {
    if (!section.content && section.items.length) {
      section.content = section.items[0]
      section.items = section.items.slice(1)
    }
  }

  const filled = sections.filter((section) => section.content || section.items.length)
  const activeId = filled[0]?.id ?? 'summary'

  return {
    sections: filled.length ? filled : sections.slice(0, 1),
    activeId,
    summary: sections[0].content || sections[0].items.join('\n') || text,
    findings: sections.find((s) => s.id === 'decisions' || s.id === 'findings')?.items ?? [],
    risks: sections.find((s) => s.id === 'risks')?.items ?? [],
    actions: sections.find((s) => s.id === 'actions')?.items ?? []
  }
}

export function buildReportMetrics({ taskStatus, evidenceCount = 0, stepCount = 0, successCount = 0 }) {
  const completeness = stepCount ? Math.round((successCount / stepCount) * 100) : 0
  const confidenceBase = Math.min(95, 52 + evidenceCount * 8 + successCount * 6)
  const confidence =
    taskStatus === 'SUCCESS' ? Math.min(95, confidenceBase + 12) : Math.max(28, confidenceBase - (taskStatus === 'MANUAL_REQUIRED' ? 18 : 8))
  const crossCheck = Math.max(20, Math.min(90, confidence - 12 + Math.floor(evidenceCount / 2) * 5))

  return [
    {
      label: '结论置信度',
      value: confidence,
      hint: taskStatus === 'MANUAL_REQUIRED' ? '需人工复核' : '基于步骤与证据'
    },
    {
      label: '结构化完整度',
      value: completeness || (evidenceCount ? 72 : 48),
      hint: `${successCount}/${stepCount || '-'} 步完成`
    },
    {
      label: '交叉验证',
      value: crossCheck,
      hint: `${evidenceCount} 条证据`
    }
  ]
}
