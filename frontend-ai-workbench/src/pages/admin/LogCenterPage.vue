<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../../composables/useSession'
import { systemLogs } from '../../data/adminMockData'

const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'admin')

if (!allowed.value) {
  router.replace('/module/' + encodeURIComponent('日志中心'))
}

const levelFilter = ref('all')

const filtered = computed(() => {
  if (levelFilter.value === 'all') return systemLogs
  return systemLogs.filter((l) => l.level === levelFilter.value)
})

const counts = computed(() => ({
  INFO: systemLogs.filter((l) => l.level === 'INFO').length,
  WARN: systemLogs.filter((l) => l.level === 'WARN').length,
  ERROR: systemLogs.filter((l) => l.level === 'ERROR').length
}))
</script>

<template>
  <section v-if="allowed" class="admin-page">
    <header class="admin-header">
      <div>
        <h1>日志中心</h1>
      </div>
      <div class="admin-header-meta">
        <span class="admin-meta-chip">INFO {{ counts.INFO }}</span>
        <span class="admin-meta-chip">WARN {{ counts.WARN }}</span>
        <span class="admin-meta-chip">ERROR {{ counts.ERROR }}</span>
      </div>
    </header>

    <div class="admin-panel">
      <div class="admin-panel-head">
        <h2>系统日志 · 实时</h2>
        <div class="admin-toolbar">
          <button type="button" class="admin-btn" :class="{ primary: levelFilter === 'all' }" @click="levelFilter = 'all'">全部</button>
          <button type="button" class="admin-btn" :class="{ primary: levelFilter === 'INFO' }" @click="levelFilter = 'INFO'">INFO</button>
          <button type="button" class="admin-btn" :class="{ primary: levelFilter === 'WARN' }" @click="levelFilter = 'WARN'">WARN</button>
          <button type="button" class="admin-btn" :class="{ primary: levelFilter === 'ERROR' }" @click="levelFilter = 'ERROR'">ERROR</button>
        </div>
      </div>
      <div class="admin-log-list">
        <div v-for="(log, idx) in filtered" :key="idx" class="admin-log-item">
          <span :class="['admin-log-level', log.level]">{{ log.level }}</span>
          <time>{{ log.time }}</time>
          <p>{{ log.text }}</p>
        </div>
      </div>
      <p v-if="!filtered.length" class="admin-empty">无匹配日志</p>
    </div>
  </section>
</template>
