<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../../composables/useSession'
import {
  adminClock,
  adminKpis,
  topologyNodes,
  healthScore,
  configItems
} from '../../data/adminMockData'

const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'admin')

if (!allowed.value) {
  router.replace('/module/' + encodeURIComponent('系统配置'))
}

const cpuSpark = computed(() => {
  const vals = adminKpis.cpu.spark
  const max = Math.max(...vals)
  return vals.map((v) => `${Math.max(18, (v / max) * 100)}%`)
})

const memSpark = computed(() => {
  const vals = adminKpis.memory.spark
  const max = Math.max(...vals)
  return vals.map((v) => `${Math.max(18, (v / max) * 100)}%`)
})

const stars = computed(() => '★'.repeat(healthScore.stars) + '☆'.repeat(5 - healthScore.stars))
</script>

<template>
  <section v-if="allowed" class="admin-page">
    <header class="admin-header">
      <div>
        <h1>系统配置</h1>
      </div>
      <div class="admin-header-meta">
        <span class="admin-badge">正常运行</span>
        <span class="admin-meta-chip">{{ adminClock }}</span>
      </div>
    </header>

    <div class="admin-kpi-row" style="grid-template-columns: repeat(4, 1fr)">
      <div class="admin-kpi-card">
        <small>CPU 使用率</small>
        <strong>{{ adminKpis.cpu.value }}</strong>
        <em>{{ adminKpis.cpu.hint }}</em>
        <div class="admin-spark">
          <span v-for="(h, i) in cpuSpark" :key="'c' + i" :style="{ height: h }" />
        </div>
      </div>
      <div class="admin-kpi-card">
        <small>内存使用率</small>
        <strong>{{ adminKpis.memory.value }}</strong>
        <em>{{ adminKpis.memory.hint }}</em>
        <div class="admin-spark">
          <span v-for="(h, i) in memSpark" :key="'m' + i" :style="{ height: h }" />
        </div>
      </div>
      <div class="admin-kpi-card">
        <small>健康评分</small>
        <strong>{{ healthScore.score }}</strong>
        <em>五维综合</em>
      </div>
      <div class="admin-kpi-card">
        <small>配置项</small>
        <strong>{{ configItems.length }}</strong>
        <em>安全 / 接口 / AI / 治理</em>
      </div>
    </div>

    <div class="admin-grid-2">
      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>系统运行拓扑</h2>
          <small>应用 · 中间件 · AI 网关</small>
        </div>
        <div class="admin-topo">
          <div class="admin-topo-row">
            <div class="admin-topo-node" :class="topologyNodes.app.status">{{ topologyNodes.app.name }}</div>
            <span class="admin-topo-arrow">→</span>
            <div class="admin-topo-node" :class="topologyNodes.spring.status">{{ topologyNodes.spring.name }}</div>
          </div>
          <div class="admin-topo-row">
            <div class="admin-topo-node" :class="topologyNodes.mysql.status">{{ topologyNodes.mysql.name }}</div>
            <div class="admin-topo-node" :class="topologyNodes.redis.status">{{ topologyNodes.redis.name }}</div>
            <div class="admin-topo-node" :class="topologyNodes.minio.status">{{ topologyNodes.minio.name }}</div>
          </div>
          <span class="admin-topo-arrow">↓</span>
          <div class="admin-topo-row">
            <div class="admin-topo-node" :class="topologyNodes.gateway.status">{{ topologyNodes.gateway.name }}</div>
          </div>
          <div class="admin-topo-row">
            <div class="admin-topo-node" :class="topologyNodes.qwen.status">{{ topologyNodes.qwen.name }}</div>
            <div class="admin-topo-node" :class="topologyNodes.deepseek.status">{{ topologyNodes.deepseek.name }}</div>
            <div class="admin-topo-node" :class="topologyNodes.gemini.status">{{ topologyNodes.gemini.name }}</div>
          </div>
        </div>
      </div>

      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>系统健康评分</h2>
          <small>综合运行质量</small>
        </div>
        <div class="admin-health">
          <div class="admin-health-gauge">
            <div>
              <strong>{{ healthScore.score }}</strong>
              <small>分</small>
              <div class="admin-health-stars">{{ stars }}</div>
            </div>
          </div>
          <div class="admin-health-bars">
            <div v-for="dim in healthScore.dims" :key="dim.label" class="admin-health-bar-row">
              <span>{{ dim.label }}</span>
              <div class="admin-health-bar-track">
                <div class="admin-health-bar-fill" :style="{ width: dim.value + '%' }" />
              </div>
              <b>{{ dim.value }}</b>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="admin-panel">
      <div class="admin-panel-head">
        <h2>关键参数</h2>
        <small>演示配置，保存需对接后端</small>
      </div>
      <div class="admin-config-grid">
        <div v-for="item in configItems" :key="item.key" class="admin-config-item">
          <small>{{ item.group }}</small>
          <strong>{{ item.key }}</strong>
          <span>{{ item.value }}</span>
        </div>
      </div>
    </div>
  </section>
</template>
