<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useInventory } from '../composables/useInventory'
import { getAuthToken } from '../api'

const router = useRouter()
const route = useRoute()
const selectedBatchKey = ref('')

const { login: doLogin, getTrace: traceBatch, getTransactions: fetchTransactions, fetchInventoryListWithDetail, loading } = useInventory()

const batchOptions = ref([])
const traceSteps = ref([])

async function fetchFromApi() {
  if (!getAuthToken()) {
    try { await doLogin('admin', 'admin123') } catch { return }
  }
  const list = await fetchInventoryListWithDetail()
  if (list && list.length) {
    const seen = {}
    batchOptions.value = list.filter(item => {
      const key = item.itemId + '-' + item.batchId
      if (seen[key]) return false
      seen[key] = true
      return true
    }).map(item => ({
      key: item.itemId + '-' + item.batchId,
      itemId: item.itemId,
      batchId: item.batchId,
      itemCode: item.itemCode,
      itemName: item.itemName,
      batchNo: item.batchNo,
    }))
    if (batchOptions.value.length) {
      selectedBatchKey.value = batchOptions.value[0].key
      loadTrace(batchOptions.value[0].itemId, batchOptions.value[0].batchId)
    }
  }
}

async function loadTrace(itemId, batchId) {
  try {
    const txs = await fetchTransactions(itemId, batchId)
    if (txs && txs.length) {
      traceSteps.value = txs.map((tx, i) => ({
        step: i + 1,
        name: TX_TYPE_LABEL[tx.businessType] || tx.businessType || '操作',
        time: tx.operatedAt ? tx.operatedAt.replace('T', ' ').slice(0, 16) : '—',
        handler: tx.operatedBy ? '用户' + tx.operatedBy : '—',
        desc: buildDesc(tx),
        ref: tx.transactionNo || '—',
        status: 'done',
      }))
    } else {
      traceSteps.value = []
    }
  } catch {
    traceSteps.value = []
  }
}

const TX_TYPE_LABEL = {
  INBOUND: '入库',
  OUTBOUND: '出库',
  FREEZE: '冻结',
  UNFREEZE: '解冻',
  MOVE: '移库',
  ADJUST: '调整',
}

function buildDesc(tx) {
  const parts = []
  if (tx.sourceDocType) {
    parts.push('来源: ' + tx.sourceDocType)
  }
  if (tx.changeQty != null) {
    const qty = Number(tx.changeQty)
    parts.push('数量变动: ' + (qty >= 0 ? '+' : '') + qty)
  }
  if (tx.beforeQty != null && tx.afterQty != null) {
    parts.push('结存: ' + Number(tx.beforeQty) + ' → ' + Number(tx.afterQty))
  }
  return parts.join(' | ') || '操作记录'
}

function onBatchChange() {
  const opt = batchOptions.value.find(b => b.key === selectedBatchKey.value)
  if (opt) loadTrace(opt.itemId, opt.batchId)
}

const currentTrace = computed(() => {
  const opt = batchOptions.value.find(b => b.key === selectedBatchKey.value)
  if (!opt) return null
  const total = traceSteps.value.length
  return {
    item: opt.itemName,
    batchNo: opt.batchNo,
    totalQty: total || '—',
    steps: traceSteps.value,
  }
})

onMounted(fetchFromApi)

function goBack() {
  const from = route.query.from
  if (from === 'frozen') return router.push('/inventory-frozen')
  if (from === 'variance') return router.push('/inventory-variance')
  if (from === 'adjustment') return router.push('/inventory-adjustment')
  router.push('/inventory-control')
}
</script>

<template>
  <div class="trace-page">
    <header class="trace-header">
      <button class="trace-back" type="button" @click="goBack">← 返回控制台</button>
      <div>
        <h1>批次全链路追溯</h1>
      </div>
      <div class="trace-picker">
        <label>选择批次</label>
        <select v-model="selectedBatchKey" @change="onBatchChange">
          <option v-for="opt in batchOptions" :key="opt.key" :value="opt.key">{{ opt.batchNo }} — {{ opt.itemName }} ({{ opt.itemCode }})</option>
        </select>
      </div>
    </header>

    <div v-if="currentTrace" class="trace-main">
      <div class="trace-summary">
        <div class="ts-item">
          <span class="ts-label">物料</span>
          <strong class="ts-value">{{ currentTrace.item }}</strong>
        </div>
        <div class="ts-item">
          <span class="ts-label">批次号</span>
          <span class="ts-value">{{ currentTrace.batchNo }}</span>
        </div>
        <div class="ts-item">
          <span class="ts-label">流水记录</span>
          <strong class="ts-value">{{ currentTrace.totalQty }} 条</strong>
        </div>
        <div class="ts-item">
          <span class="ts-label">当前状态</span>
          <i :class="['ts-status', currentTrace.steps[currentTrace.steps.length - 1].status === 'warn' ? 'warn' : currentTrace.steps[currentTrace.steps.length - 1].status === 'pending' ? 'pending' : 'done']">
            {{ currentTrace.steps[currentTrace.steps.length - 1].name }}
          </i>
        </div>
      </div>

      <div class="trace-chain">
        <div
          v-for="(step, index) in currentTrace.steps"
          :key="step.step"
          :class="['trace-step', step.status]"
        >
          <div class="trace-dot-area">
            <span class="trace-dot">
              <i v-if="step.status === 'done'">✓</i>
              <i v-else-if="step.status === 'warn'">!</i>
            </span>
            <div v-if="index < currentTrace.steps.length - 1" class="trace-line"></div>
          </div>
          <div class="trace-body">
            <div class="trace-head">
              <strong>{{ step.name }}</strong>
              <span class="trace-time">{{ step.time }}</span>
            </div>
            <p class="trace-desc">{{ step.desc }}</p>
            <div class="trace-ref">
              <span class="ref-tag">证据</span>
              <span class="ref-code">{{ step.ref }}</span>
              <span class="ref-handler">{{ step.handler }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.trace-page {
  width: min(800px, 100%);
  margin: 10px auto 0;
}

.trace-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.trace-back {
  border: 1px solid #dfe6e1;
  border-radius: 999px;
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.7);
  color: #6b7a72;
  font-size: 13px;
  cursor: pointer;
}

.trace-back:hover {
  background: #eef4f0;
}

.trace-header h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(24px, 3vw, 32px);
  font-weight: 600;
  color: #2a3a33;
}

.trace-header p {
  margin: 6px 0 0;
  color: #7d8983;
  font-size: 14px;
}

.trace-picker {
  display: flex;
  align-items: center;
  gap: 8px;
}

.trace-picker label {
  color: #8b958f;
  font-size: 13px;
}

.trace-picker select {
  border: 1px solid #dfe6e1;
  border-radius: 8px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.8);
  color: #46544f;
  font-size: 13px;
  outline: 0;
  min-width: 200px;
  cursor: pointer;
}

.trace-picker select:focus {
  border-color: #6b8c7b;
}

.trace-main {
  margin-top: 22px;
}

.trace-summary {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 20px;
  border: 1px solid #e0e6e2;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.8);
}

.ts-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ts-label {
  color: #8b958f;
  font-size: 11px;
}

.ts-value {
  color: #46544f;
  font-size: 14px;
}

.ts-value strong {
  color: #2f4139;
}

.ts-status {
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 12px;
  font-style: normal;
}

.ts-status.done {
  background: #e2f0e7;
  color: #4b7a5f;
}

.ts-status.warn {
  background: #f5edd7;
  color: #a97e2c;
}

.ts-status.pending {
  background: #eef4f0;
  color: #6b7a72;
}

.trace-chain {
  margin-top: 24px;
  padding-left: 10px;
}

.trace-step {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 16px;
  padding-bottom: 24px;
  position: relative;
}

.trace-step:last-child {
  padding-bottom: 0;
}

.trace-dot-area {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.trace-dot {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  border: 2px solid #cfdad3;
  background: #f8faf8;
  display: grid;
  place-items: center;
  font-size: 12px;
  color: #8b958f;
  z-index: 1;
  flex-shrink: 0;
}

.trace-step.done .trace-dot {
  border-color: #688a79;
  background: #688a79;
  color: #fff;
}

.trace-step.warn .trace-dot {
  border-color: #d79a3a;
  background: #f5edd7;
  color: #a97e2c;
}

.trace-step.pending .trace-dot {
  border-style: dashed;
  background: #f8faf8;
}

.trace-line {
  flex: 1;
  width: 1px;
  background: #dce5df;
  min-height: 20px;
}

.trace-body {
  min-width: 0;
  padding-top: 2px;
}

.trace-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.trace-head strong {
  font-size: 15px;
  color: #31433c;
}

.trace-time {
  color: #4d5e56;
  font-size: 12px;
  white-space: nowrap;
}

.trace-desc {
  margin: 0 0 8px;
  color: #52615b;
  font-size: 13px;
  line-height: 1.6;
}

.trace-ref {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ref-tag {
  border-radius: 4px;
  padding: 1px 6px;
  background: #eef4f0;
  color: #5f7268;
  font-size: 11px;
}

.ref-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: #6b8c7b;
  font-size: 12px;
}

.ref-handler {
  color: #8b958f;
  font-size: 12px;
}
</style>
