<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSession } from '../composables/useSession'
import { useWarehouse } from '../composables/useWarehouse'

const route = useRoute()
const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'warehouse')

const { getOutboundOrder, statusClass, loadWarehouseData, warehouseDataLoading } = useWarehouse()

const order = computed(() => getOutboundOrder(route.params.id))

onMounted(() => {
  if (allowed.value) loadWarehouseData()
})

function goBack() {
  router.push('/outbound-orders')
}

function goPicking() {
  if (!order.value?.pickingTaskId) return
  router.push(`/warehouse-picking/${order.value.pickingTaskId}`)
}
</script>

<template>
  <section v-if="allowed && order" class="wh-board wh-detail">
    <header class="plan-header">
      <div>
        <button type="button" class="worker-link-btn" @click="goBack">← 返回出库单</button>
        <h1>出库单详情 · {{ order.id }}</h1>
      </div>
      <div class="plan-ops">
        <i :class="['plan-status', statusClass(order.status)]">{{ order.status }}</i>
        <button v-if="order.pickingTaskId" type="button" class="primary" @click="goPicking">查看拣货任务</button>
      </div>
    </header>

    <section class="wh-detail-card">
      <h2>基础信息</h2>
      <div class="worker-readonly-list">
        <p><span>出库单号</span><strong>{{ order.id }}</strong></p>
        <p><span>来源领料单</span><strong>{{ order.requisition }}</strong></p>
        <p><span>工单号</span><strong>{{ order.workOrder }}</strong></p>
        <p><span>仓库</span><strong>{{ order.warehouse }}</strong></p>
        <p><span>状态</span><strong>{{ order.status }}</strong></p>
        <p><span>创建时间</span><strong>{{ order.createdAt }}</strong></p>
      </div>
    </section>

    <section class="wh-detail-card">
      <h2>物料明细</h2>
      <div class="plan-table wh-table">
        <div class="plan-row wh-row-lines plan-row-head">
          <span>物料名称</span>
          <span>应发数量</span>
          <span>批次</span>
          <span>库位</span>
          <span>实发数量</span>
          <span>状态</span>
        </div>
        <div v-for="(line, idx) in order.lines" :key="idx" class="plan-row wh-row-lines">
          <span>{{ line.name }}</span>
          <span>{{ line.required }}</span>
          <span>{{ line.batch }}</span>
          <span>{{ line.location }}</span>
          <span>{{ line.actual }}</span>
          <span><i :class="['plan-status', statusClass(line.status)]">{{ line.status }}</i></span>
        </div>
      </div>
    </section>

    <section class="wh-detail-card">
      <h2>生成记录</h2>
      <ol class="wh-gen-log">
        <li v-for="(item, idx) in order.generationLog" :key="idx">{{ item }}</li>
      </ol>
    </section>
  </section>
  <section v-else class="wh-board">
    <p v-if="warehouseDataLoading" class="data-hint">正在加载出库单...</p>
    <p v-else class="data-hint">未找到出库单，或当前角色无权访问</p>
    <button type="button" class="worker-link-btn" @click="goBack">← 返回出库单</button>
  </section>
</template>
