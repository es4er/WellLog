export const accounts = {
  admin: { password: 'admin123', roleId: 'admin' },
  pmc: { password: 'pmc123', roleId: 'pmc' },
  warehouse: { password: 'wh123', roleId: 'warehouse' },
  quality: { password: 'qa123', roleId: 'quality' },
  inventory: { password: 'inv123', roleId: 'inventory' },
  worker: { password: 'worker123', roleId: 'worker', userId: 1 }
}

export const demoAccounts = [
  ['admin', 'admin123', '系统管理员'],
  ['pmc', 'pmc123', 'PMC计划员'],
  ['warehouse', 'wh123', '仓管员'],
  ['quality', 'qa123', '质检员'],
  ['inventory', 'inv123', '库存管理员'],
  ['worker', 'worker123', '生产工人']
]
