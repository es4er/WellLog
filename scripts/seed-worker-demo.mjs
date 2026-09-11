/**
 * 将前端生产工人 mock 数据注入 wms_db，并生成可重复执行的 SQL。
 * 用法: node scripts/seed-worker-demo.mjs
 */
import { writeFileSync } from 'fs'
import { spawnSync } from 'child_process'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const TODAY = '2026-07-09'
const YESTERDAY = '2026-07-08'

const tasks = [
  { key: 'pick-001', workOrder: 'WO20260708009', requisition: 'REQ20260708018', product: '测井探头组件', priority: '高', planQty: 20, unit: '套', planDate: TODAY, expectedTime: '09:30', handler: '赵工', handoverTime: '', cancelled: false, items: [
    { material: '高温密封圈', materialCode: 'SEAL-HIGH', spec: 'Φ20×2', required: 40, scanned: 40, batch: 'B20260705011', location: 'A-03-02', available: 120 },
    { material: '防爆接插件', materialCode: 'EXPROOF', spec: 'EX-4P', required: 40, scanned: 36, batch: 'B20260706004', location: 'A-03-08', available: 36 },
    { material: '测井探头外壳', materialCode: 'SHELL', spec: 'TP-100', required: 20, scanned: 20, batch: 'B20260707002', location: 'B-01-03', available: 45 }
  ]},
  { key: 'pick-002', workOrder: 'WO20260708012', requisition: 'REQ20260708021', product: '井下通信模块', priority: '中', planQty: 15, unit: '套', planDate: TODAY, expectedTime: '10:00', handler: '赵工', handoverTime: '', cancelled: false, items: [
    { material: '通信主板', materialCode: 'COMM-BOARD', spec: 'CM-V2', required: 15, scanned: 0, batch: 'B20260706008', location: 'C-02-01', available: 28 },
    { material: '防水接头', materialCode: 'WATER-JOINT', spec: 'WJ-M12', required: 30, scanned: 0, batch: 'B20260706009', location: 'C-02-05', available: 60 }
  ]},
  { key: 'pick-006', workOrder: 'WO20260708007', requisition: 'REQ20260708012', product: '定向钻具组件', priority: '低', planQty: 8, unit: '套', planDate: TODAY, expectedTime: '08:15', handler: '赵工', handoverTime: '10:25', cancelled: false, items: [
    { material: '钻具接头', materialCode: 'DRILL-JOINT', spec: 'DJ-38', required: 8, scanned: 8, batch: 'B20260707001', location: 'D-01-02', available: 16 },
    { material: '耐磨套筒', materialCode: 'WEAR-SLEEVE', spec: 'WS-38', required: 8, scanned: 8, batch: 'B20260707003', location: 'D-01-04', available: 20 }
  ]},
  { key: 'pick-003', workOrder: 'WO20260708005', requisition: 'REQ20260708014', product: '压力传感器总成', priority: '中', planQty: 10, unit: '套', planDate: TODAY, expectedTime: '08:30', handler: '赵工', handoverTime: '09:05', cancelled: false, items: [
    { material: '压力传感芯体', materialCode: 'PRESS-CORE', spec: 'PC-200', required: 10, scanned: 10, batch: 'B20260705020', location: 'E-02-01', available: 30 },
    { material: '不锈钢外壳', materialCode: 'SS-SHELL', spec: 'SS-Ø80', required: 10, scanned: 10, batch: 'B20260705021', location: 'E-02-03', available: 22 }
  ]},
  { key: 'pick-004', workOrder: 'WO20260708003', requisition: 'REQ20260708011', product: '数据采集卡', priority: '低', planQty: 8, unit: '套', planDate: TODAY, expectedTime: '08:00', handler: '李仓', handoverTime: '08:42', cancelled: false, items: [
    { material: 'ADC 采集板', materialCode: 'ADC-BOARD', spec: 'ADC-16', required: 8, scanned: 8, batch: 'B20260704015', location: 'F-01-01', available: 12 }
  ]},
  { key: 'pick-overdue', workOrder: 'WO20260708006', requisition: 'REQ20260708015', product: '液压控制阀组', priority: '高', planQty: 12, unit: '套', planDate: YESTERDAY, expectedTime: '16:30', handler: '李仓', handoverTime: '', cancelled: false, items: [
    { material: '控制阀体', materialCode: 'VALVE-BODY', spec: 'VB-25', required: 12, scanned: 10, batch: 'B20260705030', location: 'G-03-02', available: 10 },
    { material: '密封组件', materialCode: 'SEAL-KIT', spec: 'SK-25', required: 24, scanned: 24, batch: 'B20260705031', location: 'G-03-04', available: 48 }
  ]},
  { key: 'pick-005', workOrder: 'WO20260708001', requisition: 'REQ20260708008', product: '电缆接头组件', priority: '中', planQty: 25, unit: '套', planDate: YESTERDAY, expectedTime: '07:45', handler: '李仓', handoverTime: '', cancelled: false, items: [
    { material: '电缆接头', materialCode: 'CABLE-JOINT', spec: 'CJ-3P', required: 50, scanned: 48, batch: 'B20260704010', location: 'H-01-06', available: 48 }
  ]},
  { key: 'pick-010', workOrder: 'WO20260708010', requisition: 'REQ20260708023', product: '高温密封组件', priority: '高', planQty: 6, unit: '套', planDate: TODAY, expectedTime: '10:30', handler: '赵工', handoverTime: '', cancelled: false, items: [
    { material: '陶瓷密封环', materialCode: 'CERAMIC-SEAL', spec: 'Φ32×3', required: 6, scanned: 0, batch: 'B20260707010', location: 'A-04-01', available: 18 },
    { material: '石墨垫片', materialCode: 'GRAPHITE', spec: 'G-32', required: 6, scanned: 0, batch: 'B20260707011', location: 'A-04-02', available: 24 },
    { material: '紧固螺栓', materialCode: 'BOLT-M8', spec: 'M8×25', required: 24, scanned: 0, batch: 'B20260707012', location: 'A-04-03', available: 200 },
    { material: 'O 型密封圈', materialCode: 'O-RING', spec: 'Φ20×2', required: 12, scanned: 0, batch: 'B20260707013', location: 'A-04-04', available: 80 },
    { material: '金属垫圈', materialCode: 'WASHER', spec: 'M8', required: 12, scanned: 0, batch: 'B20260707014', location: 'A-04-05', available: 160 },
    { material: '高温润滑脂', materialCode: 'GREASE', spec: 'HT-200', required: 6, scanned: 0, batch: 'B20260707015', location: 'A-04-06', available: 30 },
    { material: '连接器', materialCode: 'M5008', spec: 'CJ-3P', required: 6, scanned: 0, batch: 'B20260707016', location: 'A-04-07', available: 14 },
    { material: '密封胶', materialCode: 'SEALANT', spec: 'SG-50', required: 6, scanned: 0, batch: 'B20260707017', location: 'A-04-08', available: 40 }
  ]},
  { key: 'pick-011', workOrder: 'WO20260708011', requisition: 'REQ20260708022', product: '传感器壳体', priority: '中', planQty: 10, unit: '套', planDate: TODAY, expectedTime: '11:00', handler: '赵工', handoverTime: '', cancelled: false, items: [
    { material: '壳体本体', materialCode: 'SHELL-BODY', spec: 'SB-Ø90', required: 10, scanned: 10, batch: 'B20260707020', location: 'B-02-01', available: 20 },
    { material: '透明视窗', materialCode: 'VIEW-WIN', spec: 'VW-Ø40', required: 10, scanned: 10, batch: 'B20260707021', location: 'B-02-02', available: 15 }
  ]},
  { key: 'pick-012', workOrder: 'WO20260708004', requisition: 'REQ20260708010', product: '信号调理模块', priority: '低', planQty: 5, unit: '套', planDate: TODAY, expectedTime: '07:30', handler: '李仓', handoverTime: '11:10', cancelled: false, items: [
    { material: '调理板', materialCode: 'COND-BOARD', spec: 'CB-V1', required: 5, scanned: 5, batch: 'B20260704020', location: 'C-03-01', available: 10 }
  ]},
  { key: 'pick-cancelled', workOrder: 'WO20260707028', requisition: 'REQ20260707035', product: '临时试制件', priority: '低', planQty: 3, unit: '套', planDate: YESTERDAY, expectedTime: '14:00', handler: '赵工', handoverTime: '', cancelled: true, items: [
    { material: '试制支架', materialCode: 'TRIAL-BRACKET', spec: 'TB-01', required: 3, scanned: 0, batch: 'B20260703001', location: 'Z-99-01', available: 3 }
  ]},
  { key: 'pick-013', workOrder: 'WO20260709001', requisition: 'REQ20260709001', product: '泥浆脉冲发生器', priority: '高', planQty: 4, unit: '套', planDate: TODAY, expectedTime: '11:30', handler: '赵工', handoverTime: '', cancelled: false, items: [
    { material: '脉冲阀芯', materialCode: 'PULSE-CORE', spec: 'PC-M12', required: 4, scanned: 0, batch: 'B20260708001', location: 'B-03-01', available: 12 },
    { material: '驱动线圈', materialCode: 'DRIVE-COIL', spec: 'DC-24V', required: 4, scanned: 0, batch: 'B20260708002', location: 'B-03-02', available: 10 },
    { material: '密封端盖', materialCode: 'END-CAP', spec: 'EC-Ø60', required: 8, scanned: 0, batch: 'B20260708003', location: 'B-03-03', available: 20 }
  ]},
  { key: 'pick-014', workOrder: 'WO20260709002', requisition: 'REQ20260709002', product: '井下电源模块', priority: '中', planQty: 12, unit: '套', planDate: TODAY, expectedTime: '13:00', handler: '赵工', handoverTime: '', cancelled: false, items: [
    { material: '锂电池组', materialCode: 'BAT-PACK', spec: 'BP-36V', required: 12, scanned: 0, batch: 'B20260708010', location: 'C-04-01', available: 30 },
    { material: '电源管理板', materialCode: 'PM-BOARD', spec: 'PM-V3', required: 12, scanned: 0, batch: 'B20260708011', location: 'C-04-02', available: 18 },
    { material: '绝缘护套', materialCode: 'INSUL-SLEEVE', spec: 'IS-10', required: 24, scanned: 0, batch: 'B20260708012', location: 'C-04-03', available: 60 },
    { material: '接线端子', materialCode: 'TERM-BLOCK', spec: 'TB-4P', required: 24, scanned: 0, batch: 'B20260708013', location: 'C-04-04', available: 80 }
  ]},
  { key: 'pick-015', workOrder: 'WO20260709003', requisition: 'REQ20260709003', product: '旋转导向短节', priority: '高', planQty: 2, unit: '套', planDate: TODAY, expectedTime: '14:00', handler: '赵工', handoverTime: '', cancelled: false, items: [
    { material: '导向轴', materialCode: 'GUIDE-SHAFT', spec: 'GS-Ø45', required: 2, scanned: 0, batch: 'B20260708020', location: 'D-02-01', available: 6 },
    { material: '轴承组件', materialCode: 'BEARING-KIT', spec: 'BK-6205', required: 4, scanned: 0, batch: 'B20260708021', location: 'D-02-02', available: 16 },
    { material: '液压油缸', materialCode: 'HYD-CYL', spec: 'HC-25', required: 2, scanned: 0, batch: 'B20260708022', location: 'D-02-03', available: 5 }
  ]},
  { key: 'pick-016', workOrder: 'WO20260709004', requisition: 'REQ20260709004', product: '伽马探管', priority: '中', planQty: 6, unit: '套', planDate: TODAY, expectedTime: '15:00', handler: '赵工', handoverTime: '', cancelled: false, items: [
    { material: '闪烁晶体', materialCode: 'SCINT-CRYSTAL', spec: 'SC-NaI', required: 6, scanned: 0, batch: 'B20260708030', location: 'E-01-01', available: 10 },
    { material: '光电倍增管', materialCode: 'PMT-TUBE', spec: 'PMT-R928', required: 6, scanned: 0, batch: 'B20260708031', location: 'E-01-02', available: 8 }
  ]},
  { key: 'pick-017', workOrder: 'WO20260709005', requisition: 'REQ20260709005', product: '随钻测斜仪', priority: '低', planQty: 8, unit: '套', planDate: TODAY, expectedTime: '15:30', handler: '赵工', handoverTime: '', cancelled: false, items: [
    { material: '加速度计', materialCode: 'ACCEL', spec: 'ACC-3A', required: 8, scanned: 0, batch: 'B20260708040', location: 'F-02-01', available: 20 },
    { material: '磁力计', materialCode: 'MAGNETO', spec: 'MAG-3M', required: 8, scanned: 0, batch: 'B20260708041', location: 'F-02-02', available: 15 },
    { material: '信号处理板', materialCode: 'SIG-BOARD', spec: 'SB-V2', required: 8, scanned: 0, batch: 'B20260708042', location: 'F-02-03', available: 12 }
  ]},
  { key: 'pick-018', workOrder: 'WO20260708015', requisition: 'REQ20260708028', product: '井口防喷器密封件', priority: '高', planQty: 10, unit: '套', planDate: TODAY, expectedTime: '09:00', handler: '赵工', handoverTime: '', cancelled: false, items: [
    { material: '橡胶闸板', materialCode: 'RUBBER-RAM', spec: 'RR-7-1/16', required: 10, scanned: 6, batch: 'B20260707040', location: 'G-01-01', available: 14 },
    { material: '金属骨架', materialCode: 'METAL-FRAME', spec: 'MF-7', required: 10, scanned: 10, batch: 'B20260707041', location: 'G-01-02', available: 18 },
    { material: '紧固螺钉', materialCode: 'SCREW-M10', spec: 'M10×40', required: 40, scanned: 20, batch: 'B20260707042', location: 'G-01-03', available: 200 }
  ]},
  { key: 'pick-019', workOrder: 'WO20260708016', requisition: 'REQ20260708029', product: '泥浆密度传感器', priority: '中', planQty: 15, unit: '套', planDate: TODAY, expectedTime: '10:45', handler: '赵工', handoverTime: '', cancelled: false, items: [
    { material: '密度探头', materialCode: 'DENS-PROBE', spec: 'DP-200', required: 15, scanned: 15, batch: 'B20260707050', location: 'H-02-01', available: 22 },
    { material: '温度补偿片', materialCode: 'TEMP-COMP', spec: 'TC-PT100', required: 15, scanned: 8, batch: 'B20260707051', location: 'H-02-02', available: 30 },
    { material: '信号线缆', materialCode: 'SIG-CABLE', spec: 'SC-5M', required: 15, scanned: 0, batch: 'B20260707052', location: 'H-02-03', available: 40 }
  ]},
  { key: 'pick-020', workOrder: 'WO20260708017', requisition: 'REQ20260708030', product: '井下马达定子', priority: '中', planQty: 3, unit: '套', planDate: TODAY, expectedTime: '12:00', handler: '赵工', handoverTime: '', cancelled: false, items: [
    { material: '定子橡胶衬套', materialCode: 'STATOR-LINER', spec: 'SL-6-3/4', required: 3, scanned: 2, batch: 'B20260707060', location: 'D-03-01', available: 5 },
    { material: '钢体外壳', materialCode: 'STEEL-HOUSING', spec: 'SH-6-3/4', required: 3, scanned: 3, batch: 'B20260707061', location: 'D-03-02', available: 6 }
  ]},
  { key: 'pick-021', workOrder: 'WO20260708018', requisition: 'REQ20260708031', product: '振动筛筛网组件', priority: '低', planQty: 20, unit: '套', planDate: TODAY, expectedTime: '13:30', handler: '赵工', handoverTime: '', cancelled: false, items: [
    { material: '主筛网', materialCode: 'MAIN-SCREEN', spec: 'MS-200', required: 20, scanned: 12, batch: 'B20260707070', location: 'I-01-01', available: 35 },
    { material: '边框压条', materialCode: 'FRAME-BAR', spec: 'FB-AL', required: 40, scanned: 40, batch: 'B20260707071', location: 'I-01-02', available: 80 },
    { material: '固定卡扣', materialCode: 'CLIP-FIX', spec: 'CF-12', required: 80, scanned: 40, batch: 'B20260707072', location: 'I-01-03', available: 200 }
  ]},
  { key: 'pick-022', workOrder: 'WO20260707025', requisition: 'REQ20260707030', product: '液位变送器', priority: '中', planQty: 10, unit: '套', planDate: YESTERDAY, expectedTime: '15:00', handler: '李仓', handoverTime: '16:20', cancelled: false, items: [
    { material: '变送器本体', materialCode: 'LT-BODY', spec: 'LT-4-20', required: 10, scanned: 10, batch: 'B20260704030', location: 'E-03-01', available: 15 },
    { material: '法兰接头', materialCode: 'FLANGE', spec: 'FL-DN50', required: 10, scanned: 10, batch: 'B20260704031', location: 'E-03-02', available: 20 }
  ]},
  { key: 'pick-023', workOrder: 'WO20260707026', requisition: 'REQ20260707031', product: '电磁流量计', priority: '低', planQty: 5, unit: '套', planDate: YESTERDAY, expectedTime: '11:00', handler: '李仓', handoverTime: '12:15', cancelled: false, items: [
    { material: '流量计表体', materialCode: 'FLOW-BODY', spec: 'FB-DN80', required: 5, scanned: 5, batch: 'B20260704040', location: 'F-03-01', available: 8 },
    { material: '电极组件', materialCode: 'ELECTRODE', spec: 'EL-SS', required: 10, scanned: 10, batch: 'B20260704041', location: 'F-03-02', available: 20 },
    { material: '接地环', materialCode: 'GROUND-RING', spec: 'GR-DN80', required: 5, scanned: 5, batch: 'B20260704042', location: 'F-03-03', available: 12 }
  ]},
  { key: 'pick-024', workOrder: 'WO20260707027', requisition: 'REQ20260707032', product: '压力变送器总成', priority: '高', planQty: 8, unit: '套', planDate: YESTERDAY, expectedTime: '09:30', handler: '赵工', handoverTime: '10:40', cancelled: false, items: [
    { material: '压力芯体', materialCode: 'PRESS-CORE-2', spec: 'PC-350', required: 8, scanned: 8, batch: 'B20260704050', location: 'E-02-05', available: 16 },
    { material: '显示表头', materialCode: 'DISP-HEAD', spec: 'DH-LCD', required: 8, scanned: 8, batch: 'B20260704051', location: 'E-02-06', available: 12 }
  ]}
]

const replenishRecords = [
  { workOrder: 'WO20260708009', product: '测井探头组件', material: '防爆接插件', materialCode: 'EXPROOF', spec: 'EX-4P', qty: 4, reason: '装配过程发现数量不足', note: '现场缺 4 件，需仓管补发', status: 'PENDING_REVIEW', submittedAt: `${TODAY} 09:45:00`, handler: '赵工' },
  { workOrder: 'WO20260708006', product: '液压控制阀组', material: '控制阀体', materialCode: 'VALVE-BODY', spec: 'VB-25', qty: 2, reason: '物料损坏需补发', note: '阀体磕碰，申请补发 2 件', status: 'REPLENISHING', submittedAt: `${YESTERDAY} 17:20:00`, handler: '李仓' },
  { workOrder: 'WO20260708001', product: '电缆接头组件', material: '电缆接头', materialCode: 'CABLE-JOINT', spec: 'CJ-3P', qty: 2, reason: '装配过程发现数量不足', note: '', status: 'COMPLETED', submittedAt: `${YESTERDAY} 11:05:00`, handler: '赵工' },
  { workOrder: 'WO20260708010', product: '高温密封组件', material: '连接器', materialCode: 'M5008', spec: 'CJ-3P', qty: 1, reason: '工艺变更追加用量', note: '工艺追加 1 件连接器', status: 'COMPLETED', submittedAt: '2026-07-08 16:40:00', handler: '赵工' },
  { workOrder: 'WO20260708015', product: '井口防喷器密封件', material: '橡胶闸板', materialCode: 'RUBBER-RAM', spec: 'RR-7-1/16', qty: 4, reason: '装配过程发现数量不足', note: '闸板磨损，需补发', status: 'PENDING_REVIEW', submittedAt: `${TODAY} 10:12:00`, handler: '赵工' },
  { workOrder: 'WO20260708016', product: '泥浆密度传感器', material: '温度补偿片', materialCode: 'TEMP-COMP', spec: 'TC-PT100', qty: 7, reason: '装配过程发现数量不足', note: '扫码后发现短缺', status: 'REPLENISHING', submittedAt: `${TODAY} 10:50:00`, handler: '赵工' },
  { workOrder: 'WO20260709001', product: '泥浆脉冲发生器', material: '脉冲阀芯', materialCode: 'PULSE-CORE', spec: 'PC-M12', qty: 1, reason: '物料损坏需补发', note: '阀芯表面划伤', status: 'PENDING_REVIEW', submittedAt: `${TODAY} 11:35:00`, handler: '赵工' },
  { workOrder: 'WO20260709002', product: '井下电源模块', material: '锂电池组', materialCode: 'BAT-PACK', spec: 'BP-36V', qty: 2, reason: '工艺变更追加用量', note: '备用电池追加', status: 'REPLENISHING', submittedAt: `${TODAY} 13:18:00`, handler: '李仓' },
  { workOrder: 'WO20260708012', product: '井下通信模块', material: '防水接头', materialCode: 'WATER-JOINT', spec: 'WJ-M12', qty: 3, reason: '装配过程发现数量不足', note: '', status: 'COMPLETED', submittedAt: `${YESTERDAY} 14:22:00`, handler: '赵工' },
  { workOrder: 'WO20260708017', product: '井下马达定子', material: '定子橡胶衬套', materialCode: 'STATOR-LINER', spec: 'SL-6-3/4', qty: 1, reason: '物料损坏需补发', note: '衬套开裂', status: 'PENDING_REVIEW', submittedAt: `${TODAY} 12:08:00`, handler: '赵工' },
  { workOrder: 'WO20260709003', product: '旋转导向短节', material: '轴承组件', materialCode: 'BEARING-KIT', spec: 'BK-6205', qty: 2, reason: '装配过程发现数量不足', note: '轴承库存不足', status: 'REPLENISHING', submittedAt: `${TODAY} 14:05:00`, handler: '李仓' },
  { workOrder: 'WO20260708005', product: '压力传感器总成', material: '不锈钢外壳', materialCode: 'SS-SHELL', spec: 'SS-Ø80', qty: 1, reason: '其他', note: '外壳变形更换', status: 'COMPLETED', submittedAt: `${YESTERDAY} 09:40:00`, handler: '赵工' },
  { workOrder: 'WO20260709004', product: '伽马探管', material: '光电倍增管', materialCode: 'PMT-TUBE', spec: 'PMT-R928', qty: 1, reason: '物料损坏需补发', note: '管脚弯曲', status: 'PENDING_REVIEW', submittedAt: `${TODAY} 15:10:00`, handler: '赵工' },
  { workOrder: 'WO20260708018', product: '振动筛筛网组件', material: '主筛网', materialCode: 'MAIN-SCREEN', spec: 'MS-200', qty: 8, reason: '装配过程发现数量不足', note: '筛网破损需整批补发', status: 'REPLENISHING', submittedAt: `${TODAY} 13:42:00`, handler: '李仓' },
  { workOrder: 'WO20260707025', product: '液位变送器', material: '法兰接头', materialCode: 'FLANGE', spec: 'FL-DN50', qty: 2, reason: '工艺变更追加用量', note: '', status: 'COMPLETED', submittedAt: '2026-07-08 15:30:00', handler: '赵工' },
  { workOrder: 'WO20260709005', product: '随钻测斜仪', material: '加速度计', materialCode: 'ACCEL', spec: 'ACC-3A', qty: 2, reason: '装配过程发现数量不足', note: '校准后发现缺件', status: 'PENDING_REVIEW', submittedAt: `${TODAY} 15:45:00`, handler: '赵工' }
]

function esc(s) {
  return String(s ?? '').replace(/\\/g, '\\\\').replace(/'/g, "''")
}

function taskStatus(task) {
  if (task.cancelled) return 'CANCELLED'
  if (task.handoverTime) return 'COMPLETED'
  const hasShortage = task.items.some((i) => i.scanned > 0 && i.scanned < i.required)
  if (hasShortage && (task.key === 'pick-001' || task.key === 'pick-overdue' || task.key === 'pick-005')) return 'PAUSED'
  if (task.items.some((i) => i.scanned > 0)) return 'IN_PROGRESS'
  return 'ASSIGNED'
}

const lines = []
const push = (s) => lines.push(s)

push(`-- 生产工人演示数据（由 scripts/seed-worker-demo.mjs 生成）`)
push(`SET NAMES utf8mb4;`)
push(`SET FOREIGN_KEY_CHECKS = 0;`)

push(`
-- 扩展拣货任务展示字段（兼容无 IF NOT EXISTS 的 MySQL）
SET @db := DATABASE();
`)

const alterCols = [
  ['priority', 'VARCHAR(10) NULL'],
  ['product_name', 'VARCHAR(150) NULL'],
  ['plan_qty', 'DECIMAL(18,3) NULL'],
  ['unit', 'VARCHAR(16) NULL'],
  ['handler_name', 'VARCHAR(64) NULL'],
  ['cancelled', 'TINYINT NOT NULL DEFAULT 0'],
  ['work_order_no', 'VARCHAR(64) NULL'],
  ['requisition_no', 'VARCHAR(64) NULL']
]
for (const [col, def] of alterCols) {
  push(`
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='out_picking_task' AND COLUMN_NAME='${col}');
SET @sql := IF(@exists=0, 'ALTER TABLE out_picking_task ADD COLUMN ${col} ${def}', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
`)
}

push(`
CREATE TABLE IF NOT EXISTS worker_replenish_request (
  replenish_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  picking_task_id BIGINT NULL,
  picking_line_id BIGINT NULL,
  worker_id BIGINT NOT NULL,
  work_order_no VARCHAR(64) NULL,
  requisition_no VARCHAR(64) NULL,
  product_name VARCHAR(128) NULL,
  item_id BIGINT NULL,
  material_code VARCHAR(64) NULL,
  material_name VARCHAR(128) NULL,
  spec_model VARCHAR(128) NULL,
  request_qty DECIMAL(18,3) NOT NULL,
  reason VARCHAR(64) NOT NULL,
  note VARCHAR(512) NULL,
  request_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handler_name VARCHAR(64) NULL,
  INDEX idx_wr_worker (worker_id),
  INDEX idx_wr_task (picking_task_id),
  INDEX idx_wr_status (request_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`)

// Clean previous worker demo range 101-199 / 1001+
push(`
DELETE FROM worker_replenish_request WHERE replenish_id BETWEEN 1001 AND 1999 OR worker_id = 1;
DELETE FROM worker_handover WHERE picking_task_id BETWEEN 101 AND 199;
DELETE FROM worker_scan_record WHERE picking_task_id BETWEEN 101 AND 199 OR worker_id = 1;
DELETE FROM worker_exception WHERE picking_task_id BETWEEN 101 AND 199 OR worker_id = 1;
DELETE FROM out_picking_line WHERE picking_task_id BETWEEN 101 AND 199;
DELETE FROM out_picking_task WHERE picking_task_id BETWEEN 101 AND 199;
UPDATE out_picking_task SET assigned_to = NULL WHERE assigned_to = 1 AND picking_task_id < 101;
DELETE FROM out_order_line WHERE outbound_line_id BETWEEN 1001 AND 1999;
DELETE FROM out_order WHERE outbound_id BETWEEN 101 AND 199;
DELETE FROM pmc_requisition_line WHERE requisition_line_id BETWEEN 1001 AND 1999;
DELETE FROM pmc_requisition_order WHERE requisition_id BETWEEN 101 AND 199;
DELETE FROM pmc_production_plan_line WHERE plan_line_id BETWEEN 1001 AND 1999;
DELETE FROM pmc_production_plan WHERE plan_id BETWEEN 101 AND 199;
DELETE FROM inv_inventory WHERE inventory_id BETWEEN 201 AND 999;
DELETE FROM md_batch WHERE batch_id BETWEEN 201 AND 999;
DELETE FROM md_item WHERE item_id BETWEEN 201 AND 999;
DELETE FROM wh_location WHERE location_id BETWEEN 101 AND 999;
`)

// Collect unique materials / locations / batches
const materialMap = new Map()
const locationMap = new Map()
const batchMap = new Map()
let mid = 201
let lid = 101
let bid = 201
let iid = 201

for (const task of tasks) {
  if (!materialMap.has(`PROD:${task.product}`)) {
    const id = mid++
    materialMap.set(`PROD:${task.product}`, { id, code: `P-W-${id}`, name: task.product, spec: '', type: 'PRODUCT' })
  }
  for (const item of task.items) {
    if (!materialMap.has(item.materialCode)) {
      materialMap.set(item.materialCode, { id: mid++, code: item.materialCode, name: item.material, spec: item.spec, type: 'MATERIAL' })
    }
    if (!locationMap.has(item.location)) {
      locationMap.set(item.location, { id: lid++, code: item.location })
    }
    const bkey = `${item.materialCode}|${item.batch}`
    if (!batchMap.has(bkey)) {
      batchMap.set(bkey, { id: bid++, itemCode: item.materialCode, batchNo: item.batch, available: item.available })
    }
  }
}

push(`-- 库位（已存在编码则复用，避免 UNIQUE 冲突导致 location_id 悬空）`)
for (const loc of locationMap.values()) {
  push(`INSERT INTO wh_location (location_id, zone_id, location_code, location_name, location_status)
SELECT ${loc.id}, 1, '${esc(loc.code)}', '${esc(loc.code)}', 'AVAILABLE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wh_location WHERE location_code = '${esc(loc.code)}');`)
  push(`SET @loc_${loc.id} := (SELECT location_id FROM wh_location WHERE location_code = '${esc(loc.code)}' LIMIT 1);`)
}

push(`-- 物料`)
for (const m of materialMap.values()) {
  push(`INSERT INTO md_item (item_id, item_code, item_name, category_id, uom_id, spec_model, item_type, status) VALUES (${m.id}, '${esc(m.code)}', '${esc(m.name)}', ${m.type === 'PRODUCT' ? 1 : 2}, ${m.type === 'PRODUCT' ? 1 : 2}, '${esc(m.spec || '')}', '${m.type}', 'ENABLED') ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), spec_model=VALUES(spec_model);`)
}

push(`-- 批次 + 库存`)
for (const b of batchMap.values()) {
  const item = materialMap.get(b.itemCode)
  // find a location that uses this batch
  let locCode = 'A-03-02'
  for (const task of tasks) {
    for (const it of task.items) {
      if (it.materialCode === b.itemCode && it.batch === b.batchNo) {
        locCode = it.location
        break
      }
    }
  }
  push(`INSERT INTO md_batch (batch_id, item_id, batch_no, quality_status) VALUES (${b.id}, ${item.id}, '${esc(b.batchNo)}', 'QUALIFIED') ON DUPLICATE KEY UPDATE batch_no=VALUES(batch_no);`)
  push(`INSERT INTO inv_inventory (inventory_id, warehouse_id, location_id, item_id, batch_id, onhand_qty, available_qty, reserved_qty, frozen_qty, inventory_status, version)
SELECT ${iid}, 1, wl.location_id, ${item.id}, ${b.id}, ${b.available}.000, ${b.available}.000, 0.000, 0.000, 'AVAILABLE', 0
FROM wh_location wl WHERE wl.location_code = '${esc(locCode)}' LIMIT 1
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), onhand_qty=VALUES(onhand_qty), location_id=VALUES(location_id);`)
  b.inventoryId = iid
  b.locationCode = locCode
  iid++
}

const taskIdByKey = new Map()
const lineIdByTaskMaterial = new Map()
let planId = 101
let reqId = 101
let outId = 101
let outLineId = 1001
let pickId = 101
let pickLineId = 1001
let reqLineId = 1001
let planLineId = 1001

push(`-- 计划 / 领料 / 出库 / 拣货`)
for (const task of tasks) {
  const product = [...materialMap.values()].find((m) => m.name === task.product)
  const status = taskStatus(task)
  const planned = `${task.planDate} ${task.expectedTime}:00`

  push(`INSERT INTO pmc_production_plan (plan_id, plan_no, source_system_id, source_order_id, mes_plan_no, plan_status, planned_start_date, planned_end_date, created_by, created_at) VALUES (${planId}, 'PP-W-${planId}', 1, 1, '${esc(task.workOrder)}', 'READY', '${task.planDate}', '${task.planDate}', 1, '${planned}') ON DUPLICATE KEY UPDATE mes_plan_no=VALUES(mes_plan_no), planned_start_date=VALUES(planned_start_date);`)

  for (const item of task.items) {
    const mat = materialMap.get(item.materialCode)
    push(`INSERT INTO pmc_production_plan_line (plan_line_id, plan_id, item_id, required_qty, due_date, line_status) VALUES (${planLineId++}, ${planId}, ${mat.id}, ${item.required}.000, '${task.planDate}', 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);`)
  }

  push(`INSERT INTO pmc_requisition_order (requisition_id, requisition_no, source_order_id, source_plan_id, requisition_dept, requested_by, requested_at, requisition_status) VALUES (${reqId}, 'REQ-W-${reqId}', 1, ${planId}, '生产一部', 1, '${planned}', 'PENDING_OUTBOUND') ON DUPLICATE KEY UPDATE source_plan_id=VALUES(source_plan_id);`)

  const reqLineIds = []
  for (const item of task.items) {
    const mat = materialMap.get(item.materialCode)
    push(`INSERT INTO pmc_requisition_line (requisition_line_id, requisition_id, item_id, required_qty, issued_qty, line_status) VALUES (${reqLineId}, ${reqId}, ${mat.id}, ${item.required}.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE required_qty=VALUES(required_qty);`)
    reqLineIds.push(reqLineId++)
  }

  push(`INSERT INTO out_order (outbound_id, outbound_no, requisition_id, outbound_type, warehouse_id, outbound_status, approved_at) VALUES (${outId}, 'OUT-W-${outId}', ${reqId}, 'PRODUCTION_ISSUE', 1, '${status === 'COMPLETED' ? 'COMPLETED' : 'PICKING'}', '${planned}') ON DUPLICATE KEY UPDATE outbound_status=VALUES(outbound_status);`)

  const outLineIds = []
  task.items.forEach((item, idx) => {
    const mat = materialMap.get(item.materialCode)
    push(`INSERT INTO out_order_line (outbound_line_id, outbound_id, requisition_line_id, item_id, plan_qty, picked_qty, shipped_qty, line_status) VALUES (${outLineId}, ${outId}, ${reqLineIds[idx]}, ${mat.id}, ${item.required}.000, ${item.scanned}.000, 0.000, 'OPEN') ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty);`)
    outLineIds.push(outLineId++)
  })

  push(`INSERT INTO out_picking_task (picking_task_id, picking_task_no, outbound_id, assigned_to, task_status, planned_pick_time, created_at, priority, product_name, plan_qty, unit, handler_name, cancelled, work_order_no, requisition_no) VALUES (${pickId}, 'PICK-W-${pickId}', ${outId}, 1, '${status}', '${planned}', '${planned}', '${esc(task.priority)}', '${esc(task.product)}', ${task.planQty}.000, '${esc(task.unit)}', '${esc(task.handler)}', ${task.cancelled ? 1 : 0}, '${esc(task.workOrder)}', '${esc(task.requisition)}') ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), assigned_to=1, product_name=VALUES(product_name), plan_qty=VALUES(plan_qty), priority=VALUES(priority), handler_name=VALUES(handler_name), cancelled=VALUES(cancelled), work_order_no=VALUES(work_order_no), requisition_no=VALUES(requisition_no);`)

  taskIdByKey.set(task.key, pickId)
  taskIdByKey.set(task.workOrder, pickId)

  task.items.forEach((item, idx) => {
    const mat = materialMap.get(item.materialCode)
    const bkey = `${item.materialCode}|${item.batch}`
    const batch = batchMap.get(bkey)
    const loc = locationMap.get(item.location)
    push(`INSERT INTO out_picking_line (picking_line_id, picking_task_id, outbound_line_id, inventory_id, item_id, batch_id, location_id, plan_pick_qty, actual_pick_qty, worker_scanned_qty)
SELECT ${pickLineId}, ${pickId}, ${outLineIds[idx]}, ${batch.inventoryId}, ${mat.id}, ${batch.id}, wl.location_id, ${item.required}.000, ${item.scanned}.000, ${item.scanned}.000
FROM wh_location wl WHERE wl.location_code = '${esc(item.location)}' LIMIT 1
ON DUPLICATE KEY UPDATE worker_scanned_qty=VALUES(worker_scanned_qty), plan_pick_qty=VALUES(plan_pick_qty), location_id=VALUES(location_id);`)
    lineIdByTaskMaterial.set(`${task.key}|${item.materialCode}`, pickLineId)
    lineIdByTaskMaterial.set(`${task.workOrder}|${item.materialCode}`, pickLineId)
    pickLineId++
  })

  if (task.handoverTime) {
    push(`INSERT INTO worker_handover (picking_task_id, worker_id, work_order_no, requisition_no, warehouse_handler, handover_time, remark) VALUES (${pickId}, 1, '${esc(task.workOrder)}', '${esc(task.requisition)}', '${esc(task.handler)}', '${task.planDate} ${task.handoverTime}:00', '演示交接') ON DUPLICATE KEY UPDATE handover_time=VALUES(handover_time);`)
  }

  planId++
  reqId++
  outId++
  pickId++
}

// Exceptions
push(`-- 异常`)
const ex1Task = taskIdByKey.get('pick-001')
const ex1Line = lineIdByTaskMaterial.get('pick-001|EXPROOF')
const ex2Task = taskIdByKey.get('pick-overdue')
const ex2Line = lineIdByTaskMaterial.get('pick-overdue|VALVE-BODY')
push(`INSERT INTO worker_exception (exception_id, picking_task_id, picking_line_id, worker_id, work_order_no, requisition_no, material_name, required_qty, actual_qty, shortage_qty, exception_type, exception_note, location_code, exception_status, submitted_at) VALUES
(1001, ${ex1Task}, ${ex1Line}, 1, 'WO20260708009', 'REQ20260708018', '防爆接插件', 40.000, 36.000, 4.000, '数量不足', '现场只收到 36 件', 'A-03-08', 'PENDING_WAREHOUSE', '${TODAY} 09:42:00'),
(1002, ${ex2Task}, ${ex2Line}, 1, 'WO20260708006', 'REQ20260708015', '控制阀体', 12.000, 10.000, 2.000, '数量不足', '昨日领料缺 2 件，等待补拣', 'G-03-02', 'PENDING_WAREHOUSE', '${YESTERDAY} 17:05:00'),
(1003, ${ex1Task}, ${ex1Line}, 1, 'WO20260707020', 'REQ20260707022', '连接法兰', 6.000, 6.000, 0.000, '找不到物料', '已补拣完成', 'A-01-01', 'CLOSED', '2026-07-07 15:30:00')
ON DUPLICATE KEY UPDATE exception_status=VALUES(exception_status);`)

// Scans
push(`-- 扫码记录`)
const scanTask1 = taskIdByKey.get('pick-001')
const scanLine1 = lineIdByTaskMaterial.get('pick-001|EXPROOF')
const scanTask6 = taskIdByKey.get('pick-006')
const scanLine10 = lineIdByTaskMaterial.get('pick-006|DRILL-JOINT')
const scanLine11 = lineIdByTaskMaterial.get('pick-006|WEAR-SLEEVE')
push(`INSERT INTO worker_scan_record (scan_id, picking_task_id, picking_line_id, worker_id, barcode_value, item_id, batch_id, material_name, batch_no, scan_result, result_message, scanned_at) VALUES
(1001, ${scanTask1}, ${scanLine1}, 1, 'BC0001', ${materialMap.get('EXPROOF').id}, ${batchMap.get('EXPROOF|B20260706004').id}, '防爆接插件', 'B20260706004', 'SUCCESS', '成功', '${TODAY} 09:31:20'),
(1002, ${scanTask1}, ${scanLine1}, 1, 'BC0002', ${materialMap.get('EXPROOF').id}, ${batchMap.get('EXPROOF|B20260706004').id}, '防爆接插件', 'B20260706004', 'SUCCESS', '成功', '${TODAY} 09:31:45'),
(1003, ${scanTask1}, ${scanLine1}, 1, 'BC9999', ${materialMap.get('SEAL-HIGH').id}, ${batchMap.get('SEAL-HIGH|B20260705011').id}, '高温密封圈', 'B20260705011', 'FAILED', '非当前物料', '${TODAY} 09:32:10'),
(1004, ${scanTask6}, ${scanLine10}, 1, 'BC0101', ${materialMap.get('DRILL-JOINT').id}, ${batchMap.get('DRILL-JOINT|B20260707001').id}, '钻具接头', 'B20260707001', 'SUCCESS', '成功', '${TODAY} 10:18:05'),
(1005, ${scanTask6}, ${scanLine11}, 1, 'BC0102', ${materialMap.get('WEAR-SLEEVE').id}, ${batchMap.get('WEAR-SLEEVE|B20260707003').id}, '耐磨套筒', 'B20260707003', 'SUCCESS', '成功', '${TODAY} 10:22:30')
ON DUPLICATE KEY UPDATE scan_result=VALUES(scan_result);`)

push(`-- 补料申请`)
let rid = 1001
for (const r of replenishRecords) {
  const taskId = taskIdByKey.get(r.workOrder) || 'NULL'
  const lineId = lineIdByTaskMaterial.get(`${r.workOrder}|${r.materialCode}`) || 'NULL'
  const item = materialMap.get(r.materialCode)
  push(`INSERT INTO worker_replenish_request (replenish_id, picking_task_id, picking_line_id, worker_id, work_order_no, product_name, item_id, material_code, material_name, spec_model, request_qty, reason, note, request_status, submitted_at, handler_name) VALUES (${rid++}, ${taskId}, ${lineId}, 1, '${esc(r.workOrder)}', '${esc(r.product)}', ${item ? item.id : 'NULL'}, '${esc(r.materialCode)}', '${esc(r.material)}', '${esc(r.spec)}', ${r.qty}.000, '${esc(r.reason)}', '${esc(r.note)}', '${r.status}', '${r.submittedAt}', '${esc(r.handler)}') ON DUPLICATE KEY UPDATE request_status=VALUES(request_status), request_qty=VALUES(request_qty);`)
}

push(`SET FOREIGN_KEY_CHECKS = 1;`)
push(`SELECT 'worker demo seeded' AS result, (SELECT COUNT(*) FROM out_picking_task WHERE assigned_to=1) AS tasks, (SELECT COUNT(*) FROM worker_replenish_request WHERE worker_id=1) AS replenish;`)

const sql = lines.join('\n')
const outPath = join(__dirname, '..', 'src', 'main', 'resources', 'sql', 'worker_demo_seed.sql')
writeFileSync(outPath, sql, 'utf8')
console.log('Wrote', outPath)

const result = spawnSync('mysql', ['-uroot', '-p123456', 'wms_db'], {
  input: sql,
  encoding: 'utf8',
  maxBuffer: 20 * 1024 * 1024
})
if (result.stderr && !result.stderr.includes('Using a password')) {
  console.error(result.stderr)
}
if (result.status !== 0) {
  console.error(result.stdout)
  process.exit(result.status || 1)
}
console.log(result.stdout || 'OK')
