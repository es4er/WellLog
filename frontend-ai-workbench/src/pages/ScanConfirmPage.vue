<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../composables/useSession'
import { useWorker } from '../composables/useWorker'
import { useAgentTask } from '../composables/useAgentTask'
import { menuToPath } from '../router/menuRoutes'

const router = useRouter()
const { activeRole, prompt } = useSession()
const { startWorkerScanAgentTask } = useAgentTask()
const allowed = computed(() => activeRole.value.id === 'worker')

const {
  activeTask,
  activeMaterial,
  currentScanProgress,
  taskScanRecords,
  lastScanFeedback,
  workerDataLoading,
  loadWorkerData,
  setActiveTask,
  scanBarcode,
  pickingTasks,
  workerId,
  resolvePickingTaskId,
  resolvePickingLineId
} = useWorker()

const barcodeInput = ref('')
const scanning = ref(false)

onMounted(() => {
  if (!allowed.value) {
    router.replace('/module/' + encodeURIComponent('扫码确认'))
    return
  }
  loadWorkerData()
  if (!activeTask.value && pickingTasks.value.length) {
    setActiveTask(pickingTasks.value[0].id)
  }
})

async function handleScan() {
  if (!barcodeInput.value.trim() || scanning.value) return
  scanning.value = true
  try {
    await scanBarcode(barcodeInput.value)
    if (lastScanFeedback.value?.success) {
      barcodeInput.value = ''
    }
  } finally {
    scanning.value = false
  }
}

function handleKeydown(event) {
  if (event.key === 'Enter') handleScan()
}

function goPicking() {
  router.push(menuToPath('我的领料'))
}

function launchAgent() {
  const task = activeTask.value
  const material = activeMaterial.value
  if (!task || !material) return
  prompt.value = `确认 ${task.workOrder} 的 ${material.material} 扫码交接`
  startWorkerScanAgentTask({
    pickingTaskId: resolvePickingTaskId(task),
    pickingLineId: resolvePickingLineId(material),
    barcodeValue: barcodeInput.value || `${material.batch}-SCAN`,
    workOrder: task.workOrder,
    workerUserId: workerId.value,
    promptText: prompt.value
  })
}

const progressPercent = computed(() => {
  const { required, scanned } = currentScanProgress.value
  if (!required) return 0
  return Math.min(100, Math.round((scanned / required) * 100))
})
</script>

<template>
  <section v-if="allowed" class="worker-board worker-scan-page">
    <p v-if="workerDataLoading" class="data-hint">正在加载扫码任务...</p>

    <header class="plan-header">
      <div>
        <h1>扫码确认</h1>
      </div>
      <button type="button" class="worker-link-btn" @click="goPicking">← 返回我的领料</button>
    </header>

    <div v-if="activeTask" class="worker-scan-context">
      <div class="worker-scan-context-row">
        <span class="worker-context-tag">当前任务</span>
        <strong>{{ activeTask.workOrder }}</strong>
        <strong>{{ activeTask.requisition }}</strong>
      </div>
      <div v-if="activeMaterial" class="worker-scan-material">
        <div class="worker-material-main">
          <span>当前物料</span>
          <strong>{{ activeMaterial.material }}</strong>
        </div>
        <div class="worker-material-stats">
          <div><small>应领</small><strong>{{ currentScanProgress.required }} 件</strong></div>
          <div><small>已扫</small><strong>{{ currentScanProgress.scanned }} 件</strong></div>
          <div class="remaining"><small>剩余</small><strong>{{ currentScanProgress.remaining }} 件</strong></div>
        </div>
        <div class="worker-progress-bar">
          <i :style="{ width: `${progressPercent}%` }"></i>
        </div>
      </div>
    </div>

    <div class="worker-scan-zone">
      <label for="barcode-input">请扫描或输入条码 / RFID 标签</label>
      <div class="worker-scan-input-row">
        <input
          id="barcode-input"
          v-model="barcodeInput"
          type="text"
          placeholder="B20260706004-M-EXPROOF-0001"
          autocomplete="off"
          @keydown="handleKeydown"
        />
        <button type="button" class="worker-btn primary large" :disabled="scanning" @click="handleScan">
          {{ scanning ? '校验中...' : '扫码' }}
        </button>
      </div>
      <p class="worker-scan-hint">示例：B20260706004-M-EXPROOF-0001 · BC0001 · BC9999（物料不匹配）</p>
    </div>

    <div v-if="lastScanFeedback" :class="['worker-scan-result', lastScanFeedback.success ? 'ok' : 'danger']">
      <strong>{{ lastScanFeedback.success ? '扫码成功' : '扫码异常' }}</strong>
      <ul>
        <li v-for="(msg, index) in lastScanFeedback.messages" :key="index">{{ msg }}</li>
      </ul>
    </div>

    <div class="worker-agent-strip">
      <span>Agent 调用链</span>
      <div class="agent-chain launch-chain">
        <template v-for="(agent, index) in ['SmartWarehouseAgent', 'InventoryAgent', 'AuditAgent']" :key="agent">
          <span class="chain-node">{{ agent }}</span>
          <i v-if="index < 2" class="chain-arrow">→</i>
        </template>
      </div>
    </div>

    <section class="worker-scan-records">
      <h2>扫码记录</h2>
      <div class="worker-detail-table">
        <div class="worker-detail-row head scan-row">
          <span>时间</span>
          <span>条码</span>
          <span>物料</span>
          <span>批次</span>
          <span>结果</span>
        </div>
        <div v-for="record in taskScanRecords" :key="record.id" class="worker-detail-row scan-row">
          <span>{{ record.time }}</span>
          <span class="mono">{{ record.barcode }}</span>
          <span>{{ record.material }}</span>
          <span>{{ record.batch }}</span>
          <span><i :class="['plan-status', record.success ? 'ok' : 'danger']">{{ record.result }}</i></span>
        </div>
      </div>
    </section>

    <div class="worker-scan-footer">
      <button type="button" class="worker-btn secondary" @click="launchAgent">交给 Agent 协助</button>
    </div>
  </section>
</template>
