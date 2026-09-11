import { roles } from '../data/roles'

export const MENU_ROUTES = {
  工作台: '/workbench',
  智能体工会: '/agent-union',
  订单计划: '/plan-center',
  出库协同: '/outbound',
  出库管理: '/warehouse-outbound',
  出库单: '/warehouse-outbound',
  收货上架: '/warehouse-receiving',
  拣货任务: '/warehouse-outbound',
  PDA扫码拣货: '/warehouse-pick-scan',
  异常记录: '/warehouse-exceptions',
  库存控制: '/inventory-control',
  补货管理: '/inventory-replenishment',
  库存盘点: '/inventory-check',
  盘点调整: '/inventory-adjustment',
  库位地图: '/inventory-locations',
  安全库存: '/inventory-safety',
  差异分析: '/inventory-variance',
  冻结查询: '/inventory-frozen',
  批次追溯: '/inventory-trace',
  数据分析: '/analytics',
  今日生产任务: '/worker-today',
  工序流程: '/worker-process',
  我的领料: '/my-picking',
  扫码确认: '/scan-confirm',
  补料申请: '/replenishment',
  完成记录: '/worker-completed',
  // 质检员模块（新增）
  待检任务: '/quality/tasks',
  检测执行: '/quality/execute',
  质量异常: '/quality/issues',
  检验标准: '/quality/standards',
  // 系统管理员模块
  用户管理: '/admin/users',
  角色管理: '/admin/roles',
  权限审计: '/admin/permission-audit',
  登录审计: '/admin/login-audit',
  接口监控: '/admin/api-monitor',
  Agent监控: '/admin/agent-monitor',
  消息中心: '/admin/messages',
  证据追溯: '/admin/evidence',
  系统配置: '/admin/config',
  日志中心: '/admin/logs',
  系统备份: '/admin/backup'
}

export function menuToPath(menu, roleId) {
  return MENU_ROUTES[menu] ?? `/module/${encodeURIComponent(menu)}`
}

/** 带 receiptId 进入检测执行页 */
export function qualityExecutePath(receiptId) {
  return { path: '/quality/execute', query: { receiptId: String(receiptId) } }
}

export function pathToMenu(path, roleId) {
  if (path === '/quality/execute') {
    return '检测执行'
  }
  const entry = Object.entries(MENU_ROUTES)
    .sort(([, a], [, b]) => b.length - a.length)
    .find(([, routePath]) => path === routePath || path.startsWith(`${routePath}/`))
  if (entry) return entry[0]
  if (path.startsWith('/module/')) {
    return decodeURIComponent(path.slice('/module/'.length))
  }
  return ''
}

export function isUnionRoute(path) {
  return path === '/agent-union' || path.startsWith('/agent-union/')
}

const ADMIN_NATIVE_PREFIXES = ['/admin/', '/agent-union']
const DEMO_ROLE_IDS = ['pmc', 'warehouse', 'inventory', 'quality', 'worker']

export function isAdminNativePath(path, demoRoleQuery = '') {
  const clean = (path || '').split('?')[0]
  if (isUnionRoute(clean)) return true
  if (clean === '/workbench') {
    const hinted = String(demoRoleQuery || '').trim()
    return !hinted || !DEMO_ROLE_IDS.includes(hinted)
  }
  return ADMIN_NATIVE_PREFIXES.some((prefix) => clean === prefix || clean.startsWith(prefix))
}

function roleOwnsMenu(roleId, menuKey) {
  const role = roles.find((item) => item.id === roleId)
  return Boolean(menuKey && role?.visible?.includes(menuKey))
}

/** 管理员预览业务页时，解析当前演示角色与菜单项 */
export function resolveAdminDemoContext(path, demoRoleQuery = '') {
  const cleanPath = (path || '').split('?')[0]
  const hintedRole = String(demoRoleQuery || '').trim()
  const menuKey = pathToMenu(cleanPath, hintedRole || 'worker')

  if (hintedRole && DEMO_ROLE_IDS.includes(hintedRole)) {
    if (menuKey && roleOwnsMenu(hintedRole, menuKey)) {
      return { roleId: hintedRole, menuKey }
    }
    if (menuKey) return { roleId: hintedRole, menuKey }
  }

  if (isAdminNativePath(cleanPath, demoRoleQuery)) return null

  if (!menuKey) return null

  for (const roleId of DEMO_ROLE_IDS) {
    if (roleOwnsMenu(roleId, menuKey)) {
      return { roleId, menuKey }
    }
  }
  return null
}

export function demoNavigateTarget(menu, roleId) {
  const path = menuToPath(menu, roleId)
  return { path, query: { demoRole: roleId } }
}

/** 管理员演示模式下，跳转时保留 demoRole 参数 */
export function demoPreserveRoute(path, roleId, isAdminDemo = false, extraQuery = {}) {
  const [clean, search = ''] = String(path || '').split('?')
  const query = { ...extraQuery }
  if (search) {
    for (const part of search.split('&')) {
      const [key, value = ''] = part.split('=')
      if (key) query[key] = decodeURIComponent(value)
    }
  }
  if (isAdminDemo && roleId && roleId !== 'admin' && DEMO_ROLE_IDS.includes(roleId)) {
    query.demoRole = roleId
  }
  return Object.keys(query).length ? { path: clean, query } : { path: clean }
}
