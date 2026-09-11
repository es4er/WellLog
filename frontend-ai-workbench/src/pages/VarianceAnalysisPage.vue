<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useInventory } from '../composables/useInventory'
import { getAuthToken } from '../api'

const router = useRouter()

const { doLogin, getStocktakeList, getStocktakeDetail, loading } = useInventory()
const activeVariance = ref(null)
const activeAction = ref('')

const variances = ref([])

function mapLineVariance(line) {
  const sysQty = Number(line.bookQty ?? 0)
  const actualQty = Number(line.countedQty ?? line.countQty ?? 0)
  const differenceQty = Number(line.differenceQty ?? (actualQty - sysQty))
  return {
    item: line.itemName || line.item_name || '物料' + (line.itemId || ''),
    batch: line.batchNo || line.batch_no || '',
    location: line.locationCode || line.location_code || '',
    sysQty,
    actualQty,
    variance: differenceQty,
    rate: sysQty ? Math.abs(differenceQty / sysQty * 100).toFixed(0) + '%' : '—',
    reason: line.remark || '',
    status: line.lineStatus === 'CONFIRMED' ? '已调整' : '待核实',
    level: Math.abs(differenceQty) > 5 ? 'high' : 'medium',
  }
}

async function fetchFromApi() {
  try {
    if (!getAuthToken()) {
      await doLogin('admin', 'admin123')
    }
    const orders = await getStocktakeList()
    const rows = []
    for (const order of (orders || []).slice(0, 20)) {
      const stocktakeId = order.stocktakeId || order.stocktake_id
      if (!stocktakeId) continue
      try {
        const detail = await getStocktakeDetail(stocktakeId)
        const lines = detail?.lines || []
        for (const line of lines) {
          if (line.countedQty == null && line.countQty == null) continue
          const sysQty = Number(line.bookQty ?? 0)
          const actualQty = Number(line.countedQty ?? line.countQty ?? 0)
          if (actualQty === sysQty) continue
          rows.push(mapLineVariance(line))
        }
      } catch {
        // skip failed stocktake
      }
    }
    variances.value = rows
  } catch {
    // API unavailable, variances stays empty
  }
}

onMounted(fetchFromApi)

const totalVariance = computed(() => variances.value.reduce((sum, v) => sum + Math.abs(v.variance), 0))
const unresolved = computed(() => variances.value.filter(v => v.status === '待核实').length)

function goBack() {
  router.push('/inventory-control')
}

function exportReport() {
  const blob = new Blob(['盘点差异报告\n生成时间: ' + new Date().toLocaleString('zh-CN') + '\n\n差异总量: ' + totalVariance + '\n待核实: ' + unresolved + '\n\n' + variances.map(v => v.item + ' | ' + v.batch + ' | 系统:' + v.sysQty + ' 实盘:' + v.actualQty + ' 差异:' + v.variance).join('\n')], { type: 'text/plain' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'variance-report.txt'
  a.click()
}
</script>

<template>
  <div class="variance-page">
    <header class="variance-header">
      <button class="variance-back" type="button" @click="goBack">← 返回控制台</button>
      <div>
        <h1>盘点差异分析</h1>
      </div>
      <div class="variance-summary">
        <div class="vs-item">
          <span class="vs-num">{{ totalVariance }}</span>
          <span class="vs-label">差异总量</span>
        </div>
        <div class="vs-item warn">
          <span class="vs-num">{{ unresolved }}</span>
          <span class="vs-label">待核实</span>
        </div>
      </div>
    </header>

    <div class="variance-compare">
      <div class="compare-head">
        <span class="compare-col item">物料</span>
        <span class="compare-col batch">批次</span>
        <span class="compare-col loc">库位</span>
        <span class="compare-col num">系统数</span>
        <span class="compare-col num">实盘数</span>
        <span class="compare-col num diff">差异</span>
        <span class="compare-col rate">差异率</span>
        <span class="compare-col reason">原因分析</span>
        <span class="compare-col status">状态</span>
      </div>
      <div
        v-for="v in variances"
        :key="v.batch"
        :class="['compare-row', { active: activeVariance === v.batch }]"
        @click="activeVariance = activeVariance === v.batch ? null : v.batch"
      >
        <span class="compare-col item"><strong>{{ v.item }}</strong></span>
        <span class="compare-col batch code">{{ v.batch }}</span>
        <span class="compare-col loc">{{ v.location }}</span>
        <span class="compare-col num">{{ v.sysQty }}</span>
        <span class="compare-col num">{{ v.actualQty }}</span>
        <span :class="['compare-col', 'num', 'diff', v.variance > 0 ? 'pos' : 'neg']">{{ v.variance > 0 ? '+' : '' }}{{ v.variance }}</span>
        <span :class="['compare-col', 'rate', v.level]">{{ v.rate }}</span>
        <span class="compare-col reason">{{ v.reason }}</span>
        <span class="compare-col status">
          <i :class="['vs-badge', v.level]">{{ v.status }}</i>
        </span>
        <transition name="fade">
          <div v-if="activeVariance === v.batch" class="compare-action">
            <button type="button" @click.stop="activeAction = activeAction === 'adjust' ? '' : 'adjust'">生成调整单</button>
            <button type="button" class="outline" @click.stop="router.push('/inventory-trace?from=variance')">发起复盘</button>
          </div>
        </transition>
      </div>
    </div>

    <section class="variance-ai">
      <div class="ai-icon">AI</div>
      <div class="ai-body">
        <span class="ai-head">差异根因分析</span>
        <p>账实差异集中在 A 区临时库位和 C 区质检待处理区，疑似移库流水未及时过账和质检不合格未扣减导致。建议对 A-01-05 和 C-01-02 库位发起重点复盘，并对差异物料生成调整单。涉及移库事务的建议同步核查 wh_transfer_order 的完成状态。</p>
      </div>
      <div class="ai-ops">
        <button type="button" @click="activeAction = activeAction === 'batch-adjust' ? '' : 'batch-adjust'">批量生成调整单</button>
        <button type="button" class="outline" @click="exportReport">导出差异报告</button>
      </div>
    </section>

    <transition name="slide">
      <div v-if="activeAction === 'adjust'" class="action-panel">
        <div class="panel-title">
          <span>生成调整单</span>
          <button type="button" class="panel-back" @click="activeAction = ''">× 返回</button>
        </div>
        <div class="panel-body">
          <label>物料 <input value="连接器密封面" /></label>
          <label>调整数量 <input type="number" value="-3" /></label>
          <label>原因 <select><option>盘点差异调整</option><option>入库重复过账</option></select></label>
        </div>
        <div class="panel-foot">
          <button type="button" class="panel-btn" @click="activeAction = ''">确认提交</button>
          <button type="button" class="panel-btn outline" @click="activeAction = ''">取消</button>
        </div>
      </div>
    </transition>
    <transition name="slide">
      <div v-if="activeAction === 'batch-adjust'" class="action-panel">
        <div class="panel-title">
          <span>批量生成调整单</span>
          <button type="button" class="panel-back" @click="activeAction = ''">× 返回</button>
        </div>
        <div class="panel-body">
          <p>以下差异物料将批量生成调整单：</p>
          <div v-for="v in variances" :key="v.batch" class="panel-item">
            <span>{{ v.item }}</span><span>{{ v.variance > 0 ? '+' : '' }}{{ v.variance }}</span>
          </div>
        </div>
        <div class="panel-foot">
          <button type="button" class="panel-btn" @click="activeAction = ''">确认批量提交</button>
          <button type="button" class="panel-btn outline" @click="activeAction = ''">取消</button>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.variance-page {
  width: min(1080px, 100%);
  margin: 10px auto 0;
}

.variance-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.variance-back {
  border: 1px solid #dfe6e1;
  border-radius: 999px;
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.7);
  color: #6b7a72;
  font-size: 13px;
  cursor: pointer;
}

.variance-back:hover {
  background: #eef4f0;
}

.variance-header h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(24px, 3vw, 32px);
  font-weight: 600;
  color: #2a3a33;
}

.variance-header p {
  margin: 6px 0 0;
  color: #7d8983;
  font-size: 14px;
}

.variance-summary {
  display: flex;
  gap: 10px;
}

.vs-item {
  min-width: 70px;
  border: 1px solid #e2e8e4;
  border-radius: 10px;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.78);
  text-align: center;
}

.vs-num {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #33473f;
}

.vs-label {
  display: block;
  margin-top: 2px;
  color: #8b958f;
  font-size: 11px;
}

.vs-item.warn .vs-num {
  color: #b4553f;
}

.variance-compare {
  margin-top: 22px;
  border: 1px solid #e0e6e2;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
}

.compare-head {
  border-radius: 11px 11px 0 0;
}

.compare-row:last-child {
  border-radius: 0 0 11px 11px;
}

.compare-head {
  display: grid;
  grid-template-columns: 1.3fr 0.9fr 0.7fr 0.6fr 0.6fr 0.6fr 0.6fr 1.3fr 0.8fr;
  gap: 8px;
  padding: 12px 16px;
  background: #eef4f0;
  color: #6b7a72;
  font-size: 12px;
}

.compare-row {
  display: grid;
  grid-template-columns: 1.3fr 0.9fr 0.7fr 0.6fr 0.6fr 0.6fr 0.6fr 1.3fr 0.8fr;
  gap: 8px;
  align-items: center;
  padding: 12px 16px;
  border-top: 1px solid #eef2ef;
  font-size: 13px;
  color: #52615b;
  cursor: pointer;
  position: relative;
}

.compare-row:hover {
  background: #f2f7f4;
}

.compare-row.active {
  background: #e6f1ea;
}

.compare-col.item strong {
  color: #2f4139;
  font-size: 14px;
}

.compare-col.code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #7d8b85;
}

.compare-col.num {
  text-align: center;
}

.compare-col.diff {
  font-weight: 600;
}

.compare-col.diff.neg {
  color: #b4553f;
}

.compare-col.diff.pos {
  color: #4b7a5f;
}

.compare-col.rate {
  text-align: center;
  font-weight: 600;
}

.compare-col.rate.high { color: #b4553f; }
.compare-col.rate.medium { color: #d79a3a; }
.compare-col.rate.low { color: #6b8c7b; }

.compare-col.reason {
  color: #68776f;
  font-size: 12px;
}

.vs-badge {
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 11px;
  font-style: normal;
  white-space: nowrap;
}

.vs-badge.high { background: #f6e0d9; color: #b4553f; }
.vs-badge.medium { background: #f5edd7; color: #a97e2c; }
.vs-badge.low { background: #e2f0e7; color: #4b7a5f; }

.compare-action {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  gap: 6px;
  z-index: 2;
}

.compare-action button {
  border: 0;
  border-radius: 8px;
  padding: 5px 12px;
  background: #587766;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

.compare-action button.outline {
  border: 1px solid #cddbd3;
  background: #fff;
  color: #567566;
}

.variance-ai {
  margin-top: 22px;
  border: 1px solid #cfe0d6;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(232, 244, 237, 0.7), rgba(255, 255, 255, 0.9));
  padding: 20px;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;
}

.ai-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: #587766;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.ai-body {
  min-width: 0;
}

.ai-head {
  display: block;
  font-weight: 600;
  color: #2c3b34;
  font-size: 14px;
  margin-bottom: 6px;
}

.ai-body p {
  margin: 0;
  color: #4a5c52;
  font-size: 13px;
  line-height: 1.7;
}

.ai-ops {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ai-ops button {
  border: 0;
  border-radius: 8px;
  padding: 8px 16px;
  background: #587766;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}

.ai-ops button.outline {
  border: 1px solid #cddbd3;
  background: #fff;
  color: #567566;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
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
  margin-top: 0;
  overflow: hidden;
}
</style>
