/**
 * 系统管理员 — 全角色演示中心侧栏
 * 按业务角色分组，聚合各角色 nav 配置（含各角色「工作台」）
 */
import { roles } from './roles'
import { pmcNavGroups, pmcTopItems } from './pmcNav'
import { warehouseNavGroups, warehouseTopItems } from './warehouseNav'
import { inventoryNavGroups, inventoryTopItems } from './inventoryNav'
import { qualityNavGroups, qualityTopItems } from './qualityNav'
import { workerNavGroups, workerTopItems } from './workerNav'

const DEMO_ROLE_ORDER = ['pmc', 'inventory', 'warehouse', 'quality', 'worker']

const ROLE_NAV = {
  pmc: { topItems: pmcTopItems, groups: pmcNavGroups },
  warehouse: { topItems: warehouseTopItems, groups: warehouseNavGroups },
  inventory: { topItems: inventoryTopItems, groups: inventoryNavGroups },
  quality: { topItems: qualityTopItems, groups: qualityNavGroups },
  worker: { topItems: workerTopItems, groups: workerNavGroups }
}

function flattenNavItems(topItems = [], groups = []) {
  const items = []
  const seen = new Set()
  for (const item of topItems) {
    if (!seen.has(item.key)) {
      items.push(item)
      seen.add(item.key)
    }
  }
  for (const group of groups) {
    for (const item of group.items || []) {
      if (!seen.has(item.key)) {
        items.push(item)
        seen.add(item.key)
      }
    }
  }
  return items
}

function roleLabel(roleId) {
  return roles.find((role) => role.id === roleId)?.label || roleId
}

export const adminDemoSectionTitle = '全角色演示中心'

export const adminDemoRoleGroups = DEMO_ROLE_ORDER.map((roleId) => {
  const nav = ROLE_NAV[roleId]
  return {
    roleId,
    title: roleLabel(roleId),
    items: flattenNavItems(nav.topItems, nav.groups)
  }
}).filter((group) => group.items.length > 0)

/** 扁平化演示菜单标签，供管理员页签使用 */
export function flattenAdminDemoLabels() {
  const map = new Map()
  for (const group of adminDemoRoleGroups) {
    for (const item of group.items) {
      map.set(`${group.roleId}:${item.key}`, `${group.title}：${item.label}`)
    }
  }
  return map
}
