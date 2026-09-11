/**
 * 质检员侧栏导航（分组 + 图标），配色沿用项目绿色体系
 * key 与 MENU_ROUTES / roles.visible 对齐；label 为侧栏展示名
 */
export const qualityNavGroups = [
  {
    title: '质检管理',
    items: [
      { key: '待检任务', label: '检验任务', icon: 'task' },
      { key: '检测执行', label: '检验执行', icon: 'execute' },
      { key: '质量异常', label: '异常处理', icon: 'issue' }
    ]
  },
  {
    title: '基础设置',
    items: [
      { key: '检验标准', label: '检验标准', icon: 'standard' }
    ]
  }
]

export const qualityTopItems = [{ key: '工作台', label: '工作台', icon: 'home' }]

export const qualityGuideLinks = [
  { key: 'issue-flow', label: '异常处理流程说明', menu: '质量异常' }
]
