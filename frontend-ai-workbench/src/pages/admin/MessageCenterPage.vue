<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../../composables/useSession'
import { messageCenter, adminKpis } from '../../data/adminMockData'

const router = useRouter()
const { activeRole } = useSession()
const allowed = computed(() => activeRole.value.id === 'admin')

if (!allowed.value) {
  router.replace('/module/' + encodeURIComponent('消息中心'))
}

const messages = ref(messageCenter.map((m) => ({ ...m })))
const filter = ref('all')

const unread = computed(() => messages.value.filter((m) => !m.read).length)

const filtered = computed(() => {
  if (filter.value === 'unread') return messages.value.filter((m) => !m.read)
  if (filter.value === 'alert') return messages.value.filter((m) => m.level === '告警')
  return messages.value
})

function levelClass(level) {
  if (level === '告警') return 'danger'
  if (level === '预警') return 'warn'
  return 'info'
}

function markRead(msg) {
  msg.read = true
}

function markAllRead() {
  messages.value.forEach((m) => {
    m.read = true
  })
}
</script>

<template>
  <section v-if="allowed" class="admin-page">
    <header class="admin-header">
      <div>
        <h1>消息中心</h1>
      </div>
      <div class="admin-header-meta">
        <span class="admin-meta-chip">未读 {{ unread }}</span>
        <span class="admin-meta-chip">系统告警 {{ adminKpis.alerts.value }}</span>
        <button type="button" class="admin-btn" @click="markAllRead">全部已读</button>
      </div>
    </header>

    <div class="admin-panel">
      <div class="admin-panel-head">
        <h2>消息列表</h2>
        <div class="admin-toolbar">
          <button type="button" class="admin-btn" :class="{ primary: filter === 'all' }" @click="filter = 'all'">全部</button>
          <button type="button" class="admin-btn" :class="{ primary: filter === 'unread' }" @click="filter = 'unread'">未读</button>
          <button type="button" class="admin-btn" :class="{ primary: filter === 'alert' }" @click="filter = 'alert'">告警</button>
        </div>
      </div>
      <div class="admin-msg-list">
        <button
          v-for="msg in filtered"
          :key="msg.id"
          type="button"
          :class="['admin-msg-item', { unread: !msg.read }]"
          @click="markRead(msg)"
        >
          <span :class="['admin-status', levelClass(msg.level)]">{{ msg.level }}</span>
          <div>
            <strong>{{ msg.title }}</strong>
            <small>{{ msg.source }} · {{ msg.id }}</small>
          </div>
          <time>{{ msg.time }}</time>
        </button>
      </div>
      <p v-if="!filtered.length" class="admin-empty">暂无消息</p>
    </div>
  </section>
</template>
