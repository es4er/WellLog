import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { menuToPath, pathToMenu, isUnionRoute, resolveAdminDemoContext } from '../router/menuRoutes'
import { qualityNavGroups, qualityTopItems } from '../data/qualityNav'
import { pmcNavGroups, pmcTopItems } from '../data/pmcNav'
import { warehouseNavGroups, warehouseTopItems } from '../data/warehouseNav'
import { workerNavGroups, workerTopItems } from '../data/workerNav'
import { adminNavGroups, adminTopItems } from '../data/adminNav'
import { inventoryNavGroups, inventoryTopItems } from '../data/inventoryNav'
import { flattenAdminDemoLabels } from '../data/adminDemoNav'
import { roles } from '../data/roles'

const HOME_KEY = '工作台'

/** 按角色缓存已打开页签，切换角色不丢（同会话内） */
const tabsByRole = ref({})

function flattenNavLabels(topItems = [], groups = []) {
  const map = new Map()
  for (const item of topItems) map.set(item.key, item.label)
  for (const group of groups) {
    for (const item of group.items || []) map.set(item.key, item.label)
  }
  return map
}

const NAV_LABELS = {
  quality: flattenNavLabels(qualityTopItems, qualityNavGroups),
  pmc: flattenNavLabels(pmcTopItems, pmcNavGroups),
  warehouse: flattenNavLabels(warehouseTopItems, warehouseNavGroups),
  worker: flattenNavLabels(workerTopItems, workerNavGroups),
  admin: new Map([
    ...flattenNavLabels(adminTopItems, adminNavGroups),
    ...flattenAdminDemoLabels()
  ]),
  inventory: flattenNavLabels(inventoryTopItems, inventoryNavGroups)
}

function ensureRoleTabs(roleId) {
  if (!tabsByRole.value[roleId]) {
    tabsByRole.value[roleId] = [
      { key: HOME_KEY, label: '首页', path: menuToPath(HOME_KEY, roleId), closable: false }
    ]
  }
  return tabsByRole.value[roleId]
}

function resolveLabel(menuKey, roleId) {
  if (!menuKey) return '未命名'
  if (menuKey === HOME_KEY) return '首页'

  const direct = NAV_LABELS[roleId]?.get(menuKey)
  if (direct) return direct

  // 管理员演示页签使用 `demoRoleId:menuKey`，兜底解析为「角色名：菜单名」
  const colonIndex = menuKey.indexOf(':')
  if (roleId === 'admin' && colonIndex > 0) {
    const demoRoleId = menuKey.slice(0, colonIndex)
    const actualMenuKey = menuKey.slice(colonIndex + 1)
    const roleName = roles.find((role) => role.id === demoRoleId)?.label || demoRoleId
    const menuName = NAV_LABELS[demoRoleId]?.get(actualMenuKey) || actualMenuKey
    return `${roleName}：${menuName}`
  }

  return menuKey
}

function pathKey(path, query = {}) {
  const base = (path || '').split('?')[0]
  const demoRole = query?.demoRole
  return demoRole ? `${base}?demoRole=${demoRole}` : base
}

function resolveAdminTabMeta(path, query) {
  const demo = resolveAdminDemoContext(path, query?.demoRole)
  if (!demo) {
    const menuKey = pathToMenu(path, 'admin')
    return { menuKey, tabPath: pathKey(path), labelKey: menuKey }
  }
  const tabPath = pathKey(path, { demoRole: demo.roleId })
  return {
    menuKey: `${demo.roleId}:${demo.menuKey}`,
    tabPath,
    labelKey: `${demo.roleId}:${demo.menuKey}`
  }
}

function parseTabRoute(tabPath) {
  const [path, search = ''] = (tabPath || '').split('?')
  if (!search) return { path }
  const query = {}
  for (const part of search.split('&')) {
    const [key, value = ''] = part.split('=')
    if (key) query[key] = decodeURIComponent(value)
  }
  return { path, query }
}

export function useWorkspaceTabs(activeRole) {
  const route = useRoute()
  const router = useRouter()

  const roleId = computed(() => activeRole.value?.id || 'guest')
  const openTabs = computed(() => ensureRoleTabs(roleId.value))
  const showTabs = computed(() => !isUnionRoute(route.path) && !!NAV_LABELS[roleId.value])

  const activePath = computed(() => {
    if (roleId.value === 'admin') {
      return resolveAdminTabMeta(route.path, route.query).tabPath
    }
    return pathKey(route.path)
  })

  function openOrActivate(menuKey, targetPath, query = {}) {
    const rid = roleId.value
    const tabs = ensureRoleTabs(rid)
    let path = pathKey(targetPath || menuToPath(menuKey, rid), query)
    let key = menuKey
    let labelKey = menuKey

    if (rid === 'admin') {
      const meta = resolveAdminTabMeta(path.split('?')[0], query)
      path = meta.tabPath
      key = meta.menuKey
      labelKey = meta.labelKey
    }

    const existing = tabs.find((tab) => tab.path === path || tab.key === key)
    if (existing) {
      existing.path = path
      existing.key = key
      if (key && key !== HOME_KEY) existing.label = resolveLabel(labelKey, rid)
      return
    }
    tabs.push({
      key: key || path,
      label: resolveLabel(labelKey || pathToMenu(path.split('?')[0], rid), rid),
      path,
      closable: key !== HOME_KEY && path !== menuToPath(HOME_KEY, rid)
    })
  }

  function activateTab(tab) {
    if (!tab?.path) return
    if (activePath.value === tab.path && !route.query?.issueId && !route.query?.receiptId) return
    router.push(parseTabRoute(tab.path))
  }

  function closeTab(tab, event) {
    event?.stopPropagation?.()
    if (!tab?.closable) return
    const rid = roleId.value
    const tabs = ensureRoleTabs(rid)
    const index = tabs.findIndex((item) => item.path === tab.path)
    if (index < 0) return
    const wasActive = activePath.value === tab.path
    tabs.splice(index, 1)
    if (wasActive) {
      const next = tabs[Math.min(index, tabs.length - 1)] || tabs[0]
      if (next) router.push(parseTabRoute(next.path))
    }
  }

  function closeOthers(tab) {
    const rid = roleId.value
    const tabs = ensureRoleTabs(rid)
    tabsByRole.value[rid] = tabs.filter((item) => !item.closable || item.path === tab.path)
    if (activePath.value !== tab.path) router.push(parseTabRoute(tab.path))
  }

  watch(
    () => [route.path, route.query.demoRole, roleId.value],
    ([path, , rid]) => {
      if (isUnionRoute(path) || !NAV_LABELS[rid]) return
      if (rid === 'admin') {
        const meta = resolveAdminTabMeta(path, route.query)
        openOrActivate(meta.menuKey || pathKey(path), path, route.query)
        return
      }
      const menuKey = pathToMenu(pathKey(path), rid)
      openOrActivate(menuKey || pathKey(path), pathKey(path))
    },
    { immediate: true }
  )

  return {
    showTabs,
    openTabs,
    activePath,
    activateTab,
    closeTab,
    closeOthers,
    openOrActivate
  }
}
