<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWarehouseRoleAccess } from '../composables/useRoleAccess'
import { useWarehouse } from '../composables/useWarehouse'

const route = useRoute()
const router = useRouter()
const { allowed } = useWarehouseRoleAccess()

const { getException, resolveException, statusClass, warehouseTip, loadWarehouseData } = useWarehouse()

const exception = computed(() => getException(route.params.id))

onMounted(() => {
  if (allowed.value) loadWarehouseData()
})

function goBack() {
  router.push('/warehouse-exceptions')
}

function goOutbound() {
  const doc = exception.value?.relatedDoc
  if (!doc) return
  if (doc.startsWith('OUT')) {
    router.push(`/outbound-orders/${doc}`)
  } else {
    router.push('/outbound-orders')
  }
}

function goPicking() {
  router.push('/warehouse-picking')
}

function markResolved() {
  resolveException(exception.value.id)
}
</script>

<template>
  <section v-if="allowed && exception" class="wh-board wh-detail">
    <header class="plan-header">
      <div>
        <button type="button" class="worker-link-btn" @click="goBack">← 返回异常记录</button>
        <h1>异常详情 · {{ exception.id }}</h1>
      </div>
      <i :class="['plan-status', statusClass(exception.status)]">{{ exception.status }}</i>
    </header>

    <p v-if="warehouseTip" class="queue-tip">{{ warehouseTip }}</p>

    <section class="wh-detail-card">
      <h2>异常信息</h2>
      <div class="worker-readonly-list">
        <p><span>异常编号</span><strong>{{ exception.id }}</strong></p>
        <p><span>关联单据</span><strong>{{ exception.relatedDoc }}</strong></p>
        <p><span>异常环节</span><strong>{{ exception.stage }}</strong></p>
        <p><span>异常物料</span><strong>{{ exception.material || '—' }}</strong></p>
        <p><span>异常说明</span><strong>{{ exception.description }}</strong></p>
        <p><span>处理建议</span><strong>{{ exception.suggestion }}</strong></p>
        <p><span>处理结果</span><strong>{{ exception.result || '尚未处理' }}</strong></p>
        <p><span>登记时间</span><strong>{{ exception.createdAt }}</strong></p>
      </div>
    </section>

    <div class="wh-action-bar">
      <button
        v-if="exception.status === '待处理'"
        type="button"
        class="worker-btn primary"
        @click="markResolved"
      >标记已处理</button>
      <button type="button" class="worker-btn secondary" @click="goOutbound">返回出库单</button>
      <button type="button" class="worker-btn secondary" @click="goPicking">返回拣货任务</button>
    </div>
  </section>
  <section v-else class="wh-board">
    <p class="data-hint">未找到异常记录，或当前角色无权访问</p>
    <button type="button" class="worker-link-btn" @click="goBack">← 返回异常记录</button>
  </section>
</template>
