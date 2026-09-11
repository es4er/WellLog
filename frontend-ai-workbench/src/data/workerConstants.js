export const EXCEPTION_TYPES = [
  '找不到物料',
  '数量不足',
  '包装破损',
  '标签损坏',
  '扫码失败',
  '其他'
]

export const REPLENISH_REASONS = [
  '装配过程发现数量不足',
  '物料损坏需补发',
  '工艺变更追加用量',
  '其他'
]

/** 演示工人赵工账号（与后端 sys_user.user_code=worker 对齐） */
export const DEMO_WORKER_USER_ID = 20

/** 原型基准日期：2026-07-09 */
export const TODAY = '2026-07-09'
export const YESTERDAY = '2026-07-08'

export const SCOPE_OPTIONS = [
  { id: 'default', label: '待处理', hint: '今日 + 逾期未完成' },
  { id: 'today', label: '今日任务', hint: '计划领料日期 = 今天' },
  { id: 'overdue', label: '逾期未完成', hint: '昨天及更早未交接' },
  { id: 'all-pending', label: '全部待处理', hint: '含缺件、补拣待确认' },
  { id: 'history', label: '历史记录', hint: '已交接 / 已关闭' }
]

export const CATEGORY_OPTIONS = [
  { id: 'overview', label: '今日领料任务', countKey: 'overview' },
  { id: 'pending-scan', label: '待扫码', countKey: 'pendingScan' },
  { id: 'handed-over', label: '已交接', countKey: 'handedOver' },
  { id: 'exceptions', label: '缺件反馈', countKey: 'exceptions' }
]

export const initialPickingTasks = [
  {
    id: 'pick-001',
    workOrder: 'WO20260708009',
    requisition: 'REQ20260708018',
    product: '测井探头组件',
    priority: '高',
    planQty: 20,
    unit: '套',
    planDate: TODAY,
    expectedTime: '09:30',
    handler: '赵工',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-1', material: '高温密封圈', materialCode: 'SEAL-HIGH', spec: 'Φ20×2', required: 40, scanned: 40, batch: 'B20260705011', location: 'A-03-02', available: 120, status: '已确认' },
      { id: 'item-2', material: '防爆接插件', materialCode: 'EXPROOF', spec: 'EX-4P', required: 40, scanned: 36, batch: 'B20260706004', location: 'A-03-08', available: 36, status: '缺 4', shortage: 4 },
      { id: 'item-3', material: '测井探头外壳', materialCode: 'SHELL', spec: 'TP-100', required: 20, scanned: 20, batch: 'B20260707002', location: 'B-01-03', available: 45, status: '已确认' }
    ]
  },
  {
    id: 'pick-002',
    workOrder: 'WO20260708012',
    requisition: 'REQ20260708021',
    product: '井下通信模块',
    priority: '中',
    planQty: 15,
    unit: '套',
    planDate: TODAY,
    expectedTime: '10:00',
    handler: '赵工',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-4', material: '通信主板', materialCode: 'COMM-BOARD', spec: 'CM-V2', required: 15, scanned: 0, batch: 'B20260706008', location: 'C-02-01', available: 28, status: '待扫码' },
      { id: 'item-5', material: '防水接头', materialCode: 'WATER-JOINT', spec: 'WJ-M12', required: 30, scanned: 0, batch: 'B20260706009', location: 'C-02-05', available: 60, status: '待扫码' }
    ]
  },
  {
    id: 'pick-006',
    workOrder: 'WO20260708007',
    requisition: 'REQ20260708012',
    product: '定向钻具组件',
    priority: '低',
    planQty: 8,
    unit: '套',
    planDate: TODAY,
    expectedTime: '08:15',
    handler: '赵工',
    handoverTime: '10:25',
    cancelled: false,
    items: [
      { id: 'item-10', material: '钻具接头', materialCode: 'DRILL-JOINT', spec: 'DJ-38', required: 8, scanned: 8, batch: 'B20260707001', location: 'D-01-02', available: 16, status: '已确认' },
      { id: 'item-11', material: '耐磨套筒', materialCode: 'WEAR-SLEEVE', spec: 'WS-38', required: 8, scanned: 8, batch: 'B20260707003', location: 'D-01-04', available: 20, status: '已确认' }
    ]
  },
  {
    id: 'pick-003',
    workOrder: 'WO20260708005',
    requisition: 'REQ20260708014',
    product: '压力传感器总成',
    priority: '中',
    planQty: 10,
    unit: '套',
    planDate: TODAY,
    expectedTime: '08:30',
    handler: '赵工',
    handoverTime: '09:05',
    cancelled: false,
    items: [
      { id: 'item-6', material: '压力传感芯体', materialCode: 'PRESS-CORE', spec: 'PC-200', required: 10, scanned: 10, batch: 'B20260705020', location: 'E-02-01', available: 30, status: '已确认' },
      { id: 'item-7', material: '不锈钢外壳', materialCode: 'SS-SHELL', spec: 'SS-Ø80', required: 10, scanned: 10, batch: 'B20260705021', location: 'E-02-03', available: 22, status: '已确认' }
    ]
  },
  {
    id: 'pick-004',
    workOrder: 'WO20260708003',
    requisition: 'REQ20260708011',
    product: '数据采集卡',
    priority: '低',
    planQty: 8,
    unit: '套',
    planDate: TODAY,
    expectedTime: '08:00',
    handler: '李仓',
    handoverTime: '08:42',
    cancelled: false,
    items: [
      { id: 'item-8', material: 'ADC 采集板', materialCode: 'ADC-BOARD', spec: 'ADC-16', required: 8, scanned: 8, batch: 'B20260704015', location: 'F-01-01', available: 12, status: '已确认' }
    ]
  },
  {
    id: 'pick-overdue',
    workOrder: 'WO20260708006',
    requisition: 'REQ20260708015',
    product: '液压控制阀组',
    priority: '高',
    planQty: 12,
    unit: '套',
    planDate: YESTERDAY,
    expectedTime: '16:30',
    handler: '李仓',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-12', material: '控制阀体', materialCode: 'VALVE-BODY', spec: 'VB-25', required: 12, scanned: 10, batch: 'B20260705030', location: 'G-03-02', available: 10, status: '缺 2', shortage: 2 },
      { id: 'item-13', material: '密封组件', materialCode: 'SEAL-KIT', spec: 'SK-25', required: 24, scanned: 24, batch: 'B20260705031', location: 'G-03-04', available: 48, status: '已确认' }
    ]
  },
  {
    id: 'pick-005',
    workOrder: 'WO20260708001',
    requisition: 'REQ20260708008',
    product: '电缆接头组件',
    priority: '中',
    planQty: 25,
    unit: '套',
    planDate: YESTERDAY,
    expectedTime: '07:45',
    handler: '李仓',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-9', material: '电缆接头', materialCode: 'CABLE-JOINT', spec: 'CJ-3P', required: 50, scanned: 48, batch: 'B20260704010', location: 'H-01-06', available: 48, status: '缺 2', shortage: 2 }
    ]
  },
  {
    id: 'pick-010',
    workOrder: 'WO20260708010',
    requisition: 'REQ20260708023',
    product: '高温密封组件',
    priority: '高',
    planQty: 6,
    unit: '套',
    planDate: TODAY,
    expectedTime: '10:30',
    handler: '赵工',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-15', material: '陶瓷密封环', materialCode: 'CERAMIC-SEAL', spec: 'Φ32×3', required: 6, scanned: 0, batch: 'B20260707010', location: 'A-04-01', available: 18, status: '待扫码' },
      { id: 'item-16', material: '石墨垫片', materialCode: 'GRAPHITE', spec: 'G-32', required: 6, scanned: 0, batch: 'B20260707011', location: 'A-04-02', available: 24, status: '待扫码' },
      { id: 'item-17', material: '紧固螺栓', materialCode: 'BOLT-M8', spec: 'M8×25', required: 24, scanned: 0, batch: 'B20260707012', location: 'A-04-03', available: 200, status: '待扫码' },
      { id: 'item-18', material: 'O 型密封圈', materialCode: 'O-RING', spec: 'Φ20×2', required: 12, scanned: 0, batch: 'B20260707013', location: 'A-04-04', available: 80, status: '待扫码' },
      { id: 'item-19', material: '金属垫圈', materialCode: 'WASHER', spec: 'M8', required: 12, scanned: 0, batch: 'B20260707014', location: 'A-04-05', available: 160, status: '待扫码' },
      { id: 'item-20', material: '高温润滑脂', materialCode: 'GREASE', spec: 'HT-200', required: 6, scanned: 0, batch: 'B20260707015', location: 'A-04-06', available: 30, status: '待扫码' },
      { id: 'item-20b', material: '连接器', materialCode: 'M5008', spec: 'CJ-3P', required: 6, scanned: 0, batch: 'B20260707016', location: 'A-04-07', available: 14, status: '待扫码' },
      { id: 'item-20c', material: '密封胶', materialCode: 'SEALANT', spec: 'SG-50', required: 6, scanned: 0, batch: 'B20260707017', location: 'A-04-08', available: 40, status: '待扫码' }
    ]
  },
  {
    id: 'pick-011',
    workOrder: 'WO20260708011',
    requisition: 'REQ20260708022',
    product: '传感器壳体',
    priority: '中',
    planQty: 10,
    unit: '套',
    planDate: TODAY,
    expectedTime: '11:00',
    handler: '赵工',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-21', material: '壳体本体', materialCode: 'SHELL-BODY', spec: 'SB-Ø90', required: 10, scanned: 10, batch: 'B20260707020', location: 'B-02-01', available: 20, status: '已确认' },
      { id: 'item-22', material: '透明视窗', materialCode: 'VIEW-WIN', spec: 'VW-Ø40', required: 10, scanned: 10, batch: 'B20260707021', location: 'B-02-02', available: 15, status: '已确认' }
    ]
  },
  {
    id: 'pick-012',
    workOrder: 'WO20260708004',
    requisition: 'REQ20260708010',
    product: '信号调理模块',
    priority: '低',
    planQty: 5,
    unit: '套',
    planDate: TODAY,
    expectedTime: '07:30',
    handler: '李仓',
    handoverTime: '11:10',
    cancelled: false,
    items: [
      { id: 'item-23', material: '调理板', materialCode: 'COND-BOARD', spec: 'CB-V1', required: 5, scanned: 5, batch: 'B20260704020', location: 'C-03-01', available: 10, status: '已确认' }
    ]
  },
  {
    id: 'pick-cancelled',
    workOrder: 'WO20260707028',
    requisition: 'REQ20260707035',
    product: '临时试制件',
    priority: '低',
    planQty: 3,
    unit: '套',
    planDate: YESTERDAY,
    expectedTime: '14:00',
    handler: '赵工',
    handoverTime: '',
    cancelled: true,
    items: [
      { id: 'item-14', material: '试制支架', materialCode: 'TRIAL-BRACKET', spec: 'TB-01', required: 3, scanned: 0, batch: 'B20260703001', location: 'Z-99-01', available: 3, status: '待扫码' }
    ]
  },
  {
    id: 'pick-013',
    workOrder: 'WO20260709001',
    requisition: 'REQ20260709001',
    product: '泥浆脉冲发生器',
    priority: '高',
    planQty: 4,
    unit: '套',
    planDate: TODAY,
    expectedTime: '11:30',
    handler: '赵工',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-24', material: '脉冲阀芯', materialCode: 'PULSE-CORE', spec: 'PC-M12', required: 4, scanned: 0, batch: 'B20260708001', location: 'B-03-01', available: 12, status: '待扫码' },
      { id: 'item-25', material: '驱动线圈', materialCode: 'DRIVE-COIL', spec: 'DC-24V', required: 4, scanned: 0, batch: 'B20260708002', location: 'B-03-02', available: 10, status: '待扫码' },
      { id: 'item-26', material: '密封端盖', materialCode: 'END-CAP', spec: 'EC-Ø60', required: 8, scanned: 0, batch: 'B20260708003', location: 'B-03-03', available: 20, status: '待扫码' }
    ]
  },
  {
    id: 'pick-014',
    workOrder: 'WO20260709002',
    requisition: 'REQ20260709002',
    product: '井下电源模块',
    priority: '中',
    planQty: 12,
    unit: '套',
    planDate: TODAY,
    expectedTime: '13:00',
    handler: '赵工',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-27', material: '锂电池组', materialCode: 'BAT-PACK', spec: 'BP-36V', required: 12, scanned: 0, batch: 'B20260708010', location: 'C-04-01', available: 30, status: '待扫码' },
      { id: 'item-28', material: '电源管理板', materialCode: 'PM-BOARD', spec: 'PM-V3', required: 12, scanned: 0, batch: 'B20260708011', location: 'C-04-02', available: 18, status: '待扫码' },
      { id: 'item-29', material: '绝缘护套', materialCode: 'INSUL-SLEEVE', spec: 'IS-10', required: 24, scanned: 0, batch: 'B20260708012', location: 'C-04-03', available: 60, status: '待扫码' },
      { id: 'item-30', material: '接线端子', materialCode: 'TERM-BLOCK', spec: 'TB-4P', required: 24, scanned: 0, batch: 'B20260708013', location: 'C-04-04', available: 80, status: '待扫码' }
    ]
  },
  {
    id: 'pick-015',
    workOrder: 'WO20260709003',
    requisition: 'REQ20260709003',
    product: '旋转导向短节',
    priority: '高',
    planQty: 2,
    unit: '套',
    planDate: TODAY,
    expectedTime: '14:00',
    handler: '赵工',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-31', material: '导向轴', materialCode: 'GUIDE-SHAFT', spec: 'GS-Ø45', required: 2, scanned: 0, batch: 'B20260708020', location: 'D-02-01', available: 6, status: '待扫码' },
      { id: 'item-32', material: '轴承组件', materialCode: 'BEARING-KIT', spec: 'BK-6205', required: 4, scanned: 0, batch: 'B20260708021', location: 'D-02-02', available: 16, status: '待扫码' },
      { id: 'item-33', material: '液压油缸', materialCode: 'HYD-CYL', spec: 'HC-25', required: 2, scanned: 0, batch: 'B20260708022', location: 'D-02-03', available: 5, status: '待扫码' }
    ]
  },
  {
    id: 'pick-016',
    workOrder: 'WO20260709004',
    requisition: 'REQ20260709004',
    product: '伽马探管',
    priority: '中',
    planQty: 6,
    unit: '套',
    planDate: TODAY,
    expectedTime: '15:00',
    handler: '赵工',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-34', material: '闪烁晶体', materialCode: 'SCINT-CRYSTAL', spec: 'SC-NaI', required: 6, scanned: 0, batch: 'B20260708030', location: 'E-01-01', available: 10, status: '待扫码' },
      { id: 'item-35', material: '光电倍增管', materialCode: 'PMT-TUBE', spec: 'PMT-R928', required: 6, scanned: 0, batch: 'B20260708031', location: 'E-01-02', available: 8, status: '待扫码' }
    ]
  },
  {
    id: 'pick-017',
    workOrder: 'WO20260709005',
    requisition: 'REQ20260709005',
    product: '随钻测斜仪',
    priority: '低',
    planQty: 8,
    unit: '套',
    planDate: TODAY,
    expectedTime: '15:30',
    handler: '赵工',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-36', material: '加速度计', materialCode: 'ACCEL', spec: 'ACC-3A', required: 8, scanned: 0, batch: 'B20260708040', location: 'F-02-01', available: 20, status: '待扫码' },
      { id: 'item-37', material: '磁力计', materialCode: 'MAGNETO', spec: 'MAG-3M', required: 8, scanned: 0, batch: 'B20260708041', location: 'F-02-02', available: 15, status: '待扫码' },
      { id: 'item-38', material: '信号处理板', materialCode: 'SIG-BOARD', spec: 'SB-V2', required: 8, scanned: 0, batch: 'B20260708042', location: 'F-02-03', available: 12, status: '待扫码' }
    ]
  },
  {
    id: 'pick-018',
    workOrder: 'WO20260708015',
    requisition: 'REQ20260708028',
    product: '井口防喷器密封件',
    priority: '高',
    planQty: 10,
    unit: '套',
    planDate: TODAY,
    expectedTime: '09:00',
    handler: '赵工',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-39', material: '橡胶闸板', materialCode: 'RUBBER-RAM', spec: 'RR-7-1/16', required: 10, scanned: 6, batch: 'B20260707040', location: 'G-01-01', available: 14, status: '扫码中' },
      { id: 'item-40', material: '金属骨架', materialCode: 'METAL-FRAME', spec: 'MF-7', required: 10, scanned: 10, batch: 'B20260707041', location: 'G-01-02', available: 18, status: '已确认' },
      { id: 'item-41', material: '紧固螺钉', materialCode: 'SCREW-M10', spec: 'M10×40', required: 40, scanned: 20, batch: 'B20260707042', location: 'G-01-03', available: 200, status: '扫码中' }
    ]
  },
  {
    id: 'pick-019',
    workOrder: 'WO20260708016',
    requisition: 'REQ20260708029',
    product: '泥浆密度传感器',
    priority: '中',
    planQty: 15,
    unit: '套',
    planDate: TODAY,
    expectedTime: '10:45',
    handler: '赵工',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-42', material: '密度探头', materialCode: 'DENS-PROBE', spec: 'DP-200', required: 15, scanned: 15, batch: 'B20260707050', location: 'H-02-01', available: 22, status: '已确认' },
      { id: 'item-43', material: '温度补偿片', materialCode: 'TEMP-COMP', spec: 'TC-PT100', required: 15, scanned: 8, batch: 'B20260707051', location: 'H-02-02', available: 30, status: '扫码中' },
      { id: 'item-44', material: '信号线缆', materialCode: 'SIG-CABLE', spec: 'SC-5M', required: 15, scanned: 0, batch: 'B20260707052', location: 'H-02-03', available: 40, status: '待扫码' }
    ]
  },
  {
    id: 'pick-020',
    workOrder: 'WO20260708017',
    requisition: 'REQ20260708030',
    product: '井下马达定子',
    priority: '中',
    planQty: 3,
    unit: '套',
    planDate: TODAY,
    expectedTime: '12:00',
    handler: '赵工',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-45', material: '定子橡胶衬套', materialCode: 'STATOR-LINER', spec: 'SL-6-3/4', required: 3, scanned: 2, batch: 'B20260707060', location: 'D-03-01', available: 5, status: '扫码中' },
      { id: 'item-46', material: '钢体外壳', materialCode: 'STEEL-HOUSING', spec: 'SH-6-3/4', required: 3, scanned: 3, batch: 'B20260707061', location: 'D-03-02', available: 6, status: '已确认' }
    ]
  },
  {
    id: 'pick-021',
    workOrder: 'WO20260708018',
    requisition: 'REQ20260708031',
    product: '振动筛筛网组件',
    priority: '低',
    planQty: 20,
    unit: '套',
    planDate: TODAY,
    expectedTime: '13:30',
    handler: '赵工',
    handoverTime: '',
    cancelled: false,
    items: [
      { id: 'item-47', material: '主筛网', materialCode: 'MAIN-SCREEN', spec: 'MS-200', required: 20, scanned: 12, batch: 'B20260707070', location: 'I-01-01', available: 35, status: '扫码中' },
      { id: 'item-48', material: '边框压条', materialCode: 'FRAME-BAR', spec: 'FB-AL', required: 40, scanned: 40, batch: 'B20260707071', location: 'I-01-02', available: 80, status: '已确认' },
      { id: 'item-49', material: '固定卡扣', materialCode: 'CLIP-FIX', spec: 'CF-12', required: 80, scanned: 40, batch: 'B20260707072', location: 'I-01-03', available: 200, status: '扫码中' }
    ]
  },
  {
    id: 'pick-022',
    workOrder: 'WO20260707025',
    requisition: 'REQ20260707030',
    product: '液位变送器',
    priority: '中',
    planQty: 10,
    unit: '套',
    planDate: YESTERDAY,
    expectedTime: '15:00',
    handler: '李仓',
    handoverTime: '16:20',
    cancelled: false,
    items: [
      { id: 'item-50', material: '变送器本体', materialCode: 'LT-BODY', spec: 'LT-4-20', required: 10, scanned: 10, batch: 'B20260704030', location: 'E-03-01', available: 15, status: '已确认' },
      { id: 'item-51', material: '法兰接头', materialCode: 'FLANGE', spec: 'FL-DN50', required: 10, scanned: 10, batch: 'B20260704031', location: 'E-03-02', available: 20, status: '已确认' }
    ]
  },
  {
    id: 'pick-023',
    workOrder: 'WO20260707026',
    requisition: 'REQ20260707031',
    product: '电磁流量计',
    priority: '低',
    planQty: 5,
    unit: '套',
    planDate: YESTERDAY,
    expectedTime: '11:00',
    handler: '李仓',
    handoverTime: '12:15',
    cancelled: false,
    items: [
      { id: 'item-52', material: '流量计表体', materialCode: 'FLOW-BODY', spec: 'FB-DN80', required: 5, scanned: 5, batch: 'B20260704040', location: 'F-03-01', available: 8, status: '已确认' },
      { id: 'item-53', material: '电极组件', materialCode: 'ELECTRODE', spec: 'EL-SS', required: 10, scanned: 10, batch: 'B20260704041', location: 'F-03-02', available: 20, status: '已确认' },
      { id: 'item-54', material: '接地环', materialCode: 'GROUND-RING', spec: 'GR-DN80', required: 5, scanned: 5, batch: 'B20260704042', location: 'F-03-03', available: 12, status: '已确认' }
    ]
  },
  {
    id: 'pick-024',
    workOrder: 'WO20260707027',
    requisition: 'REQ20260707032',
    product: '压力变送器总成',
    priority: '高',
    planQty: 8,
    unit: '套',
    planDate: YESTERDAY,
    expectedTime: '09:30',
    handler: '赵工',
    handoverTime: '10:40',
    cancelled: false,
    items: [
      { id: 'item-55', material: '压力芯体', materialCode: 'PRESS-CORE-2', spec: 'PC-350', required: 8, scanned: 8, batch: 'B20260704050', location: 'E-02-05', available: 16, status: '已确认' },
      { id: 'item-56', material: '显示表头', materialCode: 'DISP-HEAD', spec: 'DH-LCD', required: 8, scanned: 8, batch: 'B20260704051', location: 'E-02-06', available: 12, status: '已确认' }
    ]
  }
]

export const initialScanRecords = [
  { id: 'scan-1', time: '09:31:20', barcode: 'BC0001', material: '防爆接插件', batch: 'B20260706004', result: '成功', success: true, workOrder: 'WO20260708009' },
  { id: 'scan-2', time: '09:31:45', barcode: 'BC0002', material: '防爆接插件', batch: 'B20260706004', result: '成功', success: true, workOrder: 'WO20260708009' },
  { id: 'scan-3', time: '09:32:10', barcode: 'BC9999', material: '高温密封圈', batch: 'B20260705011', result: '非当前物料', success: false, workOrder: 'WO20260708009' },
  { id: 'scan-4', time: '10:18:05', barcode: 'BC0101', material: '钻具接头', batch: 'B20260707001', result: '成功', success: true, workOrder: 'WO20260708007' },
  { id: 'scan-5', time: '10:22:30', barcode: 'BC0102', material: '耐磨套筒', batch: 'B20260707003', result: '成功', success: true, workOrder: 'WO20260708007' }
]

export const initialWorkerExceptions = [
  {
    id: 'ex-001',
    workOrder: 'WO20260708009',
    requisition: 'REQ20260708018',
    material: '防爆接插件',
    required: 40,
    actual: 36,
    shortage: 4,
    type: '数量不足',
    note: '现场只收到 36 件',
    status: '待仓管员补拣',
    submittedAt: '09:42',
    handler: '赵工',
    taskId: 'pick-001',
    itemId: 'item-2'
  },
  {
    id: 'ex-002',
    workOrder: 'WO20260708006',
    requisition: 'REQ20260708015',
    material: '控制阀体',
    required: 12,
    actual: 10,
    shortage: 2,
    type: '数量不足',
    note: '昨日领料缺 2 件，等待补拣',
    status: '待仓管员补拣',
    submittedAt: '昨天 17:05',
    handler: '李仓',
    taskId: 'pick-overdue',
    itemId: 'item-12'
  },
  {
    id: 'ex-closed',
    workOrder: 'WO20260707020',
    requisition: 'REQ20260707022',
    material: '连接法兰',
    required: 6,
    actual: 6,
    shortage: 0,
    type: '找不到物料',
    note: '已补拣完成',
    status: '已关闭',
    submittedAt: '07-07 15:30',
    handler: '赵工',
    taskId: '',
    itemId: ''
  }
]
