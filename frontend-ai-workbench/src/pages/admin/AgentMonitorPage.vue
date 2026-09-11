<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../../composables/useSession'
import { adminKpis, agentMonitors, aiOpsFeed } from '../../data/adminMockData'

const router = useRouter()
const { activeRole, goWorkbench } = useSession()
const allowed = computed(() => activeRole.value.id === 'admin')

if (!allowed.value) {
  router.replace('/module/' + encodeURIComponent('Agent监控'))
}

const toast = ref('')

function statusClass(status) {
  if (status === '忙碌') return 'busy'
  return 'ok'
}

function levelClass(level) {
  if (level === '告警') return 'danger'
  if (level === '预警') return 'warn'
  return 'ok'
}

function runAction(name) {
  if (name === '日报') {
    goWorkbench('生成今日系统运维日报，汇总 Agent 调用、告警与接口健康。')
    return
  }
  toast.value = `${name}已触发（演示）`
  setTimeout(() => {
    toast.value = ''
  }, 2200)
}
</script>

<template>
  <section v-if="allowed" class="admin-page">
    <header class="admin-header">
      <div>
        <h1>Agent 监控</h1>
      </div>
      <div class="admin-header-meta">
        <span class="admin-meta-chip">运行中 {{ adminKpis.agents.value }}</span>
        <span class="admin-meta-chip">{{ adminKpis.agents.hint }}</span>
      </div>
    </header>

    <div class="admin-grid-2">
      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>AI Agent 运行监控</h2>
          <small>今日调用 / 时延 / Token</small>
        </div>
        <table class="admin-table">
          <thead>
            <tr>
              <th>Agent</th>
              <th>状态</th>
              <th>今日调用</th>
              <th>平均时延</th>
              <th>Token</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="agent in agentMonitors" :key="agent.name">
              <td><strong>{{ agent.name }}</strong></td>
              <td>
                <span :class="['admin-status', statusClass(agent.status)]">{{ agent.status }}</span>
              </td>
              <td>{{ agent.calls }}</td>
              <td>{{ agent.latency }}</td>
              <td>{{ agent.tokens }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>AI 运维助手</h2>
          <small>自动巡检与建议</small>
        </div>
        <div class="admin-ops-feed">
          <div v-for="(item, idx) in aiOpsFeed" :key="idx" class="admin-ops-item">
            <span :class="['admin-status', levelClass(item.level)]">{{ item.level }}</span>
            <p>{{ item.text }}</p>
            <time>{{ item.time }}</time>
          </div>
        </div>
        <div class="admin-ops-actions">
          <button type="button" class="admin-btn primary" @click="runAction('日报')">生成日报</button>
          <button type="button" class="admin-btn" @click="runAction('系统巡检')">系统巡检</button>
          <button type="button" class="admin-btn" @click="runAction('导出日志')">导出日志</button>
        </div>
        <p v-if="toast" class="admin-empty" style="padding: 10px 0 0">{{ toast }}</p>
      </div>
    </div>
  </section>
</template>
