<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../../composables/useSession'
import { traceFlow, recentTraces } from '../../data/adminMockData'

const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'admin')

if (!allowed.value) {
  router.replace('/module/' + encodeURIComponent('证据追溯'))
}

const query = ref('')
const activeStage = ref('领料')
const searched = ref(false)

const resultHint = computed(() => {
  if (!searched.value) return ''
  const q = query.value.trim() || 'SO-88421'
  return `已定位「${q}」相关链路，当前节点：${activeStage.value}`
})

function doSearch() {
  searched.value = true
  const hit = recentTraces.find((t) =>
    [t.id, t.keyword].some((x) => x.toLowerCase().includes(query.value.trim().toLowerCase()))
  )
  if (hit) activeStage.value = hit.stage
}

function openTrace(row) {
  query.value = row.keyword
  activeStage.value = row.stage
  searched.value = true
}
</script>

<template>
  <section v-if="allowed" class="admin-page">
    <header class="admin-header">
      <div>
        <h1>证据追溯</h1>
      </div>
      <div class="admin-header-meta">
        <span class="admin-badge">全链路可追溯</span>
      </div>
    </header>

    <div class="admin-panel">
      <div class="admin-panel-head">
        <h2>全链路证据追溯</h2>
        <small>支持订单号、物料编码、批次号</small>
      </div>
      <div class="admin-toolbar" style="margin-bottom: 16px">
        <input
          v-model="query"
          class="admin-search"
          type="search"
          placeholder="输入订单号 / 物料编码 / 批次号"
          @keyup.enter="doSearch"
        />
        <button type="button" class="admin-btn primary" @click="doSearch">追溯</button>
      </div>

      <div class="admin-trace-flow">
        <div v-for="(step, idx) in traceFlow" :key="step" class="admin-trace-step">
          <span :class="['admin-trace-chip', { active: step === activeStage }]">{{ step }}</span>
          <span v-if="idx < traceFlow.length - 1" class="admin-trace-chevron">›</span>
        </div>
      </div>
      <p v-if="resultHint" class="admin-empty" style="padding: 0 0 8px; text-align: left">{{ resultHint }}</p>
    </div>

    <div class="admin-panel">
      <div class="admin-panel-head">
        <h2>最近追溯记录</h2>
        <small>点击可快速定位</small>
      </div>
      <table class="admin-table">
        <thead>
          <tr>
            <th>追溯 ID</th>
            <th>关键字</th>
            <th>当前节点</th>
            <th>时间</th>
            <th>状态</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in recentTraces" :key="row.id">
            <td>{{ row.id }}</td>
            <td>{{ row.keyword }}</td>
            <td>{{ row.stage }}</td>
            <td>{{ row.time }}</td>
            <td>
              <span :class="['admin-status', row.status === '进行中' ? 'warn' : 'ok']">{{ row.status }}</span>
            </td>
            <td>
              <button type="button" class="admin-btn" @click="openTrace(row)">查看</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
