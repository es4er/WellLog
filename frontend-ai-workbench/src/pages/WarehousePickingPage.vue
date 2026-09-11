<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../composables/useSession'
import { useWarehouse } from '../composables/useWarehouse'
import { PICKING_TABS } from '../data/warehouseConstants'

const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'warehouse')

const {
  pickingByTab,
  pickingPendingCount,
  pickingActiveCount,
  pickingReviewCount,
  statusClass,
  startPicking,
  warehouseTip,
  warehouseDataLoading,
  warehouseDataError,
  loadWarehouseData
} = useWarehouse()

const activeTab = ref('pending')

onMounted(() => {
  if (allowed.value) loadWarehouseData()
})

const tabs = computed(() =>
  PICKING_TABS.map((tab) => ({
    ...tab,
    count:
      tab.id === 'pending'
        ? pickingPendingCount.value
        : tab.id === 'picking'
          ? pickingActiveCount.value
          : pickingReviewCount.value
  }))
)

const list = computed(() => pickingByTab(activeTab.value))

function openDetail(taskId) {
  router.push(`/warehouse-picking/${taskId}`)
}

function handleStart(taskId) {
  startPicking(taskId)
  openDetail(taskId)
}

function handleContinue(taskId) {
  openDetail(taskId)
}

function handleReview(taskId) {
  openDetail(taskId)
}
</script>

<template>
  <section v-if="allowed" class="wh-board">
    <header class="plan-header">
      <div>
        <h1>拣货任务</h1>
      </div>
    </header>

    <p v-if="warehouseDataLoading" class="data-hint">正在加载拣货任务...</p>
    <p v-if="warehouseDataError" class="data-hint danger">{{ warehouseDataError }}</p>
    <p v-if="warehouseTip" class="queue-tip">{{ warehouseTip }}</p>

    <div class="wh-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        type="button"
        :class="['wh-tab', { active: activeTab === tab.id }]"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
        <em>{{ tab.count }}</em>
      </button>
    </div>

    <!-- 待拣货 -->
    <div v-if="activeTab === 'pending'" class="plan-table wh-table">
      <div class="plan-row wh-row-pick-pending plan-row-head">
        <span>拣货任务号</span>
        <span>出库单号</span>
        <span>工单号</span>
        <span>物料项</span>
        <span>推荐库位</span>
        <span>状态</span>
        <span>操作</span>
      </div>
      <div v-for="row in list" :key="row.id" class="plan-row wh-row-pick-pending">
        <span class="plan-id"><strong>{{ row.id }}</strong></span>
        <span>{{ row.outbound }}</span>
        <span>{{ row.workOrder }}</span>
        <span>{{ row.itemCount }} 项</span>
        <span>{{ row.zone }}</span>
        <span><i :class="['plan-status', statusClass(row.status)]">{{ row.status }}</i></span>
        <span class="plan-ops">
          <button type="button" class="primary" @click="handleStart(row.id)">开始拣货</button>
        </span>
      </div>
      <div v-if="!list.length" class="wh-empty">暂无待拣货任务</div>
    </div>

    <!-- 拣货中 -->
    <div v-else-if="activeTab === 'picking'" class="plan-table wh-table">
      <div class="plan-row wh-row-pick-active plan-row-head">
        <span>拣货任务号</span>
        <span>出库单号</span>
        <span>物料项</span>
        <span>当前进度</span>
        <span>状态</span>
        <span>操作</span>
      </div>
      <div v-for="row in list" :key="row.id" class="plan-row wh-row-pick-active">
        <span class="plan-id"><strong>{{ row.id }}</strong></span>
        <span>{{ row.outbound }}</span>
        <span>{{ row.itemCount }} 项</span>
        <span>{{ row.progress.done }}/{{ row.progress.total }}</span>
        <span><i :class="['plan-status', statusClass(row.status)]">{{ row.status }}</i></span>
        <span class="plan-ops">
          <button type="button" class="primary" @click="handleContinue(row.id)">继续拣货</button>
        </span>
      </div>
      <div v-if="!list.length" class="wh-empty">暂无进行中的拣货任务</div>
    </div>

    <!-- 待复核 -->
    <div v-else class="plan-table wh-table">
      <div class="plan-row wh-row-pick-review plan-row-head">
        <span>拣货任务号</span>
        <span>出库单号</span>
        <span>工单号</span>
        <span>物料项</span>
        <span>状态</span>
        <span>操作</span>
      </div>
      <div v-for="row in list" :key="row.id" class="plan-row wh-row-pick-review">
        <span class="plan-id"><strong>{{ row.id }}</strong></span>
        <span>{{ row.outbound }}</span>
        <span>{{ row.workOrder }}</span>
        <span>{{ row.itemCount }} 项</span>
        <span><i :class="['plan-status', statusClass(row.status)]">{{ row.status }}</i></span>
        <span class="plan-ops">
          <button type="button" class="primary" @click="handleReview(row.id)">复核</button>
        </span>
      </div>
      <div v-if="!list.length" class="wh-empty">暂无待复核任务</div>
    </div>
  </section>
  <section v-else class="wh-board">
    <p class="data-hint">当前角色无权访问拣货任务模块</p>
  </section>
</template>
