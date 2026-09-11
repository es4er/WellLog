import { marked } from 'marked'

const SECTION_SLUGS = {
  summary: 'section-summary',
  findings: 'section-findings',
  risks: 'section-risks',
  actions: 'section-actions'
}

marked.setOptions({
  breaks: true,
  gfm: true
})

function sectionToMarkdown(section) {
  const lines = []
  lines.push(`## ${section.title}`)
  if (section.content) lines.push(section.content)
  if (section.items?.length) {
    lines.push(section.items.map((item) => `- ${item}`).join('\n'))
  }
  return lines.join('\n\n')
}

export function buildReportMarkdown(text = '', sections = []) {
  const raw = (text || '').trim()
  if (raw.includes('##') || raw.includes('**') || raw.includes('- ')) {
    return raw
  }
  if (sections.length) {
    return sections.map(sectionToMarkdown).join('\n\n')
  }
  return raw
}

export function sectionAnchorId(section) {
  return SECTION_SLUGS[section.id] || `section-${section.id}`
}

export function renderReportMarkdown(text = '', sections = []) {
  const markdown = buildReportMarkdown(text, sections)
  let html = marked.parse(markdown)
  let index = 0
  html = html.replace(/<h2(\s[^>]*)?>/g, (match) => {
    const section = sections[index]
    index += 1
    if (!section) return match
    return `<h2 id="${sectionAnchorId(section)}"`
  })
  return html
}

export function formatPlanNo(value, planId) {
  if (value == null || value === '') return value
  const raw = String(value).trim().toUpperCase()
  if (/^PP\d{8}\d{3,5}$/.test(raw)) {
    const seq = raw.slice(10)
    const datePart = raw.slice(2, 10)
    return `PP${datePart}${String(Number(seq)).padStart(5, '0')}`
  }

  const dateMatch = raw.match(/^PP(\d{8})/)
  const datePart = dateMatch?.[1] ?? new Date().toISOString().slice(0, 10).replace(/-/g, '')
  const seq = planId != null && planId > 0 ? Number(planId) : 1
  return `PP${datePart}${String(seq).padStart(5, '0')}`
}

export function normalizePlanText(text = '') {
  return String(text).replace(/PP\d{10,}/gi, (match) => formatPlanNo(match))
}

/** 工作台助手问答回复 Markdown 渲染 */
export function renderAssistantMarkdown(text = '') {
  const normalized = normalizePlanText(String(text || '').trim())
  if (!normalized) return ''
  return marked.parse(normalized)
}
