/** 库存管理员 — 补货任务演示数据 */

export const REPLENISH_STATUS = {
  DEMAND_PENDING: { label: '待生成', cls: 'status-pending' },
  CREATED: { label: '待下发', cls: 'status-created' },
  ASSIGNED: { label: '待执行', cls: 'status-assigned' },
  IN_PROGRESS: { label: '执行中', cls: 'status-progress' },
  COMPLETED: { label: '已完成', cls: 'status-done' },
  CANCELLED: { label: '已取消', cls: 'status-cancelled' },
}

export const REPLENISH_STATUS_OPTIONS = Object.entries(REPLENISH_STATUS).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

let seq = 8

export function nextReplenishNo() {
  seq += 1
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `RP${y}${m}${day}${String(seq).padStart(3, '0')}`
}

export function createInitialReplenishTasks() {
  return [
    {
      id: 'RP20260713001',
      taskNo: 'RP20260713001',
      itemCode: 'M-SEAL-RING',
      itemName: '高温密封圈',
      batchNo: 'B20260705011',
      fromLocation: 'R-01-05',
      toLocation: 'A-03-02',
      suggestQty: 50,
      actualQty: null,
      status: 'CREATED',
      executor: '',
      createdAt: '2026-07-13 09:12:00',
      reason: '拣选位低于安全库存',
      remark: '',
      sourceAvailable: 320,
      targetCurrent: 18,
      safetyStock: 60,
    },
    {
      id: 'RP20260713002',
      taskNo: 'RP20260713002',
      itemCode: 'M-BEARING-6205',
      itemName: '轴承组件',
      batchNo: 'B20260628003',
      fromLocation: 'R-02-01',
      toLocation: 'P-01-08',
      suggestQty: 20,
      actualQty: 20,
      status: 'ASSIGNED',
      executor: '李仓',
      createdAt: '2026-07-13 08:40:00',
      reason: '出库前备货',
      remark: '优先补满拣选位',
      sourceAvailable: 180,
      targetCurrent: 5,
      safetyStock: 25,
    },
    {
      id: 'RP20260712003',
      taskNo: 'RP20260712003',
      itemCode: 'VALVE-BODY',
      itemName: '控制阀体',
      batchNo: 'B20260701007',
      fromLocation: 'R-03-02',
      toLocation: 'A-02-04',
      suggestQty: 12,
      actualQty: 12,
      status: 'IN_PROGRESS',
      executor: '王仓',
      createdAt: '2026-07-12 16:22:00',
      reason: '库位需求补货',
      remark: '',
      sourceAvailable: 90,
      targetCurrent: 3,
      safetyStock: 15,
    },
    {
      id: 'RP20260712004',
      taskNo: 'RP20260712004',
      itemCode: 'EXPROOF',
      itemName: '防爆接插件',
      batchNo: 'B20260515002',
      fromLocation: 'R-01-02',
      toLocation: 'P-02-01',
      suggestQty: 30,
      actualQty: 30,
      status: 'COMPLETED',
      executor: '李仓',
      createdAt: '2026-07-12 11:05:00',
      reason: '安全库存补货',
      remark: '已完成上架',
      sourceAvailable: 200,
      targetCurrent: 40,
      safetyStock: 40,
    },
    {
      id: 'RP20260711005',
      taskNo: 'RP20260711005',
      itemCode: 'CABLE-JOINT',
      itemName: '电缆接头',
      batchNo: 'B20260620019',
      fromLocation: 'R-04-01',
      toLocation: 'A-01-06',
      suggestQty: 8,
      actualQty: null,
      status: 'CANCELLED',
      executor: '',
      createdAt: '2026-07-11 14:30:00',
      reason: '需求取消',
      remark: '计划变更取消',
      sourceAvailable: 60,
      targetCurrent: 10,
      safetyStock: 12,
    },
    {
      id: 'RP20260713006',
      taskNo: 'RP20260713006',
      itemCode: 'M5008',
      itemName: '连接器',
      batchNo: 'B20260708001',
      fromLocation: 'R-02-08',
      toLocation: 'P-03-02',
      suggestQty: 15,
      actualQty: null,
      status: 'DEMAND_PENDING',
      executor: '',
      createdAt: '2026-07-13 10:18:00',
      reason: '系统按下限自动识别',
      remark: '',
      sourceAvailable: 110,
      targetCurrent: 2,
      safetyStock: 20,
    },
  ]
}
