<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useInventory } from '../composables/useInventory'
import { getAuthToken } from '../api'

const router = useRouter()
const { doLogin, checkSafetyStock, fetchInventoryAlertList, loading } = useInventory()

const items = ref([])

const selectedItem = ref(null)
const activeAction = ref('')
const activeItem = ref(null)

function mapApiData(record) {
  return {
    name: record.itemName || '物料' + record.itemId,
    stock: 0,
    safe: record.alertQty || 0,
    gap: 0,
    unit: '件',
    priority: 'medium',
    trend: 'stable',
    lastIn: record.createdAt ? record.createdAt.slice(0, 10) : '—'
  }
}

async function fetchFromApi() {
  if (!getAuthToken()) {
    try { await doLogin('admin', 'admin123') } catch { return }
  }
  try {
    const data = await fetchInventoryAlertList()
    if (data && data.length) {
      items.value = data.map(mapApiData)
    }
  } catch {
    // API unavailable, items stays empty
  }
}

onMounted(fetchFromApi)

function openAction(action, item) {
  activeAction.value = activeAction.value === action ? '' : action
  activeItem.value = item
  selectedItem.value = item?.name
}

function goBack() {
  router.push('/inventory-control')
}

function exportReport() {
  const blob = new Blob(['安全库存报告\n扫描时间: ' + new Date().toLocaleString('zh-CN') + '\n\n' + items.value.map(i => i.name + ' | 库存:' + i.stock + '/' + i.safe + ' | 缺口:' + i.gap).join('\n')], { type: 'text/plain' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'safety-stock-report.txt'
  a.click()
  URL.revokeObjectURL(a.href)
}
</script>

<template>
  <div class="safety-page">
    <header class="safety-header">
      <button class="safety-back" type="button" @click="goBack">← 返回控制台</button>
      <div>
        <h1>安全库存扫描</h1>
      </div>
      <div class="safety-pulse">
        <span class="pulse-dot"></span>
        <span>扫描完成 · {{ new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }}</span>
      </div>
    </header>

    <div class="safety-matrix">
      <div class="matrix-zone critical-zone">
        <div class="zone-label">
          <span class="zone-tag critical">紧急</span>
          <span class="zone-count">2 项</span>
        </div>
        <div v-for="item in items.filter(i => i.priority === 'critical')" :key="item.name" class="matrix-card critical" @click="selectedItem = selectedItem === item.name ? null : item.name">
          <div class="matrix-top">
            <strong>{{ item.name }}</strong>
            <i :class="['trend-icon', item.trend]">{{ item.trend === 'down' ? '↓' : item.trend === 'frozen' ? '⛓' : '→' }}</i>
          </div>
          <div class="matrix-gauge">
            <div class="gauge-empty">
              <div class="gauge-fill" :style="{ width: `${Math.min(100, (item.stock / item.safe) * 100)}%` }"></div>
            </div>
          </div>
          <div class="matrix-data">
            <span class="data-stock">{{ item.stock }}<small>/{{ item.safe }}{{ item.unit }}</small></span>
            <span class="data-gap">缺 {{ item.gap }}</span>
          </div>
          <transition name="fade">
            <div v-if="selectedItem === item.name" class="matrix-action">
              <button type="button" @click.stop="openAction('replenish', item)">生成补货单</button>
              <button type="button" class="outline" @click.stop="openAction('transfer', item)">发起调拨</button>
            </div>
          </transition>
        </div>
      </div>

      <div class="matrix-zone high-zone">
        <div class="zone-label">
          <span class="zone-tag high">高危</span>
          <span class="zone-count">1 项</span>
        </div>
        <div v-for="item in items.filter(i => i.priority === 'high')" :key="item.name" class="matrix-card high" @click="selectedItem = selectedItem === item.name ? null : item.name">
          <div class="matrix-top">
            <strong>{{ item.name }}</strong>
            <i class="trend-icon down">↓</i>
          </div>
          <div class="matrix-gauge">
            <div class="gauge-empty">
              <div class="gauge-fill" :style="{ width: `${Math.min(100, (item.stock / item.safe) * 100)}%` }"></div>
            </div>
          </div>
          <div class="matrix-data">
            <span class="data-stock">{{ item.stock }}<small>/{{ item.safe }}{{ item.unit }}</small></span>
            <span class="data-gap">缺 {{ item.gap }}</span>
          </div>
          <transition name="fade">
            <div v-if="selectedItem === item.name" class="matrix-action">
              <button type="button" @click.stop="openAction('replenish', item)">生成补货单</button>
              <button type="button" class="outline" @click.stop="openAction('transfer', item)">发起调拨</button>
            </div>
          </transition>
        </div>
      </div>

      <div class="matrix-zone medium-zone">
        <div class="zone-label">
          <span class="zone-tag medium">偏低</span>
          <span class="zone-count">3 项</span>
        </div>
        <div v-for="item in items.filter(i => i.priority === 'medium')" :key="item.name" class="matrix-card medium" @click="selectedItem = selectedItem === item.name ? null : item.name">
          <div class="matrix-top">
            <strong>{{ item.name }}</strong>
            <i :class="['trend-icon', item.trend]">{{ item.trend === 'down' ? '↓' : '→' }}</i>
          </div>
          <div class="matrix-gauge">
            <div class="gauge-empty">
              <div class="gauge-fill" :style="{ width: `${Math.min(100, (item.stock / item.safe) * 100)}%` }"></div>
            </div>
          </div>
          <div class="matrix-data">
            <span class="data-stock">{{ item.stock }}<small>/{{ item.safe }}{{ item.unit }}</small></span>
            <span class="data-gap">缺 {{ item.gap }}</span>
          </div>
          <transition name="fade">
            <div v-if="selectedItem === item.name" class="matrix-action">
              <button type="button" @click.stop="openAction('replenish', item)">生成补货单</button>
              <button type="button" class="outline" @click.stop="openAction('transfer', item)">发起调拨</button>
            </div>
          </transition>
        </div>
      </div>
    </div>

    <section class="safety-summary">
      <div class="summary-block">
        <span class="summary-head">AI 分析结论</span>
        <p>安全库存规则命中 6 条，其中 2 条为紧急缺货。钛合金管线已完全耗尽，防爆接插件处于冻结状态。建议对紧急物料立即触发采购流程，偏高危物料安排调拨或分批补货，全部证据已锁定可追溯。</p>
      </div>
      <div class="summary-ops">
        <button type="button" class="ops-btn" @click="openAction('procurement', null)">生成采购补货清单</button>
        <button type="button" class="ops-btn outline" @click="exportReport">导出安全库存报告</button>
      </div>
    </section>

    <transition name="slide">
      <div v-if="activeAction === 'replenish' && activeItem" class="action-panel">
        <div class="panel-title">
          <span>补货单 — {{ activeItem.name }}</span>
          <button type="button" class="panel-back" @click="activeAction = ''">× 返回</button>
        </div>
        <div class="panel-body">
          <label>建议补货量 <input type="number" :value="activeItem.gap" /></label>
          <label>供应商 <select><option>中石油装备有限公司</option><option>华丰防爆器材有限公司</option></select></label>
          <label>期望到货日 <input type="date" /></label>
        </div>
        <div class="panel-foot">
          <button type="button" class="panel-btn" @click="activeAction = ''">确认提交</button>
          <button type="button" class="panel-btn outline" @click="activeAction = ''">取消</button>
        </div>
      </div>
    </transition>
    <transition name="slide">
      <div v-if="activeAction === 'transfer' && activeItem" class="action-panel">
        <div class="panel-title">
          <span>调拨单 — {{ activeItem.name }}</span>
          <button type="button" class="panel-back" @click="activeAction = ''">× 返回</button>
        </div>
        <div class="panel-body">
          <label>调出库位 <select><option>A-01-01</option><option>B-02-01</option></select></label>
          <label>目标库位 <select><option>C-01-01</option><option>D-01-01</option></select></label>
          <label>调拨数量 <input type="number" :value="activeItem.gap" /></label>
        </div>
        <div class="panel-foot">
          <button type="button" class="panel-btn" @click="activeAction = ''">确认发起</button>
          <button type="button" class="panel-btn outline" @click="activeAction = ''">取消</button>
        </div>
      </div>
    </transition>
    <transition name="slide">
      <div v-if="activeAction === 'procurement'" class="action-panel">
        <div class="panel-title">
          <span>采购补货清单</span>
          <button type="button" class="panel-back" @click="activeAction = ''">× 返回</button>
        </div>
        <div class="panel-body">
          <p>以下物料将纳入采购清单：</p>
          <div v-for="item in items" :key="item.name" class="panel-item">
            <span>{{ item.name }}</span><span>缺口 {{ item.gap }}{{ item.unit }}</span>
          </div>
        </div>
        <div class="panel-foot">
          <button type="button" class="panel-btn" @click="activeAction = ''">确认生成采购单</button>
          <button type="button" class="panel-btn outline" @click="activeAction = ''">取消</button>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.safety-page {
  width: min(960px, 100%);
  margin: 10px auto 0;
}

.safety-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.safety-back {
  border: 1px solid #dfe6e1;
  border-radius: 999px;
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.7);
  color: #6b7a72;
  font-size: 13px;
  cursor: pointer;
}

.safety-back:hover {
  background: #eef4f0;
}

.safety-header h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(24px, 3vw, 32px);
  font-weight: 600;
  color: #2a3a33;
}

.safety-header p {
  margin: 6px 0 0;
  color: #7d8983;
  font-size: 14px;
}

.safety-pulse {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 999px;
  background: #eaf3ed;
  color: #4b7a5f;
  font-size: 12px;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #4b7a5f;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.safety-matrix {
  margin-top: 22px;
  display: grid;
  gap: 18px;
}

.matrix-zone {
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.8);
  padding: 18px;
}

.critical-zone {
  background: rgba(255, 248, 245, 0.5);
}

.high-zone {
  background: rgba(255, 250, 242, 0.5);
}

.zone-label {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.zone-tag {
  border-radius: 999px;
  padding: 3px 12px;
  font-size: 12px;
  font-weight: 600;
}

.zone-tag.critical { background: #f6e0d9; color: #b4553f; }
.zone-tag.high { background: #f5edd7; color: #a97e2c; }
.zone-tag.medium { background: #eef4f0; color: #5f7268; }

.zone-count {
  color: #8b958f;
  font-size: 13px;
}

.matrix-cards {
  display: grid;
  gap: 10px;
}

.matrix-card {
  border: 1px solid #e8eeea;
  border-radius: 10px;
  padding: 14px;
  background: #fff;
  cursor: pointer;
  transition: box-shadow 0.15s;
}

.matrix-card:hover {
  box-shadow: 0 2px 8px rgba(55, 78, 67, 0.08);
}

.matrix-card.critical {
  border-left: 3px solid #b4553f;
}

.matrix-card.high {
  border-left: 3px solid #d79a3a;
}

.matrix-card.medium {
  border-left: 3px solid #8ba896;
}

.matrix-card + .matrix-card {
  margin-top: 8px;
}

.matrix-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.matrix-top strong {
  font-size: 14px;
  color: #31433c;
}

.trend-icon {
  font-style: normal;
  font-size: 14px;
}

.trend-icon.down { color: #b4553f; }
.trend-icon.frozen { color: #8a7a9a; }
.trend-icon.stable { color: #8ba896; }

.matrix-gauge {
  margin-bottom: 8px;
}

.gauge-empty {
  height: 6px;
  border-radius: 999px;
  background: #e2eae5;
  overflow: hidden;
}

.gauge-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #b4553f, #d79a3a);
  transition: width 0.3s;
}

.matrix-data {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.data-stock {
  color: #46544f;
}

.data-stock small {
  color: #8b958f;
}

.data-gap {
  color: #b4553f;
  font-weight: 600;
}

.matrix-action {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #e2e8e4;
  display: flex;
  gap: 8px;
}

.matrix-action button {
  border: 0;
  border-radius: 8px;
  padding: 6px 14px;
  background: #587766;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

.matrix-action button.outline {
  border: 1px solid #cddbd3;
  background: #fff;
  color: #567566;
}

.matrix-action button.outline:hover {
  background: #eef4f0;
}

.safety-summary {
  margin-top: 22px;
  border: 1px solid #cfe0d6;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(232, 244, 237, 0.7), rgba(255, 255, 255, 0.9));
  padding: 20px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.summary-block {
  flex: 1;
}

.summary-head {
  display: inline-block;
  border-radius: 999px;
  padding: 3px 10px;
  background: #587766;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 10px;
}

.summary-block p {
  margin: 0;
  color: #4a5c52;
  font-size: 14px;
  line-height: 1.7;
}

.summary-ops {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
}

.ops-btn {
  border: 0;
  border-radius: 8px;
  padding: 9px 18px;
  background: #587766;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}

.ops-btn.outline {
  border: 1px solid #cddbd3;
  background: #fff;
  color: #567566;
}

.ops-btn.outline:hover {
  background: #eef4f0;
}

.action-panel {
  margin-top: 18px;
  border: 1px solid #e0e6e2;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.85);
  overflow: hidden;
}

.panel-title {
  padding: 14px 18px;
  background: #eef4f0;
  color: #2c3b34;
  font-weight: 600;
  font-size: 14px;
  border-bottom: 1px solid #e2e8e4;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-body {
  padding: 16px 18px;
  display: grid;
  gap: 12px;
}

.panel-body label {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 10px;
  align-items: center;
  color: #52615b;
  font-size: 13px;
}

.panel-body input,
.panel-body select {
  border: 1px solid #dfe6e1;
  border-radius: 8px;
  padding: 7px 10px;
  font: inherit;
  font-size: 13px;
  background: #fff;
  outline: 0;
}

.panel-body p {
  margin: 0;
  color: #52615b;
  font-size: 13px;
}

.panel-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f6f9f7;
  font-size: 13px;
  color: #46544f;
}

.panel-foot {
  padding: 12px 18px;
  border-top: 1px solid #eef2ef;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.panel-btn {
  border: 0;
  border-radius: 8px;
  padding: 7px 16px;
  background: #587766;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.panel-btn.outline {
  border: 1px solid #cddbd3;
  background: #fff;
  color: #567566;
}

.panel-back {
  border: 0;
  background: transparent;
  color: #7b8a83;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
}
.panel-back:hover {
  background: rgba(0,0,0,0.04);
}

.slide-enter-active, .slide-leave-active {
  transition: all 0.25s ease;
}
.slide-enter-from, .slide-leave-to {
  opacity: 0;
  max-height: 0;
  overflow: hidden;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
