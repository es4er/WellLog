<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../../composables/useSession'
import { apiGet } from '../../api'

const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'admin')

if (!allowed.value) {
  router.replace('/module/' + encodeURIComponent('权限审计'))
}

const records = ref([])
const total = ref(0)
const pages = ref(0)
const current = ref(1)
const pageSize = 10
const loading = ref(false)
const error = ref('')

// 筛选条件
const filterType = ref('')   // '' | 'CREATE_USER' | 'DELETE_USER' | 'DISABLE_USER' | 'ASSIGN_ROLE' | 'UPDATE_ROLE_PERMISSION'
const filterResult = ref('') // '' | 'SUCCESS' | 'FAIL'

// 操作类型中文映射
const typeLabels = {
  CREATE_USER: '新增用户',
  DELETE_USER: '删除用户',
  DISABLE_USER: '禁用用户',
  ASSIGN_ROLE: '分配角色',
  UPDATE_ROLE_PERMISSION: '更新权限'
}

// 统计卡片
const totalChanges = ref(0)
const createUser = ref(0)
const deleteUser = ref(0)
const roleAssign = ref(0)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    params.set('page', String(current.value))
    params.set('size', String(pageSize))
    if (filterType.value) params.set('operationType', filterType.value)
    if (filterResult.value) params.set('result', filterResult.value)

    const data = await apiGet(`/audit/permission/list?${params.toString()}`)
    records.value = data.records ?? []
    total.value = data.total ?? 0
    pages.value = data.pages ?? 0
    current.value = data.current ?? 1
  } catch (err) {
    error.value = err?.message || '加载权限审计失败'
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const [allRes, createRes, deleteRes, assignRes] = await Promise.all([
      apiGet('/audit/permission/list?page=1&size=1'),
      apiGet('/audit/permission/list?page=1&size=1&operationType=CREATE_USER'),
      apiGet('/audit/permission/list?page=1&size=1&operationType=DELETE_USER'),
      apiGet('/audit/permission/list?page=1&size=1&operationType=ASSIGN_ROLE')
    ])
    totalChanges.value = allRes?.total ?? 0
    createUser.value = createRes?.total ?? 0
    deleteUser.value = deleteRes?.total ?? 0
    roleAssign.value = assignRes?.total ?? 0
  } catch {
    // ignore
  }
}

function setTypeFilter(val) {
  filterType.value = val
}

function clearFilters() {
  filterType.value = ''
  filterResult.value = ''
}

function goPage(p) {
  if (p >= 1 && p <= pages.value && p !== current.value) {
    current.value = p
    loadData()
  }
}

function formatTime(ts) {
  if (!ts) return '-'
  return ts.replace('T', ' ').substring(0, 19)
}

function typeLabel(type) {
  return typeLabels[type] || type || '-'
}

function statusClass(result) {
  if (result === 'SUCCESS') return 'ok'
  if (result === 'FAIL') return 'danger'
  return 'info'
}

function statusText(result) {
  if (result === 'SUCCESS') return '成功'
  if (result === 'FAIL') return '失败'
  return result || '-'
}

watch([filterType, filterResult], () => {
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
        <h1>权限审计</h1>
      </div>
      <div class="admin-header-meta">
        <span class="admin-badge">审计链路完整</span>
      </div>
    </header>

    <div class="admin-stat-strip">
      <div class="admin-stat-item info">
        <strong>{{ totalChanges }}</strong>
        <small>今日权限变更</small>
      </div>
      <div class="admin-stat-item ok">
        <strong>{{ createUser }}</strong>
        <small>新增用户</small>
      </div>
      <div class="admin-stat-item warn">
        <strong>{{ deleteUser }}</strong>
        <small>删除用户</small>
      </div>
      <div class="admin-stat-item info">
        <strong>{{ roleAssign }}</strong>
        <small>角色分配</small>
      </div>
    </div>

    <div class="admin-panel">
      <div class="admin-panel-head">
        <h2>最近权限变更记录</h2>
        <div class="admin-toolbar">
          <button type="button" class="admin-btn" :class="{ primary: !filterType }" @click="clearFilters">全部</button>
          <button
            v-for="(label, key) in typeLabels"
            :key="key"
            type="button"
            class="admin-btn"
            :class="{ primary: filterType === key }"
            @click="setTypeFilter(key)"
          >{{ label }}</button>
          <button type="button" class="admin-btn" :disabled="loading" @click="loadData">刷新</button>
        </div>
      </div>

      <p v-if="error" class="login-error">{{ error }}</p>

      <table class="admin-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>操作人</th>
            <th>操作</th>
            <th>对象</th>
            <th>IP</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in records" :key="row.auditId">
            <td>{{ formatTime(row.operatedAt) }}</td>
            <td>{{ row.userName || '-' }}</td>
            <td>{{ typeLabel(row.operationType) }}</td>
            <td>{{ row.beforeJson || row.objectType + '#' + row.objectId || '-' }}</td>
            <td>{{ row.ipAddress || '-' }}</td>
            <td>
              <span :class="['admin-status', statusClass(row.operationResult)]">
                {{ statusText(row.operationResult) }}
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
      <p v-if="!loading && !records.length" class="admin-empty">暂无权限变更记录。</p>
    </div>
  </section>
</template>
