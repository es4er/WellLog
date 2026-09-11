<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../composables/useSession'
import { useWarehouse } from '../composables/useWarehouse'
import { useAgentTask } from '../composables/useAgentTask'
import { OUTBOUND_TABS } from '../data/warehouseConstants'

const router = useRouter()
const { activeRole, prompt } = useSession()
const allowed = computed(() => activeRole.value.id === 'warehouse')

const {
  pendingRequisitions,
  outboundOrders,
  stats,
  statusClass,
  warehouseTip,
  warehouseDataLoading,
  warehouseDataError,
  loadWarehouseData,
  setPendingOutboundRequisition
} = useWarehouse()

const { startOutboundAgentTask, agentTaskLoading, agentTaskError } = useAgentTask()

const activeTab = ref('pending')

onMounted(() => {
  if (allowed.value) loadWarehouseData()
})

const tabs = computed(() =>
  OUTBOUND_TABS.map((tab) => ({
    ...tab,
    count: tab.id === 'pending' ? pendingRequisitions.value.length : outboundOrders.value.length
  }))
)

async function generateOutbound(row) {
  prompt.value = `根据领料单 ${row.id} 生成出库单，完成库存校验与批次库位推荐。`
  setPendingOutboundRequisition(row.id)
  await startOutboundAgentTask({
    requisitionId: row.requisitionId,
    planId: row.planId,
    planNo: row.plan,
    taskName: `仓管生成出库单-${row.id}`,
    promptText: prompt.value,
    businessNo: row.id,
    pendingRequisitionNo: row.id
  })
}

function viewOutbound(row) {
  router.push(`/outbound-orders/${row.id}`)
}
</script>

<template>
  <section v-if="allowed" class="wh-board">
    <header class="plan-header">
      <div>
        <h1>出库单</h1>
      </div>
      <div class="plan-stats">
        <div v-for="stat in stats.slice(0, 4)" :key="stat[0]" class="plan-stat">
          <strong>{{ stat[1] }}</strong>
          <small>{{ stat[0] }}</small>
        </div>
      </div>
    </header>

    <p v-if="warehouseDataLoading" class="data-hint">正在加载仓管数据...</p>
    <p v-if="warehouseDataError" class="data-hint danger">{{ warehouseDataError }}</p>
    <p v-if="warehouseTip" class="queue-tip">{{ warehouseTip }}</p>
    <p v-if="agentTaskError" class="data-hint danger">{{ agentTaskError }}</p>

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

    <!-- 待生成出库单 -->
    <div v-if="activeTab === 'pending'" class="plan-table wh-table">
      <div class="plan-row wh-row-pending plan-row-head">
        <span>领料单号</span>
        <span>生产计划</span>
        <span>工单号</span>
        <span>物料项</span>
        <span>需求时间</span>
        <span>状态</span>
        <span>操作</span>
      </div>
      <div v-for="row in pendingRequisitions" :key="row.id" class="plan-row wh-row-pending">
        <span class="plan-id"><strong>{{ row.id }}</strong></span>
        <span>{{ row.plan }}</span>
        <span>{{ row.workOrder }}</span>
        <span>{{ row.itemCount }} 项</span>
        <span>{{ row.demandDate }}</span>
        <span><i :class="['plan-status', statusClass(row.status)]">{{ row.status }}</i></span>
        <span class="plan-ops">
          <button
            type="button"
            class="primary"
            :disabled="agentTaskLoading"
            @click="generateOutbound(row)"
          >{{ agentTaskLoading ? 'Agent 执行中...' : '生成出库单' }}</button>
        </span>
      </div>
      <div v-if="!pendingRequisitions.length" class="wh-empty">暂无待生成出库单</div>
    </div>

    <!-- 已生成出库单 -->
    <div v-else class="plan-table wh-table">
      <div class="plan-row wh-row-outbound plan-row-head">
        <span>出库单号</span>
        <span>来源领料单</span>
        <span>工单号</span>
        <span>物料项</span>
        <span>状态</span>
        <span>创建时间</span>
        <span>操作</span>
      </div>
      <div v-for="row in outboundOrders" :key="row.id" class="plan-row wh-row-outbound">
        <span class="plan-id"><strong>{{ row.id }}</strong></span>
        <span>{{ row.requisition }}</span>
        <span>{{ row.workOrder }}</span>
        <span>{{ row.itemCount }} 项</span>
        <span><i :class="['plan-status', statusClass(row.status)]">{{ row.status }}</i></span>
        <span>{{ row.createdAt }}</span>
        <span class="plan-ops">
          <button type="button" @click="viewOutbound(row)">查看</button>
        </span>
      </div>
      <div v-if="!outboundOrders.length" class="wh-empty">暂无已生成出库单</div>
    </div>
  </section>
  <section v-else class="wh-board">
    <p class="data-hint">当前角色无权访问出库单模块</p>
  </section>
</template>
