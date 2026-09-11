/**
 * 系统管理员侧栏导航（分组 + 图标），风格对齐质检员侧栏
 * key 与 MENU_ROUTES / roles.visible 对齐；label 为侧栏展示名
 */
export const adminNavGroups = [
  {
    title: '用户权限',
    items: [
      { key: '用户管理', label: '用户管理', icon: 'user' },
      { key: '角色管理', label: '角色管理', icon: 'role' },
      { key: '权限审计', label: '权限审计', icon: 'audit' },
      { key: '登录审计', label: '登录审计', icon: 'login' }
    ]
  },
  {
    title: '系统集成',
    items: [
      { key: '接口监控', label: '接口监控', icon: 'gauge' },
      { key: 'Agent监控', label: 'Agent监控', icon: 'agent' },
      { key: '消息中心', label: '消息中心', icon: 'mail' }
    ]
  },
  {
    title: '数据治理',
    items: [
      { key: '证据追溯', label: '证据追溯', icon: 'trace' },
      { key: '系统配置', label: '系统配置', icon: 'gear' },
      { key: '日志中心', label: '日志中心', icon: 'log' },
      { key: '系统备份', label: '系统备份', icon: 'backup' }
    ]
  }
]

export const adminTopItems = [
  { key: '工作台', label: '工作台', icon: 'home' },
  { key: '智能体工会', label: '智能体工会', icon: 'guild' }
]

export const adminGuideLinks = [
  { key: 'audit-flow', label: '权限审计流程说明', menu: '权限审计' }
]
