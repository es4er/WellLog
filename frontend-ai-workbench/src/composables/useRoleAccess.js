import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useSession } from './useSession'
import { roles } from '../data/roles'
import { resolveAdminDemoContext } from '../router/menuRoutes'

export function canAccessAsRole(activeRoleId, requiredRoleId) {
  return activeRoleId === requiredRoleId || activeRoleId === 'admin'
}

export function canAccessAsAnyRole(activeRoleId, requiredRoleIds) {
  return requiredRoleIds.includes(activeRoleId) || activeRoleId === 'admin'
}

/** 管理员演示时，从 query.demoRole 推断当前预览的业务角色 */
export function useDemoRole() {
  const route = useRoute()
  const { activeRoleId } = useSession()
  const isAdminDemo = computed(() => activeRoleId.value === 'admin')
  const demoRoleId = computed(() => {
    if (!isAdminDemo.value) return activeRoleId.value
    const q = String(route.query.demoRole || '').trim()
    if (q && q !== 'admin') return q
    return activeRoleId.value
  })
  return { isAdminDemo, demoRoleId }
}

export function useRoleAccess(requiredRoleId) {
  const { activeRole, activeRoleId } = useSession()
  const { isAdminDemo } = useDemoRole()
  const allowed = computed(() => canAccessAsRole(activeRoleId.value, requiredRoleId))
  const requiredRole = computed(() => roles.find((role) => role.id === requiredRoleId))
  const requiredRoleLabel = computed(() => requiredRole.value?.label || requiredRoleId)
  return { allowed, isAdminDemo, activeRole, requiredRole, requiredRoleLabel }
}

export function useAnyRoleAccess(requiredRoleIds) {
  const { activeRoleId } = useSession()
  const { isAdminDemo } = useDemoRole()
  const allowed = computed(() => canAccessAsAnyRole(activeRoleId.value, requiredRoleIds))
  return { allowed, isAdminDemo }
}

/** 演示预览时解析界面展示角色（工作台 / 执行页等共用） */
export function useViewRole() {
  const { activeRole } = useSession()
  const { demoRoleId, isAdminDemo } = useDemoRole()
  const viewRole = computed(
    () => roles.find((role) => role.id === demoRoleId.value) ?? activeRole.value
  )
  return { viewRole, demoRoleId, isAdminDemo }
}

/** 界面称呼：真实登录用户优先；管理员演示其他角色时用演示 persona */
export function useDisplayName() {
  const route = useRoute()
  const { currentUser } = useSession()
  const { viewRole, isAdminDemo } = useViewRole()

  const displayName = computed(() => {
    const demoRole = String(route.query.demoRole || '').trim()
    if (isAdminDemo.value && demoRole && demoRole !== 'admin') {
      return viewRole.value.persona
    }
    return currentUser.value?.userName || viewRole.value.persona
  })

  const displayInitial = computed(() => displayName.value.slice(0, 1))

  return { displayName, displayInitial, viewRole, isAdminDemo }
}

/** 仓管专属页面：真实仓管账号 + 管理员演示仓管菜单 */
export function useWarehouseRoleAccess() {
  const route = useRoute()
  const { demoRoleId, isAdminDemo } = useDemoRole()
  const allowed = computed(() => {
    if (demoRoleId.value === 'warehouse') return true
    if (!isAdminDemo.value) return false
    return resolveAdminDemoContext(route.path, route.query.demoRole)?.roleId === 'warehouse'
  })
  return { allowed, demoRoleId, isAdminDemo }
}
