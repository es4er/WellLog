<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useDisplayName } from '../composables/useRoleAccess'
import { useSession } from '../composables/useSession'
import { roles } from '../data/roles'

const props = defineProps({
  requiredRoleId: { type: String, default: '' },
  requiredRoleIds: { type: Array, default: () => [] },
  moduleName: { type: String, default: '' }
})

const router = useRouter()
const { activeRole } = useSession()
const { displayName } = useDisplayName()

const requiredRoleLabel = computed(() => {
  if (props.requiredRoleId) {
    return roles.find((role) => role.id === props.requiredRoleId)?.label || props.requiredRoleId
  }
  const labels = props.requiredRoleIds
    .map((id) => roles.find((role) => role.id === id)?.label || id)
    .filter(Boolean)
  return labels.join(' / ')
})

function goWorkbench() {
  router.push('/workbench')
}
</script>

<template>
  <section class="role-access-denied">
    <span aria-hidden="true">🔒</span>
    <strong>无访问权限</strong>
    <p>
      当前账号为 <b>{{ activeRole.label }}</b>（{{ displayName }}），无法访问
      <template v-if="moduleName">「{{ moduleName }}」</template>
      <template v-else>此页面</template>。
      请使用 <b>{{ requiredRoleLabel }}</b> 账号登录，或联系系统管理员授权。
    </p>
    <button type="button" @click="goWorkbench">返回工作台</button>
  </section>
</template>
