<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pathToMenu } from '../router/menuRoutes'

const route = useRoute()
const router = useRouter()

const menuTitle = computed(() => {
  if (route.params.menu) return decodeURIComponent(route.params.menu)
  return pathToMenu(route.path) || '模块'
})

function goWorkbench() {
  router.push('/workbench')
}
</script>

<template>
  <section class="module-placeholder">
    <span>🚧</span>
    <strong>{{ menuTitle }}</strong>
    <p>该模块正在建设中。你可以先返回工作台，通过 AI 指令完成相关任务。</p>
    <button type="button" @click="goWorkbench">返回工作台</button>
  </section>
</template>
