<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../composables/useSession'
import { useDisplayName, useViewRole } from '../composables/useRoleAccess'
import { usePmc } from '../composables/usePmc'
import { useWarehouse } from '../composables/useWarehouse'
import { useAgentTask } from '../composables/useAgentTask'
import { demoPreserveRoute, menuToPath } from '../router/menuRoutes'
import { renderAssistantMarkdown } from '../utils/renderMarkdown'

const router = useRouter()
const { prompt, useExample } = useSession()
const { viewRole, isAdminDemo, demoRoleId } = useViewRole()
const { displayName } = useDisplayName()
const { pendingReview, inboxHint, lastIntegrationSyncAt, syncLoading, syncError, loadPmcData, syncOrdersFromErpMes } = usePmc()
const { pendingCount, pickingPendingCount, exceptionOpenCount, loadWarehouseData } = useWarehouse()
const {
  startTask,
  agentTaskLoading,
  agentTaskError,
  assistantReply,
  assistantIntent,
  pendingAssistantAction,
  assistantReplyRoleId,
  confirmAssistantAction,
  cancelPmcAssistantAction,
  clearAssistantState
} = useAgentTask()

loadPmcData()
loadWarehouseData()

watch(
  () => viewRole.value.id,
  () => {
    if (isAdminDemo.value) {
      prompt.value = viewRole.value.defaultPrompt
    }
    clearAssistantState()
  },
  { immediate: true }
)

const intentLabels = {
  answer: '问答',
  action: '待执行',
  clarify: '需补充',
  forbidden: '无权限'
}

const intentLabel = computed(() => intentLabels[assistantIntent.value] || assistantIntent.value || '问答')

const timeGreeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  if (hour < 22) return '晚上好'
  return '夜深了'
})

const assistantReplyHtml = computed(() => renderAssistantMarkdown(assistantReply.value))

const confirmMessageText = computed(
  () => pendingAssistantAction.value?.confirmMessage || '确认启动 Agent 流水线？'
)

const showConfirmActions = computed(
  () => assistantIntent.value === 'action' && Boolean(pendingAssistantAction.value)
)

const showAssistantPanel = computed(() => {
  if (assistantReplyRoleId.value && assistantReplyRoleId.value !== demoRoleId.value) {
    return false
  }
  return Boolean(assistantReply.value?.trim()) || Boolean(assistantIntent.value) || agentTaskLoading.value
})

async function syncErpMes() {
  try {
    await syncOrdersFromErpMes()
  } catch {
    // syncError 已写入
  }
}

const modelOptions = ['deepseek-v4-pro']
const selectedModel = ref(modelOptions[0])
const showModelMenu = ref(false)

function goPlanCenter() {
  router.push(demoPreserveRoute(menuToPath('订单计划'), demoRoleId.value, isAdminDemo.value))
}

function goOutboundOrders() {
  router.push(demoPreserveRoute(menuToPath('出库单'), demoRoleId.value, isAdminDemo.value))
}

function toggleModelMenu() {
  showModelMenu.value = !showModelMenu.value
}

function selectModel(model) {
  selectedModel.value = model
  showModelMenu.value = false
}
</script>

<template>
  <div>
    <div v-if="viewRole.id === 'pmc'" class="inbox-alert">
      <span class="inbox-dot"></span>
      <p>
        {{ inboxHint || (pendingReview > 0
          ? `你有 ${pendingReview} 条新订单 / 计划待审核。`
          : '暂无待审核订单。') }}
        <small v-if="lastIntegrationSyncAt" class="sync-hint">最近同步 {{ lastIntegrationSyncAt }}</small>
      </p>
      <button type="button" :disabled="syncLoading" @click="syncErpMes">
        {{ syncLoading ? '同步中…' : '同步 ERP/MES' }}
      </button>
      <button type="button" @click="goPlanCenter">去审核 →</button>
    </div>
    <p v-if="viewRole.id === 'pmc' && syncError" class="data-hint danger">{{ syncError }}</p>
    <div v-else-if="viewRole.id === 'warehouse'" class="inbox-alert">
      <span class="inbox-dot"></span>
      <p>
        今日待办：待生成出库单 <b>{{ pendingCount }}</b>、
        待拣货 <b>{{ pickingPendingCount }}</b>、
        待处理异常 <b>{{ exceptionOpenCount }}</b>
      </p>
      <button type="button" @click="goOutboundOrders">去出库单 →</button>
    </div>
    <section class="hero-copy">
      <h1>{{ timeGreeting }}，{{ displayName }}</h1>
    </section>

    <section class="mission-band">
      <div class="mission-head">
        <span class="mission-label">岗位使命</span>
        <strong>{{ viewRole.mission }}</strong>
      </div>
      <ul class="mission-points">
        <li v-for="point in viewRole.missionPoints" :key="point">{{ point }}</li>
      </ul>
      <div v-if="viewRole.id !== 'pmc'" class="agent-chain">
        <span class="chain-label">Agent 调用链</span>
        <div class="chain-flow">
          <template v-for="(agent, index) in viewRole.agentChain" :key="agent">
            <span class="chain-node">{{ agent }}</span>
            <i v-if="index < viewRole.agentChain.length - 1" class="chain-arrow">→</i>
          </template>
        </div>
      </div>
    </section>

    <section class="prompt-card">
      <textarea v-model="prompt" aria-label="AI 调度指令"></textarea>
      <p v-if="agentTaskError" class="data-hint danger">{{ agentTaskError }}</p>
      <div class="prompt-toolbar">
        <div class="model-picker">
          <button class="model-button" type="button" @click="toggleModelMenu">切换模型</button>
          <ul v-if="showModelMenu" class="model-menu">
            <li
              v-for="model in modelOptions"
              :key="model"
              :class="{ selected: model === selectedModel }"
              @click="selectModel(model)"
            >
              {{ model }}
            </li>
          </ul>
        </div>
        <button class="attach-button" type="button">⌘</button>
        <button class="send-button" type="button" :disabled="agentTaskLoading" @click="startTask">
          {{ agentTaskLoading ? '…' : '↑' }}
        </button>
      </div>
    </section>

    <section v-if="showAssistantPanel" class="assistant-panel" aria-live="polite">
      <header class="assistant-panel-head">
        <strong>工作台助手 · {{ displayName }}</strong>
        <span
          v-if="assistantIntent && !agentTaskLoading"
          class="assistant-intent-tag"
          :data-intent="assistantIntent"
        >{{ intentLabel }}</span>
        <span v-else-if="agentTaskLoading" class="assistant-intent-tag">分析中</span>
      </header>

      <p v-if="agentTaskLoading && !assistantReply" class="data-hint">正在理解意图并查询业务数据…</p>

      <div
        v-else-if="assistantReplyHtml"
        class="assistant-reply markdown-body"
        v-html="assistantReplyHtml"
      ></div>

      <div v-if="showConfirmActions" class="assistant-confirm">
        <p>{{ confirmMessageText }}</p>
        <div class="assistant-confirm-actions">
          <button
            type="button"
            class="assistant-confirm-btn"
            :disabled="agentTaskLoading"
            @click="confirmAssistantAction"
          >{{ agentTaskLoading ? '启动中…' : '确认执行 Agent' }}</button>
          <button
            type="button"
            class="assistant-cancel-btn"
            :disabled="agentTaskLoading"
            @click="cancelPmcAssistantAction"
          >取消</button>
        </div>
      </div>
    </section>

    <section class="example-section">
      <span>快速选择功能</span>
      <div class="example-grid">
        <button v-for="item in viewRole.examples" :key="item[0]" type="button" @click="useExample(item[1])">
          <span>{{ item[0].slice(0, 1) }}</span>
          <strong>{{ item[0] }}</strong>
          <small>{{ item[1] }}</small>
        </button>
      </div>
    </section>
  </div>
</template>
