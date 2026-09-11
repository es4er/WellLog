<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useRoleAccess, useViewRole } from '../composables/useRoleAccess'
import { useWorker } from '../composables/useWorker'
import { menuToPath, demoPreserveRoute } from '../router/menuRoutes'
import RoleAccessDenied from '../components/RoleAccessDenied.vue'

const PAGE_SIZE = 6

const router = useRouter()
const { allowed } = useRoleAccess('worker')
const { isAdminDemo, demoRoleId } = useViewRole()

const {
  workerDataLoading,
  loadWorkerData,
  pickingTasks,
  scanRecords,
  isTaskHandedOver,
  taskProgress
} = useWorker()

const page = ref(1)

const completed = computed(() =>
  pickingTasks.value
    .filter((t) => isTaskHandedOver(t))
    .sort((a, b) => (b.handoverTime || '').localeCompare(a.handoverTime || ''))
)

const totalPages = computed(() => Math.max(1, Math.ceil(completed.value.length / PAGE_SIZE)))

const pagedCompleted = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return completed.value.slice(start, start + PAGE_SIZE)
})

const pageInfo = computed(() => {
  const total = completed.value.length
  if (!total) return '共 0 条'
  const start = (page.value - 1) * PAGE_SIZE + 1
  const end = Math.min(page.value * PAGE_SIZE, total)
  return `第 ${start}-${end} 条 / 共 ${total} 条`
})

watch(completed, () => {
  if (page.value > totalPages.value) page.value = totalPages.value
})

onMounted(() => {
  if (!allowed.value) return
  loadWorkerData()
})

function evidenceCount(workOrder) {
  return scanRecords.value.filter((r) => r.workOrder === workOrder && r.success).length
}

function goDetail(taskId) {
  router.push(demoPreserveRoute(menuToPath('我的领料'), demoRoleId.value, isAdminDemo.value, { taskId }))
}

function prevPage() {
  if (page.value > 1) page.value -= 1
}

function nextPage() {
  if (page.value < totalPages.value) page.value += 1
}
</script>

<template>
  <section v-if="allowed" class="outbound-board quality-issue-board qa-redesign worker-redesign">
    <p v-if="workerDataLoading" class="data-hint">正在加载完成记录...</p>

    <header class="qa-issue-header">
      <div>
        <h1>完成记录</h1>
      </div>
      <div class="qa-issue-stats">
        <div>
          <span class="qa-stat-icon">✓</span>
          <small>已完成工单</small>
          <strong>{{ completed.length }} <em>单</em></strong>
        </div>
      </div>
    </header>

    <section class="qa-records-panel worker-completed-panel">
      <div class="worker-panel-title">
        <h2>交接记录</h2>
        <small>{{ pageInfo }}</small>
      </div>

      <div class="qa-issue-table worker-completed-table">
        <div class="qa-issue-tr qa-issue-th worker-completed-row">
          <span>工单号</span>
          <span>产品</span>
          <span>计划数量</span>
          <span>交接时间</span>
          <span>物料进度</span>
          <span>扫码证据</span>
          <span>状态</span>
          <span>操作</span>
        </div>
        <button
          v-for="task in pagedCompleted"
          :key="task.id"
          type="button"
          class="qa-issue-tr worker-completed-row"
          @click="goDetail(task.id)"
        >
          <span class="qa-code">{{ task.workOrder }}</span>
          <span>{{ task.product }}</span>
          <span>{{ task.planQty }} {{ task.unit }}</span>
          <span>{{ task.planDate }} {{ task.handoverTime }}</span>
          <span>{{ taskProgress(task).scannedItems }}/{{ taskProgress(task).totalItems }}</span>
          <span>{{ evidenceCount(task.workOrder) }} 条</span>
          <span><em class="qa-dot-status ok">已交接</em></span>
          <span class="qa-view">查看</span>
        </button>
        <p v-if="!pagedCompleted.length" class="qa-empty">暂无已完成记录</p>
      </div>

      <div class="worker-pager">
        <button type="button" class="wb-btn-secondary wb-btn-sm" :disabled="page <= 1" @click="prevPage">上一页</button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button type="button" class="wb-btn-secondary wb-btn-sm" :disabled="page >= totalPages" @click="nextPage">下一页</button>
      </div>
    </section>
  </section>
  <RoleAccessDenied v-else required-role-id="worker" module-name="完成记录" />
</template>
