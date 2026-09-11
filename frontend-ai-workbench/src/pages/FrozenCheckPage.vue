<script setup>
import { ref, computed, onMounted } from 'vue'
import { useInventory } from '../composables/useInventory'
import { getAuthToken } from '../api'

const { doLogin, fetchFreezeRecords, unfreezeInventory, loading } = useInventory()

const searchQuery = ref('')
const pageSize = ref(20)
const currentPage = ref(1)

const frozenRecords = ref([])

function mapFreezeRecord(r) {
  return {
    id: r.freezeNo || r.id || ('FRZ-' + Date.now()),
    itemCode: r.itemCode || '',
    itemName: r.itemName || '',
    batchNo: r.batchNo || '',
    locationCode: r.locationCode || '',
    frozenQty: r.freezeQty ?? r.frozenQty ?? 0,
    reason: r.reason || '',
    operator: r.operatedBy ? '用户' + r.operatedBy : '',
    frozenAt: r.operatedAt ? r.operatedAt.replace('T',' ').slice(0,16) : '',
    unfrozenAt: null,
    status: r.freezeType === 'UNFREEZE' ? 'UNFROZEN' : 'FROZEN',
    recordId: r.freezeId || r.id,
  }
}

async function fetchFromApi() {
  if (!getAuthToken()) {
    try { await doLogin('admin', 'admin123') } catch { return }
  }
  try {
    const data = await fetchFreezeRecords()
    if (data && (Array.isArray(data) ? data.length : (data.data && data.data.length))) {
      const arr = Array.isArray(data) ? data : data.data
      frozenRecords.value = arr.map(mapFreezeRecord)
    }
  } catch {
    // API unavailable, keeps empty
  }
}

onMounted(fetchFromApi)

const statusMap = {
  FROZEN: { label: '已冻结', cls: 'status-frozen' },
  UNFROZEN: { label: '已解冻', cls: 'status-unfrozen' },
}

const filteredData = computed(() => {
  let list = frozenRecords.value
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    list = list.filter(i => i.itemCode.toLowerCase().includes(q) || i.itemName.toLowerCase().includes(q) || i.batchNo.toLowerCase().includes(q) || i.id.toLowerCase().includes(q))
  }
  return list
})

const totalItems = computed(() => filteredData.value.length)
const totalPages = computed(() => Math.ceil(totalItems.value / pageSize.value))
const pagedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredData.value.slice(start, start + pageSize.value)
})

const stats = computed(() => {
  const d = frozenRecords.value
  return {
    total: d.length,
    frozen: d.filter(i => i.status === 'FROZEN').length,
    unfrozen: d.filter(i => i.status === 'UNFROZEN').length,
  }
})

const moreMenuOpen = ref(null)

function toggleMore(idx) {
  moreMenuOpen.value = moreMenuOpen.value === idx ? null : idx
}

async function unfreeze(record) {
  if (isApiMode.value && record.recordId) {
    try {
      await unfreezeInventory(record.recordId, record.frozenQty, '手动解冻', 1)
    } catch (e) { console.warn('unfreezeInventory failed', e) }
  }
  record.status = 'UNFROZEN'
  record.unfrozenAt = new Date().toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  record.frozenQty = 0
  record.reason += '（已手动解冻）'
  moreMenuOpen.value = null
}

function changePage(p) {
  if (p < 1 || p > totalPages.value) return
  currentPage.value = p
}

function changeSize(size) {
  pageSize.value = size
  currentPage.value = 1
}
</script>

<template>
  <div class="frozen-page">
    <header class="frozen-header">
      <div>
        <h1>冻结 / 解冻记录</h1>
      </div>
      <div class="frozen-strip">
        <div class="strip-item"><strong class="num">{{ stats.total }}</strong><span class="lbl">总记录</span></div>
        <div class="strip-item"><strong class="num orange">{{ stats.frozen }}</strong><span class="lbl">已冻结</span></div>
        <div class="strip-item"><strong class="num green">{{ stats.unfrozen }}</strong><span class="lbl">已解冻</span></div>
      </div>
    </header>

    <div class="frozen-toolbar">
      <div class="toolbar-left">
        <button class="tb-btn primary">新增冻结</button>
        <button class="tb-btn">导入</button>
        <button class="tb-btn">高级查询</button>
      </div>
      <div class="toolbar-right">
        <input v-model="searchQuery" placeholder="搜索记录编号 / 物料编码 / 名称 / 批次号…" class="tb-search" />
      </div>
    </div>

    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th class="col-id">冻结记录编号</th>
            <th class="col-code">物料编码</th>
            <th class="col-name">物料名称</th>
            <th class="col-batch">批次号</th>
            <th class="col-loc">库位</th>
            <th class="col-num">冻结数量</th>
            <th class="col-reason">冻结原因</th>
            <th class="col-op">操作人</th>
            <th class="col-date">冻结时间</th>
            <th class="col-date">解冻时间</th>
            <th class="col-status">状态</th>
            <th class="col-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, idx) in pagedData" :key="item.id">
            <td class="col-id"><span class="id-text">{{ item.id }}</span></td>
            <td class="col-code"><span class="code-text">{{ item.itemCode }}</span></td>
            <td class="col-name">{{ item.itemName }}</td>
            <td class="col-batch"><span class="batch-text">{{ item.batchNo }}</span></td>
            <td class="col-loc"><span class="loc-text">{{ item.locationCode }}</span></td>
            <td class="col-num" :class="{ 'text-orange': item.frozenQty > 0 }">{{ item.frozenQty || '—' }}</td>
            <td class="col-reason">{{ item.reason }}</td>
            <td class="col-op">{{ item.operator }}</td>
            <td class="col-date">{{ item.frozenAt }}</td>
            <td class="col-date">{{ item.unfrozenAt || '—' }}</td>
            <td class="col-status">
              <span :class="['status-tag', statusMap[item.status]?.cls]">
                <i class="status-dot"></i>
                {{ statusMap[item.status]?.label }}
              </span>
            </td>
            <td class="col-actions">
              <div class="action-cell">
                <button class="action-btn edit-btn">编辑</button>
                <div class="more-wrap">
                  <button class="action-btn more-btn" @click="toggleMore(idx)">更多 ▾</button>
                  <div v-if="moreMenuOpen === idx" class="more-dropdown">
                    <button v-if="item.status === 'FROZEN'" class="drop-item" @click="unfreeze(item)">手动解冻</button>
                    <button class="drop-item">查看详情</button>
                  </div>
                </div>
              </div>
            </td>
          </tr>
          <tr v-if="pagedData.length === 0">
            <td colspan="12" class="empty-cell">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="table-pagination">
      <span class="page-info">共 {{ totalItems }} 条数据</span>
      <div class="page-size">
        <span>每页</span>
        <select v-model="pageSize" @change="changeSize(pageSize)">
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
          <option :value="100">100</option>
        </select>
      </div>
      <div class="page-controls">
        <button :disabled="currentPage === 1" @click="changePage(currentPage - 1)">‹</button>
        <span class="page-num">{{ currentPage }} / {{ totalPages || 1 }}</span>
        <button :disabled="currentPage === totalPages || totalPages === 0" @click="changePage(currentPage + 1)">›</button>
      </div>
    </div>

    <div v-if="moreMenuOpen !== null" class="more-overlay" @click="moreMenuOpen = null"></div>
  </div>
</template>

<style scoped>
.frozen-page {
  width: min(1200px, 100%);
  margin: 0 auto;
  font-family: inherit;
}

.frozen-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 6px;
}

.frozen-header h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(26px, 3vw, 34px);
  font-weight: 600;
  color: #2a3a33;
}

.frozen-subtitle {
  margin: 6px 0 0;
  color: #7d8983;
  font-size: 14px;
}

.frozen-strip {
  display: flex;
  gap: 6px;
}

.strip-item {
  min-width: 64px;
  border: 1px solid #e2e8e4;
  border-radius: 10px;
  padding: 8px 14px;
  background: rgba(255,255,255,0.78);
  text-align: center;
}

.strip-item .num {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: #33473f;
  line-height: 1.2;
}

.strip-item .num.green { color: #3b7a5a; }
.strip-item .num.orange { color: #c98a2e; }

.strip-item .lbl {
  display: block;
  margin-top: 2px;
  color: #8b958f;
  font-size: 11px;
}

.frozen-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding: 12px 16px;
  background: rgba(255,255,255,0.85);
  border: 1px solid #e2eae5;
  border-radius: 10px;
}

.toolbar-left { display: flex; gap: 8px; }
.toolbar-right { display: flex; gap: 8px; }

.tb-btn {
  border: 1px solid #cddbd3;
  border-radius: 8px;
  padding: 7px 16px;
  background: #fff;
  color: #567566;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.12s;
  white-space: nowrap;
}

.tb-btn:hover { background: #eef4f0; }

.tb-btn.primary {
  background: #587766;
  border-color: #587766;
  color: #fff;
}

.tb-btn.primary:hover { background: #4a6a59; }

.tb-search {
  border: 1px solid #dfe6e1;
  border-radius: 8px;
  padding: 7px 14px;
  font-size: 13px;
  color: #33473f;
  outline: none;
  background: #fff;
  width: 260px;
}

.tb-search::placeholder { color: #bccbc2; }

.table-wrap {
  margin-top: 12px;
  border: 1px solid #e2eae5;
  border-radius: 10px;
  overflow: hidden;
  background: rgba(255,255,255,0.9);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.data-table thead { background: #f0f5f2; }

.data-table th {
  padding: 11px 10px;
  font-size: 12px;
  font-weight: 600;
  color: #5f7268;
  text-align: left;
  white-space: nowrap;
  border-bottom: 1px solid #e2eae5;
}

.data-table td {
  padding: 11px 10px;
  font-size: 13px;
  color: #3f554b;
  border-bottom: 1px solid #ecf1ee;
}

.data-table tbody tr:hover { background: #f6f9f7; }

.col-id { width: 140px; }
.col-code { width: 100px; }
.col-name { width: 120px; }
.col-batch { width: 110px; }
.col-loc { width: 90px; }
.col-num { width: 80px; text-align: right; }
.col-reason { width: 150px; }
.col-op { width: 80px; }
.col-date { width: 130px; }
.col-status { width: 80px; }
.col-actions { width: 130px; }

.id-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #31433c;
  font-weight: 600;
}

.code-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #31433c;
  font-weight: 600;
}

.batch-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #6c7d75;
}

.loc-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #437a9e;
  font-weight: 500;
}

.text-orange { color: #c98a2e; font-weight: 600; }

/* Status tag */
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 500;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-frozen { background: #fdf0e8; color: #c98a2e; }
.status-frozen .status-dot { background: #c98a2e; }

.status-unfrozen { background: #e0f0e6; color: #3b7a5a; }
.status-unfrozen .status-dot { background: #3b7a5a; }

/* Actions */
.action-cell { display: flex; gap: 4px; align-items: center; }

.action-btn {
  border: 0;
  border-radius: 6px;
  padding: 5px 10px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s;
}

.edit-btn { background: #eef4f0; color: #567566; }
.edit-btn:hover { background: #dce8e0; }

.more-btn {
  background: #fff;
  color: #567566;
  border: 1px solid #cddbd3;
}

.more-btn:hover { background: #f0f5f2; }

.more-wrap { position: relative; }

.more-dropdown {
  position: absolute;
  right: 0;
  top: 100%;
  margin-top: 4px;
  background: #fff;
  border: 1px solid #e2eae5;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
  z-index: 100;
  min-width: 110px;
  overflow: hidden;
}

.drop-item {
  display: block;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 9px 16px;
  font-size: 13px;
  color: #46544f;
  text-align: left;
  cursor: pointer;
  transition: background 0.1s;
}

.drop-item:hover { background: #f0f5f2; }

.drop-item + .drop-item { border-top: 1px solid #ecf1ee; }

.more-overlay {
  position: fixed;
  inset: 0;
  z-index: 99;
}

.empty-cell {
  text-align: center;
  padding: 40px 0 !important;
  color: #9caaa3;
  font-size: 14px;
}

/* Pagination */
.table-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 12px;
  padding: 10px 16px;
  background: rgba(255,255,255,0.85);
  border: 1px solid #e2eae5;
  border-radius: 10px;
}

.page-info { color: #8b958f; font-size: 13px; margin-right: auto; }

.page-size {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #8b958f;
  font-size: 13px;
}

.page-size select {
  border: 1px solid #dfe6e1;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  color: #46544f;
  background: #fff;
  outline: none;
}

.page-controls { display: flex; align-items: center; gap: 8px; }

.page-controls button {
  border: 1px solid #cddbd3;
  border-radius: 6px;
  padding: 4px 10px;
  background: #fff;
  color: #567566;
  font-size: 16px;
  cursor: pointer;
  line-height: 1;
}

.page-controls button:disabled { opacity: 0.4; cursor: not-allowed; }
.page-controls button:hover:not(:disabled) { background: #eef4f0; }

.page-num {
  font-size: 13px;
  color: #46544f;
  min-width: 60px;
  text-align: center;
}
</style>
