<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../../composables/useSession'
import { adminKpis, integrationServices } from '../../data/adminMockData'

const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'admin')

if (!allowed.value) {
  router.replace('/module/' + encodeURIComponent('接口监控'))
}

const sparkHeights = computed(() => {
  const vals = adminKpis.apiCalls.spark
  const max = Math.max(...vals)
  return vals.map((v) => `${Math.max(18, (v / max) * 100)}%`)
})
</script>

<template>
  <section v-if="allowed" class="admin-page">
    <header class="admin-header">
      <div>
        <h1>接口监控</h1>
      </div>
      <div class="admin-header-meta">
        <span class="admin-badge">集成链路正常</span>
      </div>
    </header>

    <div class="admin-kpi-row" style="grid-template-columns: repeat(3, 1fr)">
      <div class="admin-kpi-card">
        <small>API 调用量</small>
        <strong>{{ adminKpis.apiCalls.value }}</strong>
        <em>{{ adminKpis.apiCalls.hint }}</em>
        <div class="admin-spark">
          <span v-for="(h, i) in sparkHeights" :key="i" :style="{ height: h }" />
        </div>
      </div>
      <div class="admin-kpi-card">
        <small>已连接系统</small>
        <strong>{{ integrationServices.length }}</strong>
        <em>ERP / MES / PLM / OA / AI / SMS</em>
      </div>
      <div class="admin-kpi-card warn">
        <small>需关注接口</small>
        <strong>{{ integrationServices.filter((s) => s.tone === 'warn').length }}</strong>
        <em>响应略高于基线</em>
      </div>
    </div>

    <div class="admin-panel">
      <div class="admin-panel-head">
        <h2>系统集成状态</h2>
        <small>实时连通性与延迟</small>
      </div>
      <div class="admin-integ-grid">
        <div v-for="svc in integrationServices" :key="svc.name" class="admin-integ-card">
          <strong>{{ svc.name }}</strong>
          <span :class="['admin-status', svc.tone === 'ok' ? 'ok' : 'warn']">{{ svc.status }}</span>
          <span>响应 {{ svc.latency }} ms</span>
        </div>
      </div>
    </div>
  </section>
</template>
