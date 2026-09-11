<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRoleAccess } from '../composables/useRoleAccess'
import { useWarehouse } from '../composables/useWarehouse'
import WhWorkerNotifySelect from '../components/WhWorkerNotifySelect.vue'
import WhPrepLocationSelect from '../components/WhPrepLocationSelect.vue'

const route = useRoute()
const router = useRouter()
const { allowed } = useRoleAccess('warehouse')

const {
  pickingTasks,
  activePickingTask,
  activePickingLine,
  activePickingProgress,
  lastWarehouseScanFeedback,
  warehouseTip,
  warehouseDataLoading,
  loadWarehouseData,
  loadWorkerOptions,
  setActivePickingTask,
  setActivePickingLine,
  startWarehousePicking,
  submitWarehouseScan,
  completeWarehousePicking,
  fetchPrepRecommend,
  prepRecommend,
  prepRecommendLoading,
  statusClass,
  workerOptions,
  workersLoading
} = useWarehouse()

const barcodeInput = ref('')
const pickQty = ref(1)
const scanning = ref(false)
const scannedOnce = ref(false)
const selectedWorkerId = ref('')
const selectedPrepLocationId = ref('')

const pendingPickTasks = computed(() => pickingTasks.value.filter((t) => t.status === '待拣货'))
const isPickingInProgress = computed(() => activePickingTask.value?.status === '拣货中')

const scanResult = computed(() => {
  const line = activePickingLine.value
  if (!line) return null
  return {
    material: line.name,
    batch: line.batch,
    location: line.location,
    required: line.required,
    picked: line.picked
  }
})

const maxPick = computed(() => Math.max(0, activePickingProgress.value.remaining))

const canComplete = computed(() => {
  const task = activePickingTask.value
  if (!task || task.status !== '拣货中') return false
  return task.lines.every((l) => l.scanStatus === '已扫码')
})

const statusBanner = computed(() => {
  if (warehouseTip.value) return warehouseTip.value
  if (isPickingInProgress.value && activePickingTask.value) {
    return `拣货中 · ${activePickingTask.value.id} · 进度 ${activePickingProgress.value.done}/${activePickingProgress.value.total}`
  }
  return ''
})

watch(workerOptions, (list) => {
  if (!selectedWorkerId.value && list.length) {
    selectedWorkerId.value = list[0].id
  }
})

watch(
  () => [activePickingTask.value?.id, activePickingTask.value?.status],
  async ([taskId, status]) => {
    selectedPrepLocationId.value = ''
    if (taskId && status === '拣货中') {
      const rec = await fetchPrepRecommend(taskId)
      if (rec?.locationId) selectedPrepLocationId.value = rec.locationId
    }
  },
  { immediate: true }
)

onMounted(async () => {
  if (!allowed.value) return
  await Promise.all([loadWarehouseData(), loadWorkerOptions()])
  const taskId = route.params.id
  if (taskId) {
    const task = pickingTasks.value.find((t) => t.id === taskId)
    if (task?.status === '拣货中') {
      setActivePickingTask(taskId)
    } else if (task?.status === '待拣货') {
      setActivePickingTask(taskId)
      await startWarehousePicking(taskId)
    } else {
      warehouseTip.value = '该任务不在待拣货状态，请从拣货任务列表进入'
    }
  } else if (pendingPickTasks.value.length) {
    setActivePickingTask(pendingPickTasks.value[0].id)
  }
  pickQty.value = Math.min(1, maxPick.value || 1)
})

watch(activePickingLine, () => {
  scannedOnce.value = false
  pickQty.value = Math.min(Math.max(1, pickQty.value), maxPick.value || 1)
})

watch(
  () => route.params.id,
  async (id) => {
    if (!id) return
    const task = pickingTasks.value.find((t) => t.id === id)
    if (task?.status === '拣货中') {
      setActivePickingTask(id)
    } else if (task?.status === '待拣货') {
      setActivePickingTask(id)
      await startWarehousePicking(id)
    }
  }
)

async function handleSelectTask(taskId) {
  if (!taskId) return
  setActivePickingTask(taskId)
  barcodeInput.value = ''
  scannedOnce.value = false
  lastWarehouseScanFeedback.value = null
}

function adjustQty(delta) {
  pickQty.value = Math.min(Math.max(1, pickQty.value + delta), maxPick.value || 1)
}

function clampPickQty() {
  const max = maxPick.value || 1
  const n = Number(pickQty.value)
  if (!Number.isFinite(n) || n < 1) {
    pickQty.value = 1
  } else if (n > max) {
    pickQty.value = max
  } else {
    pickQty.value = Math.floor(n)
  }
}

async function handleStart(taskId) {
  const ok = await startWarehousePicking(taskId)
  if (ok) setActivePickingTask(taskId)
}

async function handleScan() {
  if (scanning.value || !activePickingLine.value) return
  scanning.value = true
  try {
    barcodeInput.value =
      barcodeInput.value.trim() ||
      `${activePickingLine.value.batch}-M-${activePickingLine.value.name?.slice(0, 4) ?? 'MAT'}-0001`
    scannedOnce.value = true
  } finally {
    scanning.value = false
  }
}

async function handleConfirm() {
  if (scanning.value || !activePickingLine.value || !maxPick.value) return
  scanning.value = true
  try {
    if (!scannedOnce.value) await handleScan()
    await submitWarehouseScan({ barcodeValue: barcodeInput.value, qty: pickQty.value })
    if (lastWarehouseScanFeedback.value?.success) {
      barcodeInput.value = ''
      scannedOnce.value = false
      pickQty.value = 1
    }
  } finally {
    scanning.value = false
  }
}

async function handleComplete() {
  if (!activePickingTask.value || !selectedWorkerId.value || !selectedPrepLocationId.value) return
  const ok = await completeWarehousePicking(
    activePickingTask.value.id,
    selectedWorkerId.value,
    selectedPrepLocationId.value
  )
  if (ok) router.push('/warehouse-picking')
}

function goList() {
  router.push('/warehouse-picking')
}
</script>

<template>
  <section v-if="allowed" class="outbound-board quality-issue-board qa-redesign worker-redesign">
    <header class="qa-issue-header">
      <div>
        <h1>PDA 扫码拣货</h1>
      </div>
      <button type="button" class="worker-link-btn" @click="goList">← 返回拣货任务</button>
    </header>

    <p v-if="warehouseDataLoading" class="data-hint">正在加载拣货任务...</p>

    <!-- 任务状态条：合并原先分散的提示与任务信息 -->
    <div v-if="statusBanner" class="wh-pick-status-banner">{{ statusBanner }}</div>

    <!-- 待拣货：任务选择 -->
    <div v-if="!isPickingInProgress" class="wh-pick-task-panel">
      <div class="wh-pick-task-panel__head">
        <span class="wh-pick-task-panel__label">选择待拣货任务</span>
      </div>
      <div class="wh-pick-task-panel__body">
        <select
          class="wh-worker-select"
          :value="activePickingTask?.status === '待拣货' ? activePickingTask?.id ?? '' : ''"
          @change="handleSelectTask($event.target.value)"
        >
          <option value="" disabled>{{ pendingPickTasks.length ? '请选择任务' : '暂无待拣货任务' }}</option>
          <option v-for="task in pendingPickTasks" :key="task.id" :value="task.id">
            {{ task.id }} · {{ task.outbound }} · {{ task.workOrder }}
          </option>
        </select>
        <button
          v-if="activePickingTask?.status === '待拣货'"
          type="button"
          class="wb-btn-primary"
          @click="handleStart(activePickingTask.id)"
        >
          开始拣货
        </button>
      </div>
      <p v-if="activePickingTask?.status === '待拣货'" class="wh-pick-task-panel__hint">
        已选 {{ activePickingTask.id }}，共 {{ activePickingTask.itemCount }} 项物料
      </p>
    </div>

    <!-- 拣货中：当前任务摘要 -->
    <div v-else-if="activePickingTask" class="wh-pick-task-panel active">
      <div class="wh-pick-task-panel__head">
        <span class="wh-pick-task-panel__badge">拣货中</span>
        <strong>{{ activePickingTask.id }}</strong>
        <span class="wh-pick-task-panel__meta">{{ activePickingTask.outbound }} · {{ activePickingTask.workOrder }}</span>
      </div>
      <div class="wh-pick-task-panel__progress">
        扫码进度 {{ activePickingProgress.done }}/{{ activePickingProgress.total }}
      </div>
    </div>

    <div v-if="activePickingTask?.status === '待拣货'" class="wh-empty subtle">
      点击「开始拣货」后进入 PDA 扫码界面
    </div>

    <div v-else-if="isPickingInProgress && activePickingTask" class="worker-scan-layout">
      <section class="qa-card worker-scan-viewport">
        <div class="worker-scan-frame">
          <div class="worker-scan-laser"></div>
          <p class="worker-scan-hint-text">按推荐库位找料，将条码置于框内识别</p>
          <p v-if="activePickingLine" class="worker-scan-context-line">
            目标库位 <strong>{{ activePickingLine.location }}</strong>
            · 批次 <strong>{{ activePickingLine.batch }}</strong>
          </p>
          <div class="worker-scan-input-row compact">
            <input
              v-model="barcodeInput"
              type="text"
              class="wb-input"
              placeholder="或手动输入条码后回车"
              @keydown.enter.prevent="handleScan"
            />
            <button type="button" class="wb-btn-primary" :disabled="scanning" @click="handleScan">
              {{ scanning ? '识别中...' : '模拟扫码' }}
            </button>
          </div>
        </div>
      </section>

      <section class="qa-card worker-scan-result-card">
        <h2>拣货明细</h2>
        <div class="plan-table wh-table" style="margin-bottom: 12px;">
          <div
            v-for="line in activePickingTask.lines"
            :key="line.id"
            :class="['plan-row', 'wh-row-pick-lines', { active: line.id === activePickingLine?.id }]"
            style="cursor: pointer; grid-template-columns: 2fr 1fr 1fr 1fr 1fr;"
            @click="setActivePickingLine(line.id)"
          >
            <span>{{ line.name }}</span>
            <span>{{ line.location }}</span>
            <span>{{ line.batch }}</span>
            <span>{{ line.picked }}/{{ line.required }}</span>
            <span><i :class="['plan-status', statusClass(line.scanStatus)]">{{ line.scanStatus }}</i></span>
          </div>
        </div>

        <template v-if="scanResult">
          <dl class="qa-info-list">
            <div><dt>物料</dt><dd>{{ scanResult.material }}</dd></div>
            <div><dt>库位</dt><dd>{{ scanResult.location }}</dd></div>
            <div><dt>批次</dt><dd>{{ scanResult.batch }}</dd></div>
            <div><dt>应拣/已拣</dt><dd>{{ scanResult.required }} / {{ scanResult.picked }}</dd></div>
          </dl>
          <div class="worker-qty-field">
            <span>本次确认数量</span>
            <div class="worker-stepper">
              <button type="button" @click="adjustQty(-1)">−</button>
              <input
                v-model.number="pickQty"
                type="number"
                class="worker-stepper-input"
                min="1"
                :max="maxPick || 1"
                @change="clampPickQty"
                @blur="clampPickQty"
              />
              <button type="button" @click="adjustQty(1)">+</button>
            </div>
          </div>
        </template>

        <div v-if="lastWarehouseScanFeedback" :class="['worker-scan-feedback', lastWarehouseScanFeedback.success ? 'ok' : 'danger']">
          <strong>{{ lastWarehouseScanFeedback.success ? '核对通过' : '扫码异常' }}</strong>
          <ul>
            <li v-for="(msg, index) in lastWarehouseScanFeedback.messages" :key="index">{{ msg }}</li>
          </ul>
        </div>
      </section>
    </div>

    <div v-else-if="!pendingPickTasks.length && !isPickingInProgress" class="wh-empty">
      暂无待拣货任务，请先在出库单页生成出库单
    </div>

    <div v-if="isPickingInProgress && activePickingTask" class="wh-pick-footer">
      <WhPrepLocationSelect
        v-model="selectedPrepLocationId"
        :recommend="prepRecommend"
        :loading="prepRecommendLoading"
        compact
      />
      <WhWorkerNotifySelect
        v-model="selectedWorkerId"
        :workers="workerOptions"
        :loading="workersLoading"
        compact
      />
      <div class="wh-pick-footer__actions">
        <button type="button" class="wb-btn-secondary" @click="barcodeInput = ''; scannedOnce = false">
          重新扫码
        </button>
        <button type="button" class="wb-btn-primary" :disabled="scanning || !maxPick" @click="handleConfirm">
          {{ scanning ? '提交中...' : '确认拣货' }}
        </button>
        <button
          type="button"
          class="wb-btn-primary"
          :disabled="!canComplete || !selectedWorkerId || !selectedPrepLocationId || prepRecommendLoading"
          @click="handleComplete"
        >
          完成拣货 · 入备料区
        </button>
      </div>
    </div>
  </section>
  <section v-else class="wh-board">
    <p class="data-hint">当前角色无权访问 PDA 扫码拣货</p>
  </section>
</template>

<style scoped>
.plan-row.active {
  outline: 2px solid var(--accent, #6d8e7d);
  border-radius: 6px;
}
</style>
