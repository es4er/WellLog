<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSession } from '../composables/useSession'
import { useWarehouse } from '../composables/useWarehouse'

const route = useRoute()
const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'warehouse')

const {
  getPickingTask,
  statusClass,
  confirmScanLine,
  completePicking,
  reportPickingException,
  reviewPicking,
  warehouseTip,
  loadWarehouseData
} = useWarehouse()

const tip = ref('')
const selectedLineId = ref(null)

const task = computed(() => getPickingTask(route.params.id))

onMounted(() => {
  if (allowed.value) loadWarehouseData()
})

function goBack() {
  router.push('/warehouse-picking')
}

function scanLine(lineId) {
  selectedLineId.value = lineId
  if (confirmScanLine(task.value.id, lineId)) {
    tip.value = warehouseTip.value
  }
}

async function handleComplete() {
  if (await completePicking(task.value.id)) {
    tip.value = warehouseTip.value
  } else {
    tip.value = warehouseTip.value
  }
}

function handleException() {
  const lineId = selectedLineId.value || task.value.lines.find((l) => l.scanStatus !== '已扫码')?.id
  const ex = reportPickingException(task.value.id, lineId, '实拣数量少于应发数量')
  if (ex) {
    tip.value = warehouseTip.value
    router.push(`/warehouse-exceptions/${ex.id}`)
  }
}

async function handleReview(pass) {
  const result = await reviewPicking(task.value.id, pass)
  tip.value = warehouseTip.value
  if (result && !result.ok) {
    tip.value = warehouseTip.value || '复核异常已登记'
  }
}
</script>

<template>
  <section v-if="allowed && task" class="wh-board wh-detail">
    <header class="plan-header">
      <div>
        <button type="button" class="worker-link-btn" @click="goBack">← 返回拣货任务</button>
        <h1>拣货详情 · {{ task.id }}</h1>
      </div>
      <i :class="['plan-status', statusClass(task.status)]">{{ task.status }}</i>
    </header>

    <p v-if="tip || warehouseTip" class="queue-tip">{{ tip || warehouseTip }}</p>

    <section class="wh-detail-card">
      <h2>拣货明细</h2>
      <div class="plan-table wh-table">
        <div class="plan-row wh-row-pick-lines plan-row-head">
          <span>物料名称</span>
          <span>应发数量</span>
          <span>批次</span>
          <span>库位</span>
          <span>实拣数量</span>
          <span>扫码状态</span>
          <span>操作</span>
        </div>
        <div v-for="line in task.lines" :key="line.id" class="plan-row wh-row-pick-lines">
          <span>{{ line.name }}</span>
          <span>{{ line.required }}</span>
          <span>{{ line.batch }}</span>
          <span>{{ line.location }}</span>
          <span>{{ line.picked }}</span>
          <span><i :class="['plan-status', statusClass(line.scanStatus)]">{{ line.scanStatus }}</i></span>
          <span class="plan-ops">
            <button
              v-if="task.status === '拣货中' || task.status === '待拣货'"
              type="button"
              class="primary"
              :disabled="line.scanStatus === '已扫码'"
              @click="scanLine(line.id)"
            >扫码确认</button>
          </span>
        </div>
      </div>
    </section>

    <div v-if="task.status === '拣货中' || task.status === '待拣货'" class="wh-action-bar">
      <button type="button" class="worker-btn primary" @click="handleComplete">完成拣货</button>
      <button type="button" class="worker-btn secondary" @click="handleException">上报异常</button>
    </div>

    <div v-else-if="task.status === '待复核'" class="wh-action-bar">
      <button type="button" class="worker-btn primary" @click="handleReview(true)">复核通过</button>
      <button type="button" class="worker-btn secondary" @click="handleReview(false)">复核异常</button>
    </div>

    <div v-else class="wh-action-bar">
      <p class="data-hint">当前状态：{{ task.status }}</p>
    </div>
  </section>
  <section v-else class="wh-board">
    <p class="data-hint">未找到拣货任务，或当前角色无权访问</p>
    <button type="button" class="worker-link-btn" @click="goBack">← 返回拣货任务</button>
  </section>
</template>
