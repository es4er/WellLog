<script setup>
import { computed, ref } from 'vue'
import { agentLayers, agentProfiles } from '../data/agentProfiles'

const agentLayerFilter = ref('all')
const agentSearchQuery = ref('')
const selectedAgentId = ref(null)

const selectedAgent = computed(() => agentProfiles.find((a) => a.id === selectedAgentId.value) ?? null)
const filteredAgents = computed(() => {
  const q = agentSearchQuery.value.trim().toLowerCase()
  return agentProfiles.filter((agent) => {
    const matchLayer = agentLayerFilter.value === 'all' || agent.layer === agentLayerFilter.value
    if (!matchLayer) return false
    if (!q) return true
    const haystack = [
      agent.label,
      agent.codeName,
      agent.summary,
      agent.title,
      ...agent.skills,
      ...agent.knowledgeTags
    ].join(' ').toLowerCase()
    return haystack.includes(q)
  })
})

function openAgentProfile(agentId) {
  selectedAgentId.value = agentId
}

function closeAgentProfile() {
  selectedAgentId.value = null
}
</script>

<template>
  <section class="agent-union">
    <template v-if="!selectedAgent">
      <header class="agent-union-header">
        <div>
          <h1>智能体工会</h1>
        </div>
      </header>

      <div class="agent-union-toolbar">
        <label class="agent-search">
          <input
            v-model="agentSearchQuery"
            type="search"
            placeholder="搜索智能体、技能或知识标签"
          />
        </label>
        <div class="agent-layer-tabs">
          <button
            v-for="layer in agentLayers"
            :key="layer.id"
            :class="{ active: agentLayerFilter === layer.id }"
            type="button"
            @click="agentLayerFilter = layer.id"
          >
            <strong>{{ layer.label }}</strong>
            <small>{{ layer.desc }}</small>
          </button>
        </div>
      </div>

      <div class="agent-grid">
        <button
          v-for="agent in filteredAgents"
          :key="agent.id"
          class="agent-card"
          type="button"
          @click="openAgentProfile(agent.id)"
        >
          <div class="agent-avatar-wrap">
            <span class="agent-level">{{ agent.level }}</span>
            <img class="agent-avatar" :src="agent.photo" :alt="agent.label" />
          </div>
          <strong class="agent-name">{{ agent.label }}</strong>
          <small class="agent-code">{{ agent.codeName }}</small>
          <p class="agent-summary">{{ agent.summary }}</p>
        </button>
      </div>
      <p v-if="filteredAgents.length === 0" class="agent-empty">未找到匹配的智能体，请调整搜索或筛选条件。</p>
    </template>

    <template v-else>
      <button class="agent-back" type="button" @click="closeAgentProfile">‹ 返回智能体工会</button>

      <section class="agent-profile-hero">
        <img class="agent-profile-avatar" :src="selectedAgent.photo" :alt="selectedAgent.label" />
        <div class="agent-profile-meta">
          <div class="agent-profile-title-row">
            <h1>{{ selectedAgent.label }}</h1>
            <span class="agent-level">{{ selectedAgent.level }}</span>
          </div>
          <strong class="agent-profile-role">{{ selectedAgent.title }}</strong>
          <small class="agent-profile-layer">{{ selectedAgent.layerLabel }}</small>
          <p>{{ selectedAgent.summary }}</p>
          <div class="agent-profile-class">
            <code>{{ selectedAgent.packagePath }}.{{ selectedAgent.className }}</code>
          </div>
        </div>
      </section>

      <div class="agent-profile-row">
        <section class="agent-profile-card">
          <h2>核心技能</h2>
          <div class="agent-tag-list">
            <span v-for="skill in selectedAgent.skills" :key="skill">{{ skill }}</span>
          </div>
        </section>
        <section class="agent-profile-card">
          <h2>知识标签</h2>
          <div class="agent-tag-list knowledge">
            <span v-for="tag in selectedAgent.knowledgeTags" :key="tag">{{ tag }}</span>
          </div>
        </section>
      </div>

      <section class="agent-profile-card wide">
        <h2>知识库</h2>
        <p class="agent-knowledge-text">{{ selectedAgent.knowledgeBase }}</p>
      </section>

      <section class="agent-profile-card wide">
        <h2>履历</h2>
        <div class="agent-resume-list">
          <div v-for="(row, index) in selectedAgent.resume" :key="index" class="agent-resume-row">
            <span class="agent-resume-time">{{ row[0] }}</span>
            <p>{{ row[1] }}</p>
          </div>
        </div>
      </section>

      <section class="agent-profile-card wide">
        <h2>支持任务类型</h2>
        <div class="agent-tag-list task-types">
          <span v-for="task in selectedAgent.taskTypes" :key="task">{{ task }}</span>
        </div>
      </section>
    </template>
  </section>
</template>
