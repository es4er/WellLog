/**
 * PMC 计划员侧栏导航（分组 + 图标），风格对齐质检员侧栏
 * key 与 MENU_ROUTES / roles.visible 对齐；label 为侧栏展示名
 */
export const pmcNavGroups = [
  {
    title: '计划管理',
    items: [
      { key: '订单计划', label: '订单计划', icon: 'task' },
      { key: '出库协同', label: '出库协同', icon: 'execute' }
    ]
  },
  {
    title: '数据查询',
    items: [{ key: '数据分析', label: '数据分析', icon: 'chart' }]
  }
]

export const pmcTopItems = [{ key: '工作台', label: '工作台', icon: 'home' }]

export const pmcGuideLinks = [
  { key: 'plan-flow', label: '齐套与领料流程说明', menu: '订单计划' }
]
