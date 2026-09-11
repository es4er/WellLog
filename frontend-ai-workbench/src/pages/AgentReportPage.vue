<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AgentExecutionReport from '../components/AgentExecutionReport.vue'
import { useAgentTask } from '../composables/useAgentTask'

const router = useRouter()
const {
  agentLiveMode,
  agentAnalysis,
  agentVerdict,
  agentTaskStatus,
  agentTaskNo,
  currentEvidence,
  revealedSteps,
  agentSteps,
  streamSettled,
  showVerdict
} = useAgentTask()

const reportSteps = computed(() => (revealedSteps.value.length ? revealedSteps.value : agentSteps.value))
const canViewReport = computed(() => agentLiveMode.value && (showVerdict.value || streamSettled.value))

onMounted(() => {
  if (!canViewReport.value) {
    router.replace('/execution')
  }
})

function goBack() {
  router.push('/execution')
}
</script>

<template>
  <div v-if="canViewReport" class="report-page-shell">
    <AgentExecutionReport
      variant="page"
      :analysis="agentAnalysis"
      :fallback-text="agentVerdict"
      :task-status="agentTaskStatus"
      :task-no="agentTaskNo"
      :evidence-count="currentEvidence.length"
      :steps="reportSteps"
      @back="goBack"
    />
  </div>
</template>
