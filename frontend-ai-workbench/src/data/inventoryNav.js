/**
 * 库存管理员侧栏导航（分组 + 图标），风格对齐质检员侧栏
 * key 与 MENU_ROUTES / roles.visible 对齐
 */
export const inventoryNavGroups = [
  {
    title: '库存作业',
    items: [
      { key: '库存控制', label: '库存控制', icon: 'task' },
      { key: '补货管理', label: '补货管理', icon: 'execute' },
      { key: '库存盘点', label: '库存盘点', icon: 'record' },
      { key: '盘点调整', label: '盘点调整', icon: 'standard' },
      { key: '库位地图', label: '库位地图', icon: 'scan' }
    ]
  },
  {
    title: '分析查询',
    items: [
      { key: '数据分析', label: '数据分析', icon: 'chart' }
    ]
  }
]

export const inventoryTopItems = [{ key: '工作台', label: '工作台', icon: 'home' }]

export const inventoryGuideLinks = [
  { key: 'stocktake-flow', label: '盘点调整流程说明', menu: '库存盘点' }
]
