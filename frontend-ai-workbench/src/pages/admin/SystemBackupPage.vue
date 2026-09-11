<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../../composables/useSession'
import { backupJobs } from '../../data/adminMockData'

const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'admin')

if (!allowed.value) {
  router.replace('/module/' + encodeURIComponent('系统备份'))
}

const successCount = computed(() => backupJobs.filter((b) => b.status === '成功').length)
const failCount = computed(() => backupJobs.filter((b) => b.status === '失败').length)
</script>

<template>
  <section v-if="allowed" class="admin-page">
    <header class="admin-header">
      <div>
        <h1>系统备份</h1>
      </div>
      <div class="admin-header-meta">
        <span class="admin-meta-chip">成功 {{ successCount }}</span>
        <span class="admin-meta-chip">失败 {{ failCount }}</span>
        <button type="button" class="admin-btn primary">立即备份</button>
      </div>
    </header>

    <div class="admin-stat-strip">
      <div class="admin-stat-item ok">
        <strong>{{ successCount }}</strong>
        <small>近期成功</small>
      </div>
      <div class="admin-stat-item warn">
        <strong>{{ failCount }}</strong>
        <small>近期失败</small>
      </div>
      <div class="admin-stat-item info">
        <strong>02:00</strong>
        <small>每日窗口</small>
      </div>
      <div class="admin-stat-item">
        <strong>90 天</strong>
        <small>保留策略</small>
      </div>
    </div>

    <div class="admin-panel">
      <div class="admin-panel-head">
        <h2>备份任务记录</h2>
        <small>含 MySQL、MinIO 与配置快照</small>
      </div>
      <table class="admin-table">
        <thead>
          <tr>
            <th>任务 ID</th>
            <th>类型</th>
            <th>目标</th>
            <th>开始</th>
            <th>结束</th>
            <th>大小</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="job in backupJobs" :key="job.id">
            <td>{{ job.id }}</td>
            <td>{{ job.type }}</td>
            <td>{{ job.target }}</td>
            <td>{{ job.started }}</td>
            <td>{{ job.finished }}</td>
            <td>{{ job.size }}</td>
            <td>
              <span :class="['admin-status', job.status === '成功' ? 'ok' : 'danger']">{{ job.status }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
