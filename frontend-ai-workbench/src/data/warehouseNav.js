/**
 * 仓管员侧栏导航（分组 + 图标），风格对齐质检员侧栏
 * key 与 MENU_ROUTES / roles.visible 对齐；label 为侧栏展示名
 */
export const warehouseNavGroups = [
  {
    title: '仓储作业',
    items: [
      { key: '收货上架', label: '收货上架', icon: 'task' },
      { key: '出库管理', label: '出库管理', icon: 'record' },
      { key: 'PDA扫码拣货', label: 'PDA扫码拣货', icon: 'scan' },
      { key: '异常记录', label: '异常记录', icon: 'issue' }
    ]
  }
]

export const warehouseTopItems = [{ key: '工作台', label: '工作台', icon: 'home' }]

export const warehouseGuideLinks = [
  { key: 'exception-flow', label: '异常处理流程说明', menu: '异常记录' }
]
