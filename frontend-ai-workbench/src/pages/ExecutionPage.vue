<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../composables/useSession'
import { useAgentTask } from '../composables/useAgentTask'
import {
  buildExpertAvatars,
  buildPipelineStages,
  resolveAgentDisplay,
  resolveExpertFromStep,
  resolvePipelineIndex,
  stageStatus
} from '../data/executionPipeline'
import { normalizePlanText } from '../utils/renderMarkdown'
import { loadEvidenceHoverDetail } from '../utils/formatEvidenceHover'

const router = useRouter()
const { activeRole } = useSession()
const {
  agentLiveMode,
  agentTaskStatus,
  agentAnalysis,
  visibleLogs,
  currentEvidence,
  revealedSteps,
  showVerdict,
  showThinking,
  agentVerdict,
  decisionLogs,
  traceVisible,
  traceCollapsed,
  tracePosition,
  agentTaskNo,
  agentTaskType,
  agentSteps,
  agentLogs,
  liveExperts,
  isTaskRunning,
  displayTaskName,
  displayTaskId,
  goHome,
  startTraceDrag,
  goToGeneratedOutbound,
  lastGeneratedOutboundId
} = useAgentTask()

const streamRef = ref(null)
const evidenceHoverText = ref({})
const evidenceHoverLoading = ref({})

async function prefetchEvidenceHover(item) {
  if (!item?.id || evidenceHoverText.value[item.id] || evidenceHoverLoading.value[item.id]) return
  evidenceHoverLoading.value = { ...evidenceHoverLoading.value, [item.id]: true }
  try {
    const text = await loadEvidenceHoverDetail(item)
    evidenceHoverText.value = { ...evidenceHoverText.value, [item.id]: text }
  } finally {
    const next = { ...evidenceHoverLoading.value }
    delete next[item.id]
    evidenceHoverLoading.value = next
  }
}

function openAgentReport() {
  router.push('/execution/report')
}

function openOutboundDetail() {
  goToGeneratedOutbound()
}

const showOutboundJump = computed(() => {
  if (activeRole.value.id !== 'warehouse') return false
  if (!showVerdict.value) return false
  return agentTaskStatus.value === 'SUCCESS' || !!lastGeneratedOutboundId.value
})

function reportPreview() {
  const text = normalizePlanText(agentAnalysis.value || agentVerdict.value || '')
  return text.split('\n').find((line) => line.trim())?.trim() || 'DeepSeek 已生成 Agent 执行分析报告，可进入报告页查看完整目录与建议。'
}

function scrollStreamToBottom() {
  nextTick(() => {
    const el = streamRef.value
    if (!el) return
    el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
  })
}

watch(visibleLogs, () => scrollStreamToBottom(), { deep: true })
watch(showVerdict, (val) => {
  if (val) scrollStreamToBottom()
})

const pipelineStages = computed(() =>
  buildPipelineStages({
    agentLiveMode: agentLiveMode.value,
    revealedSteps: revealedSteps.value,
    agentSteps: agentSteps.value,
    roleId: activeRole.value.id,
    taskType: agentTaskType.value
  })
)

const executionPipelineIndex = computed(() =>
  resolvePipelineIndex({
    agentLiveMode: agentLiveMode.value,
    stages: pipelineStages.value,
    revealedSteps: revealedSteps.value,
    agentSteps: agentSteps.value
  })
)

function getStageState(index) {
  const stage = pipelineStages.value[index]
  if (stage?.status) {
    return stageStatus(index, executionPipelineIndex.value, stage)
  }
  if (index < executionPipelineIndex.value) return 'done'
  if (index === executionPipelineIndex.value && (isTaskRunning.value || agentLiveMode.value)) {
    return 'current'
  }
  return 'pending'
}

function stageAgent(index) {
  const state = getStageState(index)
  if (state === 'pending') return null

  const stage = pipelineStages.value[index]
  if (!stage) return null
  return resolveExpertFromStep(
    { agentName: stage.agentName, agentLabel: stage.agentLabel, stepName: stage.label },
    activeRole.value.persona
  )
}

const expertAvatars = computed(() => {
  if (agentLiveMode.value) {
    if (liveExperts.value.length) {
      return buildExpertAvatars(liveExperts.value, activeRole.value.persona)
    }
  }

  const agents = []
  const seen = new Set()
  for (let i = 0; i < pipelineStages.value.length; i += 1) {
    if (getStageState(i) === 'pending') continue
    const agent = stageAgent(i)
    if (!agent) continue
    const key = agent.agentKey || agent.name
    if (seen.has(key)) continue
    seen.add(key)
    agents.push(agent)
  }
  if (!agents.length) {
    agents.push(resolveAgentDisplay(null, activeRole.value.persona))
  }
  return agents
})


function tagClass(tag) {
  if (tag === '执行' || tag === '服务') return 'run'
  if (tag === '派遣') return 'assign'
  if (tag === '规划') return 'plan'
  if (tag === '异常') return 'error'
  return 'find'
}

function tagIcon(tag) {
  if (tag === '执行' || tag === '服务') return '⚙'
  if (tag === '派遣') return '↗'
  if (tag === '规划') return '✦'
  if (tag === '异常') return '!'
  return '⌕'
}

function streamAgent(log) {
  return resolveAgentDisplay(log[1], activeRole.value.persona, log[3])
}

const TASK_STATUS_LABEL = {
  RUNNING: '执行中',
  SUCCESS: '已完成',
  MANUAL_REQUIRED: '待人工复核',
  FAILED: '执行失败',
  PENDING: '等待中'
}

function formatTaskNo(value) {
  if (!value) return '—'
  const raw = String(value).trim().toUpperCase()
  if (/^AT\d{8}\d{4,5}$/.test(raw)) return raw
  const dateMatch = raw.match(/^AT(\d{8})/)
  if (dateMatch && raw.length > 16) {
    return `AT${dateMatch[1]}${raw.slice(-5).padStart(5, '0')}`
  }
  if (raw.length > 20) return `${raw.slice(0, 12)}…${raw.slice(-4)}`
  return raw
}

const taskStatusLabel = computed(() => TASK_STATUS_LABEL[agentTaskStatus.value] ?? agentTaskStatus.value)

const taskStatusTone = computed(() => {
  if (agentTaskStatus.value === 'SUCCESS') return 'ok'
  if (agentTaskStatus.value === 'MANUAL_REQUIRED' || agentTaskStatus.value === 'FAILED') return 'danger'
  if (agentTaskStatus.value === 'RUNNING') return 'warn'
  return 'neutral'
})
</script>

<template>
  <div class="execution-shell">
    <header class="execution-topbar">
      <div class="task-title-block">
        <button class="back-button" type="button" aria-label="返回" @click="goHome">‹</button>
        <div class="task-title-content">
          <h1 class="task-heading">{{ displayTaskName }}</h1>
          <div class="task-meta">
            <span class="task-no" :title="displayTaskId">{{ formatTaskNo(displayTaskId) }}</span>
            <span
              v-if="agentLiveMode && agentTaskStatus"
              :class="['task-status-chip', taskStatusTone]"
            >{{ taskStatusLabel }}</span>
          </div>
        </div>
      </div>
    </header>

    <div class="execution-board">
      <aside class="task-rail">
        <section class="rail-section">
          <h2><span class="section-icon">☰</span>任务流水线</h2>
          <div class="pipeline-list">
            <div
              v-for="(stage, index) in pipelineStages"
              :key="`${stage.label}-${index}`"
              :class="['pipeline-step', getStageState(index)]"
            >
              <div class="pipeline-track">
                <span class="pipeline-dot"></span>
              </div>
              <div class="pipeline-body">
                <span class="pipeline-label">{{ stage.label }}</span>
                <div v-if="stageAgent(index)" class="pipeline-agent">
                  <img :src="stageAgent(index).photo" :alt="stageAgent(index).name" />
                  <span>{{ stageAgent(index).name }}</span>
                </div>
              </div>
            </div>
          </div>
        </section>
        <section class="rail-section expert-team">
          <h2><span class="section-icon">👥</span>专家队（{{ expertAvatars.length }}）</h2>
          <div class="avatar-row">
            <img
              v-for="agent in expertAvatars"
              :key="agent.agentKey || agent.name"
              :src="agent.photo"
              :alt="agent.name"
              :title="agent.name"
            />
          </div>
        </section>
      </aside>

      <main ref="streamRef" class="thought-stream">
        <div class="stream-heading">
          <span><i class="heading-spark">✦</i> 实时协作思维流</span>
        </div>

        <article
          v-for="(log, index) in visibleLogs"
          :key="`${log[1]}-${index}`"
          class="stream-line stream-enter"
        >
          <img class="agent-avatar-img" :src="streamAgent(log).photo" :alt="streamAgent(log).name" />
          <div class="line-body">
            <div class="line-meta">
              <span :class="['tag', tagClass(log[0])]"><i>{{ tagIcon(log[0]) }}</i>{{ log[0] }}</span>
              <strong>{{ streamAgent(log).name }}</strong>
            </div>
            <p>{{ log[2] }}</p>
          </div>
        </article>

        <article v-if="showThinking" class="stream-line thinking-line">
          <span class="agent-avatar thinking-avatar">AI</span>
          <div class="line-body">
            <div class="line-meta">
              <span class="tag assign"><i>⚙</i>执行中</span>
              <strong>Agent 协同</strong>
            </div>
            <p class="thinking-dots"><i></i><i></i><i></i></p>
          </div>
        </article>

        <section v-if="showVerdict && agentLiveMode" class="report-entry-card stream-enter">
          <div class="report-entry-head">
            <span class="verdict-tag">DeepSeek 分析</span>
            <strong>Agent 执行报告 · DeepSeek</strong>
          </div>
          <p class="report-entry-preview">{{ reportPreview() }}</p>
          <div class="report-entry-actions">
            <button v-if="activeRole.id === 'pmc'" class="report-entry-primary" type="button" @click="openAgentReport">
              查看完整分析报告 →
            </button>
            <button v-else class="report-entry-primary" type="button" @click="openAgentReport">查看报告 →</button>
            <button
              v-if="showOutboundJump"
              class="report-entry-secondary"
              type="button"
              @click="openOutboundDetail"
            >查看出库单详情 →</button>
            <small>含执行摘要、关键发现、风险点与建议措施，左侧目录可跳转阅读。</small>
          </div>
        </section>

        <section v-else-if="showVerdict" class="verdict-card stream-enter">
          <div class="verdict-head">
            <span class="verdict-tag">{{ activeRole.verdictTag }}</span>
            <strong>AI 综合结论</strong>
          </div>
          <p class="verdict-text">{{ agentVerdict }}</p>
          <small>结论基于 {{ currentEvidence.length }} 条已锁定证据，可逐条追溯至单据、服务日志与审计记录。</small>
          <div v-if="showOutboundJump" class="report-entry-actions" style="margin-top: 14px">
            <button class="report-entry-primary" type="button" @click="openOutboundDetail">查看出库单详情 →</button>
          </div>
        </section>
      </main>

      <aside class="evidence-panel">
        <div class="panel-title">
          <span>证据库（{{ currentEvidence.length }}）</span>
        </div>
        <div v-if="currentEvidence.length === 0" class="empty-evidence">
          <span>▧</span>
          <p>{{ showThinking ? '证据采集中，Agent 正在写入……' : '证据采集中，稍候片刻……' }}</p>
        </div>
        <article
          v-for="(item, index) in currentEvidence"
          :key="item.id || item.title || index"
          class="evidence-item stream-enter"
          @mouseenter="prefetchEvidenceHover(item)"
        >
          <div class="evidence-meta">
            <span>{{ item.tag }}</span>
            <small>{{ item.id }}</small>
            <em>{{ item.credibility }}</em>
          </div>
          <strong>{{ item.title }}</strong>
          <p>{{ item.detail || '该证据已被 Agent 纳入当前任务上下文，可用于追溯、审计与复核。' }}</p>
          <div v-if="item.hoverType" class="evidence-hover-panel">
            <pre>{{ evidenceHoverText[item.id] || (evidenceHoverLoading[item.id] ? '加载中…' : '悬停查看业务详情') }}</pre>
          </div>
        </article>
      </aside>
    </div>

    <aside
      v-if="traceVisible"
      :class="['floating-trace', { collapsed: traceCollapsed }]"
      :style="{ left: `${tracePosition.x}px`, top: `${tracePosition.y}px` }"
    >
      <div class="trace-head" @pointerdown="startTraceDrag">
        <strong>Agent 决策日志 · Trace</strong>
        <span>{{ agentSteps.length || decisionLogs.length }} 步</span>
        <button type="button" :aria-label="traceCollapsed ? '展开' : '收起'" @click="traceCollapsed = !traceCollapsed">⌄</button>
        <button type="button" aria-label="关闭" @click="traceVisible = false">×</button>
      </div>
      <div v-show="!traceCollapsed" class="trace-body">
        <div class="trace-stats">
          <span v-if="agentTaskNo">任务 {{ agentTaskNo }}</span>
          <span>{{ agentLogs.length }} 次调用</span>
        </div>
        <div v-if="!decisionLogs.length" class="trace-waiting">等待 Agent 开始调用……</div>
        <div v-for="item in decisionLogs" :key="item[0]" class="trace-row">
          <b>{{ item[0] }}</b>
          <span>{{ item[1] }}</span>
          <small>{{ item[2] }}</small>
          <em>{{ item[3] }}</em>
        </div>
      </div>
    </aside>
  </div>
</template>
