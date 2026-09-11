<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { parseAgentReport } from '../utils/parseAgentReport'
import { normalizePlanText, renderReportMarkdown, sectionAnchorId } from '../utils/renderMarkdown'

const props = defineProps({
  variant: { type: String, default: 'embedded' },
  analysis: { type: String, default: '' },
  fallbackText: { type: String, default: '' },
  taskStatus: { type: String, default: '' },
  taskNo: { type: String, default: '' },
  evidenceCount: { type: Number, default: 0 },
  steps: { type: Array, default: () => [] }
})

const emit = defineEmits(['back'])

const mainRef = ref(null)
const activeSectionId = ref('summary')
const readProgress = ref(0)

const reportText = computed(() => normalizePlanText(props.analysis || props.fallbackText))
const parsed = computed(() => parseAgentReport(reportText.value))
const sections = computed(() => parsed.value.sections)

const renderedHtml = computed(() => renderReportMarkdown(reportText.value, sections.value))

const qaNote = computed(() => {
  if (props.taskStatus === 'MANUAL_REQUIRED') {
    return '质检触发人工介入：Agent 已暂停自动出库，已结合步骤日志生成分析，请 PMC 计划员复核后推进。'
  }
  if (props.taskStatus === 'SUCCESS') {
    return `任务 ${props.taskNo || ''} 已由领域智能体基于真实数据执行完成；报告由 DeepSeek 汇总步骤、日志与数据库依据生成。`
  }
  return '报告由 DeepSeek 基于 Agent 真实步骤与日志生成；库存/订单等核心判断以数据库与系统规则为准。'
})

function selectSection(section) {
  activeSectionId.value = section.id
  const anchor = sectionAnchorId(section)
  const el = mainRef.value?.querySelector(`#${anchor}`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

function updateScrollState() {
  const container = mainRef.value
  if (!container) return

  const maxScroll = container.scrollHeight - container.clientHeight
  readProgress.value = maxScroll > 0 ? Math.round((container.scrollTop / maxScroll) * 100) : 100

  const anchors = sections.value
    .map((section) => ({
      id: section.id,
      el: container.querySelector(`#${sectionAnchorId(section)}`)
    }))
    .filter((item) => item.el)

  const scrollTop = container.scrollTop + 120
  let current = sections.value[0]?.id ?? 'summary'
  for (const item of anchors) {
    if (item.el.offsetTop <= scrollTop) {
      current = item.id
    }
  }
  activeSectionId.value = current
}

watch(renderedHtml, () => {
  nextTick(updateScrollState)
})

onMounted(() => {
  mainRef.value?.addEventListener('scroll', updateScrollState, { passive: true })
  updateScrollState()
})

onUnmounted(() => {
  mainRef.value?.removeEventListener('scroll', updateScrollState)
})
</script>

<template>
  <section :class="['agent-report', variant === 'page' ? 'agent-report-page' : 'agent-report-embedded', 'stream-enter']">
    <div class="agent-report-layout">
      <aside class="agent-report-toc">
        <div v-if="variant === 'page'" class="agent-report-toc-top">
          <button class="report-back-button" type="button" aria-label="返回执行页" @click="emit('back')">‹</button>
          <strong>报告目录</strong>
        </div>
        <div v-else class="agent-report-toc-title">报告目录</div>

        <div class="agent-report-progress">
          <div class="agent-report-progress-head">
            <span>阅读进度</span>
            <em>{{ readProgress }}%</em>
          </div>
          <div class="agent-report-progress-track"><i :style="{ width: `${readProgress}%` }"></i></div>
        </div>

        <nav class="agent-report-toc-list">
          <button
            v-for="section in sections"
            :key="section.id"
            :class="['agent-report-toc-item', { active: section.id === activeSectionId }]"
            type="button"
            @click="selectSection(section)"
          >
            <span class="toc-no">{{ section.no }}</span>
            <span>{{ section.title }}</span>
          </button>
        </nav>

      </aside>

      <div ref="mainRef" class="agent-report-main">
        <header v-if="variant === 'page'" class="agent-report-page-head">
          <span class="agent-report-badge">DeepSeek 分析</span>
          <span class="agent-report-title">Agent 执行报告 · DeepSeek</span>
          <small v-if="taskNo">任务 {{ taskNo }}</small>
        </header>

        <header v-else class="agent-report-head">
          <span class="agent-report-badge">DeepSeek 分析</span>
          <span class="agent-report-title">Agent 执行报告 · DeepSeek</span>
        </header>

        <div :class="['agent-report-banner', taskStatus === 'MANUAL_REQUIRED' ? 'warn' : '']">
          <i>{{ taskStatus === 'MANUAL_REQUIRED' ? '⊘' : '✓' }}</i>
          <p>{{ qaNote }}</p>
        </div>

        <article class="agent-report-markdown markdown-body" v-html="renderedHtml"></article>

        <footer class="agent-report-foot">
          报告由 DeepSeek 基于 Agent 真实步骤、日志与证据生成；业务单据操作请以 WMS 系统记录为准。
        </footer>
      </div>
    </div>
  </section>
</template>
