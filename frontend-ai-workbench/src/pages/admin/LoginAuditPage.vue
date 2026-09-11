<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../../composables/useSession'
import { apiGet } from '../../api'

const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'admin')

if (!allowed.value) {
  router.replace('/module/' + encodeURIComponent('登录审计'))
}

const records = ref([])
const total = ref(0)
const pages = ref(0)
const current = ref(1)
const pageSize = 10
const loading = ref(false)
const error = ref('')

// 筛选条件
const filterResult = ref('')   // '' | 'SUCCESS' | 'FAIL'
const filterRisk = ref('')     // '' | 'NORMAL' | 'ABNORMAL'

// 统计
const successCount = ref(0)
const failCount = ref(0)
const abnormalCount = ref(0)
const ipCount = ref(0)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    params.set('page', String(current.value))
    params.set('size', String(pageSize))
    if (filterResult.value) params.set('result', filterResult.value)
    if (filterRisk.value) params.set('risk', filterRisk.value)

    const data = await apiGet(`/audit/login/list?${params.toString()}`)
    records.value = data.records ?? []
    total.value = data.total ?? 0
    pages.value = data.pages ?? 0
    current.value = data.current ?? 1
  } catch (err) {
    error.value = err?.message || '加载登录审计失败'
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const all = await apiGet('/audit/login/list?page=1&size=1')
    successCount.value = 0
    failCount.value = 0
    abnormalCount.value = 0
    ipCount.value = 0
    // 分别查询统计
    const [succRes, failRes, abnRes] = await Promise.all([
      apiGet('/audit/login/list?page=1&size=1&result=SUCCESS'),
      apiGet('/audit/login/list?page=1&size=1&result=FAIL'),
      apiGet('/audit/login/list?page=1&size=1&risk=ABNORMAL')
    ])
    successCount.value = succRes?.total ?? 0
    failCount.value = failRes?.total ?? 0
    abnormalCount.value = abnRes?.total ?? 0
    // 去重 IP 数量：取最近 200 条中统计
    const recent = await apiGet('/audit/login/list?page=1&size=200')
    const ips = (recent?.records ?? []).map((r) => r.ipAddress).filter(Boolean)
    ipCount.value = new Set(ips).size
  } catch {
    // 统计加载失败不影响主流程
  }
}

function setResultFilter(val) {
  filterResult.value = val
  filterRisk.value = ''
}

function setRiskFilter(val) {
  filterRisk.value = val
  filterResult.value = ''
}

function clearFilters() {
  filterResult.value = ''
  filterRisk.value = ''
}

function goPage(p) {
  if (p >= 1 && p <= pages.value && p !== current.value) {
    current.value = p
    loadData()
  }
}

function formatTime(ts) {
  if (!ts) return '-'
  // 后端返回 ISO 格式如 2026-07-10T10:28:01
  return ts.replace('T', ' ').substring(0, 19)
}

function parseDevice(ua) {
  if (!ua) return '-'
  if (ua.includes('Chrome')) return 'Chrome'
  if (ua.includes('Firefox')) return 'Firefox'
  if (ua.includes('Edge')) return 'Edge'
  if (ua.includes('Safari')) return 'Safari'
  return ua.length > 30 ? ua.substring(0, 30) + '...' : ua
}

watch([filterResult, filterRisk], () => {
  current.value = 1
  loadData()
})

onMounted(() => {
  loadData()
  loadStats()
})
</script>

<template>
  <section v-if="allowed" class="admin-page">
    <header class="admin-header">
      <div>
        <h1>登录审计</h1>
      </div>
      <div class="admin-header-meta">
        <span class="admin-meta-chip">异常 {{ abnormalCount }}</span>
        <span class="admin-meta-chip">失败 {{ failCount }}</span>
      </div>
    </header>

    <div class="admin-stat-strip">
      <div class="admin-stat-item ok">
        <strong>{{ successCount }}</strong>
        <small>成功登录</small>
      </div>
      <div class="admin-stat-item warn">
        <strong>{{ failCount }}</strong>
        <small>失败尝试</small>
      </div>
      <div class="admin-stat-item danger" style="border-color: #f0d4cf">
        <strong style="color: #b44538">{{ abnormalCount }}</strong>
        <small>异常风险</small>
      </div>
      <div class="admin-stat-item info">
        <strong>{{ ipCount }}</strong>
        <small>来源 IP</small>
      </div>
    </div>

    <div class="admin-panel">
      <div class="admin-panel-head">
        <h2>登录记录</h2>
        <div class="admin-toolbar">
          <button type="button" class="admin-btn" :class="{ primary: !filterResult && !filterRisk }" @click="clearFilters">全部</button>
          <button type="button" class="admin-btn" :class="{ primary: filterResult === 'SUCCESS' }" @click="setResultFilter('SUCCESS')">仅成功</button>
          <button type="button" class="admin-btn" :class="{ primary: filterResult === 'FAIL' }" @click="setResultFilter('FAIL')">仅失败</button>
          <button type="button" class="admin-btn" :class="{ primary: filterRisk === 'ABNORMAL' }" @click="setRiskFilter('ABNORMAL')">仅异常</button>
          <button type="button" class="admin-btn" :disabled="loading" @click="loadData">刷新</button>
        </div>
      </div>

      <p v-if="error" class="login-error">{{ error }}</p>

      <table class="admin-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>用户</th>
            <th>账号</th>
            <th>结果</th>
            <th>IP</th>
            <th>设备</th>
            <th>风险</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in records" :key="row.auditId">
            <td>{{ formatTime(row.operatedAt) }}</td>
            <td>{{ row.userName || '-' }}</td>
            <td>{{ row.userCode || '-' }}</td>
            <td>
              <span :class="['admin-status', row.operationResult === 'SUCCESS' ? 'ok' : 'danger']">
                {{ row.operationResult === 'SUCCESS' ? '成功' : '失败' }}
              </span>
            </td>
            <td>{{ row.ipAddress || '-' }}</td>
            <td :title="row.userAgent">{{ parseDevice(row.userAgent) }}</td>
            <td>
              <span :class="['admin-status', row.beforeJson === 'ABNORMAL' ? 'danger' : 'ok']">
                {{ row.beforeJson === 'ABNORMAL' ? '异常' : '正常' }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div class="user-pagination" v-if="pages > 1">
        <button type="button" class="admin-btn" :disabled="current <= 1" @click="goPage(current - 1)">上一页</button>
        <template v-for="p in pages" :key="p">
          <button
            type="button"
            class="admin-btn"
            :class="{ primary: p === current }"
            @click="goPage(p)"
          >{{ p }}</button>
        </template>
        <button type="button" class="admin-btn" :disabled="current >= pages" @click="goPage(current + 1)">下一页</button>
        <span class="page-info">共 {{ total }} 条，第 {{ current }}/{{ pages }} 页</span>
      </div>
      <p v-if="!loading && !records.length" class="admin-empty">暂无登录审计记录。</p>
    </div>
  </section>
</template>
