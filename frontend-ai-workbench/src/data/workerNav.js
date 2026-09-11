/**
 * 生产工人侧栏导航（分组 + 图标），风格对齐质检员侧栏
 * key 与 MENU_ROUTES / roles.visible 对齐
 */
export const workerNavGroups = [
  {
    title: '生产执行',
    items: [
      { key: '今日生产任务', label: '今日生产任务', icon: 'task' },
      { key: '工序流程', label: '工序流程', icon: 'flow' }
    ]
  },
  {
    title: '记录',
    items: [{ key: '完成记录', label: '完成记录', icon: 'chart' }]
  }
]

export const workerTopItems = [{ key: '工作台', label: '工作台', icon: 'home' }]

export const workerGuideLinks = [
  { key: 'picking-flow', label: '备料区领料说明', menu: '今日生产任务' }
]
