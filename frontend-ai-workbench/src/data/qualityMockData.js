/** 质检模块演示数据（后端 /quality/* 未就绪时使用） */
import { calcAqlSampleSize } from './qualityConstants'

/** 油套管接箍 — 多尺寸检测项（模拟图纸尺寸表） */
const COUPLING_DIMENSIONS = [
  { id: 'D01', balloon: '①', name: '外径 ØD', nominal: 177.8, upperTol: 0.05, lowerTol: -0.05, unit: 'mm', instrument: '外径千分尺', critical: true },
  { id: 'D02', balloon: '②', name: '内径 Ød', nominal: 157.14, upperTol: 0.08, lowerTol: -0.00, unit: 'mm', instrument: '内径千分尺', critical: true },
  { id: 'D03', balloon: '③', name: '接箍长度 L', nominal: 184.15, upperTol: 0.50, lowerTol: -0.50, unit: 'mm', instrument: '游标卡尺', critical: false },
  { id: 'D04', balloon: '④', name: '螺纹中径', nominal: 171.45, upperTol: 0.04, lowerTol: -0.04, unit: 'mm', instrument: '螺纹中径仪', critical: true },
  { id: 'D05', balloon: '⑤', name: '螺纹锥度', nominal: 0.0625, upperTol: 0.002, lowerTol: -0.002, unit: 'in/in', instrument: '锥度规', critical: true },
  { id: 'D06', balloon: '⑥', name: '螺纹螺距', nominal: 5.08, upperTol: 0.05, lowerTol: -0.05, unit: 'mm', instrument: '螺距规', critical: false },
  { id: 'D07', balloon: '⑦', name: '台肩面跳动', nominal: 0, upperTol: 0.05, lowerTol: 0, unit: 'mm', instrument: '百分表', critical: false },
  { id: 'D08', balloon: '⑧', name: '密封面粗糙度 Ra', nominal: 1.6, upperTol: 0.4, lowerTol: -0.4, unit: 'μm', instrument: '粗糙度仪', critical: false },
  { id: 'D09', balloon: '⑨', name: '壁厚最小', nominal: 10.33, upperTol: 999, lowerTol: 0, unit: 'mm', instrument: '超声波测厚', critical: true },
  { id: 'D10', balloon: '⑩', name: '倒角 C', nominal: 1.5, upperTol: 0.3, lowerTol: -0.3, unit: 'mm', instrument: 'R 规', critical: false },
  { id: 'D11', balloon: '⑪', name: '螺纹牙高', nominal: 1.575, upperTol: 0.05, lowerTol: -0.05, unit: 'mm', instrument: '螺纹高度规', critical: false },
  { id: 'D12', balloon: '⑫', name: '端面垂直度', nominal: 0, upperTol: 0.08, lowerTol: 0, unit: 'mm', instrument: '直角尺+塞尺', critical: false }
]

const PACKER_DIMENSIONS = [
  { id: 'P01', balloon: '①', name: '本体外径', nominal: 114.3, upperTol: 0.10, lowerTol: -0.10, unit: 'mm', instrument: '外径千分尺', critical: true },
  { id: 'P02', balloon: '②', name: '密封件内径', nominal: 89.0, upperTol: 0.03, lowerTol: -0.03, unit: 'mm', instrument: '内径千分尺', critical: true },
  { id: 'P03', balloon: '③', name: '总成长度', nominal: 1250, upperTol: 2.0, lowerTol: -2.0, unit: 'mm', instrument: '钢卷尺', critical: false },
  { id: 'P04', balloon: '④', name: '密封面平面度', nominal: 0, upperTol: 0.02, lowerTol: 0, unit: 'mm', instrument: '平晶', critical: true },
  { id: 'P05', balloon: '⑤', name: '卡瓦外径', nominal: 112.5, upperTol: 0.15, lowerTol: -0.15, unit: 'mm', instrument: '外径千分尺', critical: false },
  { id: 'P06', balloon: '⑥', name: '螺纹中径', nominal: 98.425, upperTol: 0.05, lowerTol: -0.05, unit: 'mm', instrument: '螺纹中径仪', critical: true },
  { id: 'P07', balloon: '⑦', name: 'O 型圈槽宽', nominal: 3.6, upperTol: 0.05, lowerTol: -0.05, unit: 'mm', instrument: '游标卡尺', critical: false },
  { id: 'P08', balloon: '⑧', name: 'O 型圈槽深', nominal: 2.7, upperTol: 0.05, lowerTol: -0.05, unit: 'mm', instrument: '深度尺', critical: false }
]

const PLUNGER_DIMENSIONS = [
  { id: 'L01', balloon: '①', name: '柱塞直径', nominal: 44.45, upperTol: 0.02, lowerTol: -0.02, unit: 'mm', instrument: '外径千分尺', critical: true },
  { id: 'L02', balloon: '②', name: '柱塞长度', nominal: 600, upperTol: 1.0, lowerTol: -1.0, unit: 'mm', instrument: '游标卡尺', critical: false },
  { id: 'L03', balloon: '③', name: '圆柱度', nominal: 0, upperTol: 0.01, lowerTol: 0, unit: 'mm', instrument: '圆度仪', critical: true },
  { id: 'L04', balloon: '④', name: '表面粗糙度 Ra', nominal: 0.4, upperTol: 0.2, lowerTol: -0.1, unit: 'μm', instrument: '粗糙度仪', critical: false },
  { id: 'L05', balloon: '⑤', name: '硬度 HRC', nominal: 60, upperTol: 2, lowerTol: -2, unit: 'HRC', instrument: '洛氏硬度计', critical: true },
  { id: 'L06', balloon: '⑥', name: '同轴度', nominal: 0, upperTol: 0.02, lowerTol: 0, unit: 'mm', instrument: '偏摆仪', critical: false }
]

const FLANGE_DIMENSIONS = [
  { id: 'F01', balloon: '①', name: '法兰外径', nominal: 285, upperTol: 1.0, lowerTol: -1.0, unit: 'mm', instrument: '游标卡尺', critical: false },
  { id: 'F02', balloon: '②', name: '法兰厚度', nominal: 22.0, upperTol: 0.1, lowerTol: -0.1, unit: 'mm', instrument: '游标卡尺', critical: true },
  { id: 'F03', balloon: '③', name: '螺栓孔中心距', nominal: 240, upperTol: 0.5, lowerTol: -0.5, unit: 'mm', instrument: '游标卡尺', critical: true },
  { id: 'F04', balloon: '④', name: '螺栓孔径', nominal: 22, upperTol: 0.3, lowerTol: -0.0, unit: 'mm', instrument: '塞规', critical: false },
  { id: 'F05', balloon: '⑤', name: '密封面粗糙度', nominal: 3.2, upperTol: 1.6, lowerTol: -1.6, unit: 'μm', instrument: '粗糙度仪', critical: false },
  { id: 'F06', balloon: '⑥', name: '内孔直径', nominal: 159.3, upperTol: 0.5, lowerTol: -0.0, unit: 'mm', instrument: '内径千分尺', critical: true }
]

function buildDrawing(itemName, drawingNo, rev = 'A') {
  return {
    drawingNo,
    rev,
    title: itemName,
    fileName: `${drawingNo}_Rev${rev}.pdf`,
    pages: 2,
    format: 'PDF',
    previewType: 'svg',
    updatedAt: '2026-06-15'
  }
}

function buildAql(lotSize, strict = false) {
  const level = strict ? 'III' : 'II'
  const aql = strict ? 1.0 : 1.5
  const sample = calcAqlSampleSize(lotSize, level, aql)
  return {
    standard: 'GB/T 2828.1-2012',
    level,
    aql,
    lotSize,
    ...sample,
    inspectedCount: 0,
    defectCount: 0
  }
}

export const MOCK_INSPECTION_TASKS = [
  {
    receiptId: 1001,
    inspectionId: null,
    id: '待生成',
    receiptNo: 'REC-20260708001',
    product: '油套管接箍 7"',
    batchNo: 'B20260708001',
    qty: '120 件',
    status: '待检测',
    strict: false,
    hasInspection: false
  },
  {
    receiptId: 1002,
    inspectionId: null,
    id: '待生成',
    receiptNo: 'REC-20260708002',
    product: '封隔器总成',
    batchNo: 'B20260707003',
    qty: '48 件',
    status: '待检测',
    strict: true,
    hasInspection: false
  },
  {
    receiptId: 1003,
    inspectionId: 501,
    id: 'QC-20260707012',
    receiptNo: 'REC-20260707008',
    product: '抽油泵柱塞',
    batchNo: 'B20260707008',
    qty: '200 件',
    status: '检测中',
    strict: false,
    hasInspection: true
  },
  {
    receiptId: 1004,
    inspectionId: 502,
    id: 'QC-20260706005',
    receiptNo: 'REC-20260706003',
    product: '井下安全阀',
    batchNo: 'B20260706003',
    qty: '36 件',
    status: '完成',
    strict: false,
    hasInspection: true
  },
  {
    receiptId: 1005,
    inspectionId: null,
    id: '待生成',
    receiptNo: 'REC-20260709001',
    product: '管线法兰 DN150',
    batchNo: 'B20260709001',
    qty: '80 件',
    status: '待检测',
    strict: false,
    hasInspection: false
  },
  {
    receiptId: 1006,
    inspectionId: null,
    id: '待生成',
    receiptNo: 'RC20260713150001',
    product: '防爆接插件',
    batchNo: 'B20260713001',
    qty: '60 件',
    status: '待检测',
    strict: false,
    hasInspection: false
  }
]

export const MOCK_QUALITY_ISSUES = [
  {
    issueId: 201,
    issueNo: 'QI-20260707003',
    itemName: '封隔器总成',
    batchNo: 'B20260707003',
    issueDesc: '密封面划痕，外观不合格',
    issueType: '外观缺陷',
    riskLevel: '中',
    status: '待分析',
    unqualifiedQty: 3
  },
  {
    issueId: 202,
    issueNo: 'QI-20260705008',
    itemName: '抽油泵柱塞',
    batchNo: 'B20260705008',
    issueDesc: '尺寸超差 0.08mm',
    issueType: '尺寸超差',
    riskLevel: '高',
    status: '分析完成',
    unqualifiedQty: 5
  },
  {
    issueId: 203,
    issueNo: 'QI-20260704002',
    itemName: '井下安全阀',
    batchNo: 'B20260704002',
    issueDesc: '耐压测试未达标',
    issueType: '性能不合格',
    riskLevel: '高',
    status: '处理中',
    unqualifiedQty: 2
  }
]

export const MOCK_ANALYTICS = {
  dateRange: { start: '2026-07-10', end: '2026-07-16' },
  kpis: [
    {
      key: 'pending',
      label: '待检任务',
      value: '128',
      delta: '较上周 -12',
      deltaTone: 'down',
      tone: 'ok',
      icon: 'clipboard',
      spark: [42, 38, 45, 40, 36, 34, 32]
    },
    {
      key: 'completed',
      label: '已完成检验',
      value: '1,246',
      delta: '较上周 +156',
      deltaTone: 'up',
      tone: 'info',
      icon: 'badge',
      spark: [18, 22, 20, 28, 26, 30, 34]
    },
    {
      key: 'passRate',
      label: '合格率',
      value: '78.6%',
      delta: '较上周 +4.2%',
      deltaTone: 'up',
      tone: 'warn',
      icon: 'shield',
      spark: [72, 74, 73, 76, 75, 78, 79]
    },
    {
      key: 'failBatches',
      label: '不合格批次',
      value: '23',
      delta: '较上周 +5',
      deltaTone: 'up-bad',
      tone: 'danger',
      icon: 'alert',
      spark: [8, 10, 9, 12, 14, 16, 18]
    },
    {
      key: 'critical',
      label: '严重不合格批次',
      value: '3',
      delta: '较上周 +1',
      deltaTone: 'up-bad',
      tone: 'critical',
      icon: 'layers',
      spark: [1, 1, 2, 1, 2, 2, 3]
    }
  ],
  passRateTrend: [
    { day: '07-10', passRate: 78.2, failRate: 21.8, inspectedCount: 142, qualifiedCount: 111 },
    { day: '07-11', passRate: 81.5, failRate: 18.5, inspectedCount: 156, qualifiedCount: 127 },
    { day: '07-12', passRate: 76.8, failRate: 23.2, inspectedCount: 138, qualifiedCount: 106 },
    { day: '07-13', passRate: 79.4, failRate: 20.6, inspectedCount: 165, qualifiedCount: 131 },
    { day: '07-14', passRate: 73.1, failRate: 26.9, inspectedCount: 128, qualifiedCount: 94 },
    { day: '07-15', passRate: 75.6, failRate: 24.4, inspectedCount: 148, qualifiedCount: 112 },
    { day: '07-16', passRate: 82.0, failRate: 18.0, inspectedCount: 160, qualifiedCount: 131 }
  ],
  issueTypeStats: [
    { type: 'APPEARANCE', label: '外观缺陷', count: 8, percent: 35, wow: 2.1, color: '#5f8f74' },
    { type: 'DIMENSION', label: '尺寸偏差', count: 6, percent: 26, wow: -1.3, color: '#6b8cae' },
    { type: 'PERFORMANCE', label: '性能不达标', count: 4, percent: 17, wow: 0.8, color: '#4a6fa5' },
    { type: 'FUNCTION', label: '功能异常', count: 3, percent: 13, wow: 1.5, color: '#c45c4a' },
    { type: 'PACKAGING', label: '包装问题', count: 2, percent: 9, wow: -0.6, color: '#c4a574' }
  ],
  alerts: [
    {
      level: 'warn',
      title: '合格率连续3天下降',
      desc: '近3日合格率由 79.4% 降至 73.1%，建议排查抽检与供应商批次。',
      time: '2026-07-16 09:30'
    },
    {
      level: 'danger',
      title: '严重不合格批次增加',
      desc: '严重不合格批次较上周增长 100%，需立即锁定关联库存。',
      time: '2026-07-16 08:15'
    },
    {
      level: 'warn',
      title: '供应商合格率偏低',
      desc: '西南密封件厂本期合格率 50.0%，已低于 60% 警戒线。',
      time: '2026-07-15 16:45'
    }
  ],
  itemRanking: [
    { itemName: '防爆接插件总成', passRate: 92.5, failBatches: 3, wow: 5.2, issueCount: 3, riskLevel: '低' },
    { itemName: '封隔器总成', passRate: 88.2, failBatches: 4, wow: -2.1, issueCount: 4, riskLevel: '高' },
    { itemName: '抽油泵柱塞', passRate: 85.6, failBatches: 5, wow: 1.4, issueCount: 5, riskLevel: '中' },
    { itemName: '井下安全阀', passRate: 81.0, failBatches: 3, wow: -3.8, issueCount: 3, riskLevel: '高' },
    { itemName: '管线法兰 DN150', passRate: 94.1, failBatches: 1, wow: 2.0, issueCount: 1, riskLevel: '低' }
  ],
  supplierStats: [
    { supplierName: '华北精密制造', batchCount: 28, passRate: 88.5, failBatches: 5, wow: 3.2, issueCount: 5 },
    { supplierName: '胜利油田装备', batchCount: 22, passRate: 91.2, failBatches: 2, wow: 1.8, issueCount: 2 },
    { supplierName: '西南密封件厂', batchCount: 12, passRate: 50.0, failBatches: 6, wow: -8.7, issueCount: 6 },
    { supplierName: '渤海管件科技', batchCount: 15, passRate: 95.3, failBatches: 1, wow: 0.6, issueCount: 1 },
    { supplierName: '东营密封科技', batchCount: 18, passRate: 86.7, failBatches: 3, wow: -1.2, issueCount: 3 }
  ]
}

const MOCK_INSPECTION_DETAILS = {
  1001: {
    inspection: { inspectionNo: 'QC-20260709001' },
    receipt: { receiptNo: 'REC-20260708001' },
    supplierName: '华北精密制造',
    strict: false,
    lineDetails: [{
      receiptLineId: 10011,
      itemId: 301,
      batchId: 401,
      itemName: '油套管接箍 7"',
      itemCode: 'CPL-7-L80',
      batchNo: 'B20260708001',
      receivedQty: 120,
      drawing: buildDrawing('油套管接箍 7"', 'DWG-CPL-7-001', 'C'),
      aql: buildAql(120, false),
      dimensions: COUPLING_DIMENSIONS,
      standards: [
        { code: 'APPEARANCE', name: '外观检测', standard: '表面无划痕、无锈蚀、无变形；螺纹牙型完整', type: 'choice' },
        { code: 'DIMENSION', name: '尺寸检测', standard: '按图纸 DWG-CPL-7-001 Rev.C 尺寸表（12 项）', type: 'numeric' },
        { code: 'PRESSURE', name: '耐压测试', standard: '480V 绝缘耐压 ≥ 60s 无击穿', type: 'performance', unit: 'V' }
      ],
      performance: { code: 'PRESSURE', name: '耐压测试', standard: '480V 绝缘耐压 ≥ 60s 无击穿', unit: 'V', minValue: 480 }
    }]
  },
  1002: {
    inspection: { inspectionNo: 'QC-20260709002' },
    receipt: { receiptNo: 'REC-20260708002' },
    supplierName: '西南密封件厂',
    strict: true,
    lineDetails: [{
      receiptLineId: 10021,
      itemId: 302,
      batchId: 402,
      itemName: '封隔器总成',
      itemCode: 'PKR-5.5-35',
      batchNo: 'B20260707003',
      receivedQty: 48,
      drawing: buildDrawing('封隔器总成', 'DWG-PKR-55-012', 'B'),
      aql: buildAql(48, true),
      dimensions: PACKER_DIMENSIONS,
      standards: [
        { code: 'APPEARANCE', name: '外观检测', standard: '密封面无划痕，标识清晰（加严：全检）', type: 'choice' },
        { code: 'DIMENSION', name: '尺寸检测', standard: '按图纸 DWG-PKR-55-012 Rev.B 尺寸表（8 项）', type: 'numeric' },
        { code: 'PRESSURE', name: '耐压测试', standard: '35MPa 保压 5min 无泄漏', type: 'performance', unit: 'MPa' }
      ],
      performance: { code: 'PRESSURE', name: '耐压测试', standard: '35MPa 保压 5min 无泄漏', unit: 'MPa', minValue: 35 }
    }]
  },
  1003: {
    inspection: { inspectionNo: 'QC-20260707012' },
    receipt: { receiptNo: 'REC-20260707008' },
    supplierName: '胜利油田装备',
    strict: false,
    lineDetails: [{
      receiptLineId: 10031,
      itemId: 303,
      batchId: 403,
      itemName: '抽油泵柱塞',
      itemCode: 'PLG-44.45',
      batchNo: 'B20260707008',
      receivedQty: 200,
      drawing: buildDrawing('抽油泵柱塞', 'DWG-PLG-4445-003', 'A'),
      aql: buildAql(200, false),
      dimensions: PLUNGER_DIMENSIONS,
      standards: [
        { code: 'APPEARANCE', name: '外观检测', standard: '表面光洁，无毛刺', type: 'choice' },
        { code: 'DIMENSION', name: '尺寸检测', standard: '按图纸 DWG-PLG-4445-003 Rev.A 尺寸表（6 项）', type: 'numeric' },
        { code: 'PRESSURE', name: '硬度测试', standard: 'HRC 58-62', type: 'performance', unit: 'HRC' }
      ],
      performance: { code: 'HARDNESS', name: '硬度测试', standard: 'HRC 58-62', unit: 'HRC', minValue: 58, maxValue: 62 }
    }]
  },
  1005: {
    inspection: { inspectionNo: 'QC-20260709003' },
    receipt: { receiptNo: 'REC-20260709001' },
    supplierName: '渤海管件科技',
    strict: false,
    lineDetails: [{
      receiptLineId: 10051,
      itemId: 305,
      batchId: 405,
      itemName: '管线法兰 DN150',
      itemCode: 'FLG-DN150-PN16',
      batchNo: 'B20260709001',
      receivedQty: 80,
      drawing: buildDrawing('管线法兰 DN150', 'DWG-FLG-DN150-008', 'B'),
      aql: buildAql(80, false),
      dimensions: FLANGE_DIMENSIONS,
      standards: [
        { code: 'APPEARANCE', name: '外观检测', standard: '无砂眼、无裂纹', type: 'choice' },
        { code: 'DIMENSION', name: '尺寸检测', standard: '按图纸 DWG-FLG-DN150-008 Rev.B 尺寸表（6 项）', type: 'numeric' },
        { code: 'PRESSURE', name: '密封测试', standard: '1.6MPa 水压试验无渗漏', type: 'performance', unit: 'MPa' }
      ],
      performance: { code: 'SEAL', name: '密封测试', standard: '1.6MPa 水压试验无渗漏', unit: 'MPa', minValue: 1.6 }
    }]
  },
  1006: {
    inspection: { inspectionNo: 'QC-20260713150001' },
    receipt: { receiptNo: 'RC20260713150001' },
    supplierName: '华北精密制造',
    strict: false,
    lineDetails: [{
      receiptLineId: 10061,
      itemId: 6,
      batchId: 501,
      itemName: '防爆接插件',
      itemCode: 'M-EXPROOF',
      batchNo: 'B20260713001',
      receivedQty: 60,
      drawing: buildDrawing('防爆接插件', 'DWG-EXPROOF-004', 'A'),
      aql: buildAql(60, false),
      dimensions: FLANGE_DIMENSIONS.slice(0, 4),
      standards: [
        { code: 'APPEARANCE', name: '外观检测', standard: '壳体无裂纹、镀层完整、标识清晰', type: 'choice' },
        { code: 'DIMENSION', name: '尺寸检测', standard: '按图纸 DWG-EXPROOF-004 Rev.A 尺寸表', type: 'numeric' },
        { code: 'PRESSURE', name: '绝缘耐压', standard: '500V 绝缘耐压 ≥ 60s 无击穿', type: 'performance', unit: 'V' }
      ],
      performance: { code: 'PRESSURE', name: '绝缘耐压', standard: '500V 绝缘耐压 ≥ 60s 无击穿', unit: 'V', minValue: 500 }
    }]
  }
}

const MOCK_ISSUE_DETAILS = {
  201: {
    inspection: { inspectionNo: 'QC-20260707003' },
    inspectionLine: { inspectedQty: 48, qualifiedQty: 45, unqualifiedQty: 3, lineResult: 'UNQUALIFIED' },
    item: { itemName: '封隔器总成' },
    batch: { batchNo: 'B20260707003' },
    supplier: { supplierName: '西南密封件厂' },
    inventories: [{ inventoryId: 1, onhandQty: 45, availableQty: 42, frozenQty: 3 }],
    analysis: null
  },
  202: {
    inspection: { inspectionNo: 'QC-20260705008' },
    inspectionLine: { inspectedQty: 200, qualifiedQty: 195, unqualifiedQty: 5, lineResult: 'UNQUALIFIED' },
    item: { itemName: '抽油泵柱塞' },
    batch: { batchNo: 'B20260705008' },
    supplier: { supplierName: '华北精密制造' },
    inventories: [{ inventoryId: 2, onhandQty: 195, availableQty: 190, frozenQty: 5 }],
    analysis: {
      riskLevel: '高',
      rootCause: '供应商机加工刀具磨损导致尺寸漂移',
      impactScope: '同批次 200 件中 5 件超差，已隔离',
      recommendation: '建议冻结库存并发起退货',
      analysisReport: '基于历史数据：该供应商近 30 天尺寸异常 3 次，建议触发加严检验。',
      aiPowered: false
    }
  },
  203: {
    inspection: { inspectionNo: 'QC-20260704002' },
    inspectionLine: { inspectedQty: 36, qualifiedQty: 34, unqualifiedQty: 2, lineResult: 'UNQUALIFIED' },
    item: { itemName: '井下安全阀' },
    batch: { batchNo: 'B20260704002' },
    supplier: { supplierName: '胜利油田装备' },
    inventories: [{ inventoryId: 3, onhandQty: 34, availableQty: 32, frozenQty: 2 }],
    analysis: {
      riskLevel: '高',
      rootCause: '耐压测试设备校准过期',
      impactScope: '2 件性能不合格，同供应商其他批次需复核',
      recommendation: '冻结库存 + 返修',
      analysisReport: '性能类异常占比上升，建议对该供应商启动专项审核。',
      aiPowered: false
    }
  }
}

export function buildMockWorkbench() {
  const tasks = MOCK_INSPECTION_TASKS.map((t) => ({ ...t }))
  const issues = MOCK_QUALITY_ISSUES.map((i) => ({ ...i }))
  const pending = tasks.filter((t) => t.status === '待检测').length
  const inspecting = tasks.filter((t) => t.status === '检测中').length
  const completed = tasks.filter((t) => t.status === '完成').length
  return {
    tasks,
    issues,
    pendingInspection: pending,
    inspectingCount: inspecting,
    todayCompleted: completed,
    passRatePercent: 96.2,
    openIssues: issues.filter((i) => i.status !== '已关闭').length,
    strictBatches: tasks.filter((t) => t.strict).length,
    dashboardKpis: MOCK_ANALYTICS.kpis.map((k) =>
      Array.isArray(k) ? k : [k.label, k.value, k.delta, k.tone]
    ),
    kpis: MOCK_ANALYTICS.kpis.slice(0, 5).map((k) =>
      Array.isArray(k) ? k : [k.label, k.value, k.delta, k.tone]
    ),
    inspectionStats: [],
    strictBatchList: [],
    qualityFunnel: [],
    passRateTrend: MOCK_ANALYTICS.passRateTrend,
    riskSummary: []
  }
}

export function filterMockTasks(tasks, filters = {}) {
  return tasks.filter((task) => {
    if (filters.status && task.status !== filters.status) return false
    if (filters.batchNo && !task.batchNo.includes(filters.batchNo.trim())) return false
    if (filters.keyword) {
      const kw = filters.keyword.trim().toLowerCase()
      const haystack = [task.receiptNo, task.product, task.id, task.batchNo].join(' ').toLowerCase()
      if (!haystack.includes(kw)) return false
    }
    return true
  })
}

export function getMockInspectionDetail(receiptId) {
  const detail = MOCK_INSPECTION_DETAILS[receiptId]
  if (detail) return JSON.parse(JSON.stringify(detail))
  const task = MOCK_INSPECTION_TASKS.find((t) => t.receiptId === receiptId)
  if (!task) return null
  return JSON.parse(JSON.stringify(MOCK_INSPECTION_DETAILS[1001]))
}

/**
 * 将后端简版详情补齐为专业检测工作台所需字段（图纸 / AQL / 多尺寸项）
 * 不覆盖后端已有字段。
 */
export function enrichInspectionDetail(detail) {
  if (!detail?.lineDetails?.length) return detail
  const enriched = JSON.parse(JSON.stringify(detail))
  const template = MOCK_INSPECTION_DETAILS[1001].lineDetails[0]

  for (const line of enriched.lineDetails) {
    const matched = pickDimensionTemplate(line.itemName || line.itemCode || '')
    if (!line.dimensions?.length) {
      line.dimensions = JSON.parse(JSON.stringify(matched.dimensions))
    }
    if (!line.drawing) {
      const code = line.itemCode || `ITEM-${line.itemId || 'X'}`
      line.drawing = buildDrawing(line.itemName || '物料', `DWG-${code}`, 'A')
    }
    if (!line.aql) {
      line.aql = buildAql(Number(line.receivedQty) || 50, !!enriched.strict)
    }
    if (!line.performance) {
      line.performance = matched.performance
        ? JSON.parse(JSON.stringify(matched.performance))
        : JSON.parse(JSON.stringify(template.performance))
    }
    if (!line.itemCode && matched.itemCode) line.itemCode = matched.itemCode
  }
  return enriched
}

function pickDimensionTemplate(name) {
  const n = String(name).toLowerCase()
  if (n.includes('封隔') || n.includes('packer')) return MOCK_INSPECTION_DETAILS[1002].lineDetails[0]
  if (n.includes('柱塞') || n.includes('plunger')) return MOCK_INSPECTION_DETAILS[1003].lineDetails[0]
  if (n.includes('法兰') || n.includes('flange')) return MOCK_INSPECTION_DETAILS[1005].lineDetails[0]
  if (n.includes('防爆') || n.includes('exproof')) return MOCK_INSPECTION_DETAILS[1006].lineDetails[0]
  return MOCK_INSPECTION_DETAILS[1001].lineDetails[0]
}

export function getMockIssueDetail(issueId) {
  const detail = MOCK_ISSUE_DETAILS[issueId]
  return detail ? JSON.parse(JSON.stringify(detail)) : null
}

export function mockCreateInspection(receiptId, tasks) {
  const task = tasks.find((t) => t.receiptId === receiptId)
  if (!task) throw new Error('未找到对应收货单')
  task.inspectionId = task.inspectionId || 600 + receiptId
  task.id = `QC-20260709${String(receiptId).slice(-3)}`
  task.status = '检测中'
  task.hasInspection = true
  return { inspectionId: task.inspectionId, inspectionNo: task.id }
}

export function mockSubmitInspection(payload, tasks, issues) {
  const line = payload.lines?.[0]
  const hasIssue = line?.lineResult === 'UNQUALIFIED'
  const task = tasks.find((t) => t.receiptId === payload.receiptId)
  if (task) {
    task.status = '完成'
    task.hasInspection = true
  }
  let issueIds = []
  if (hasIssue) {
    const newId = 300 + issues.length
    const defectLabel = Array.isArray(line.defectCodes) && line.defectCodes.length
      ? line.defectCodes.join('/')
      : null
    issues.unshift({
      issueId: newId,
      issueNo: `NCR-20260709${String(newId).slice(-3)}`,
      itemName: task?.product || '未知物料',
      batchNo: task?.batchNo || '—',
      issueDesc: line.issueDesc || '检测不合格',
      issueType: defectLabel || line.issueType || '质量缺陷',
      riskLevel: line.defectCodes?.includes('CRK') || line.defectCodes?.includes('THR') ? '高' : '中',
      status: '待分析',
      unqualifiedQty: line.unqualifiedQty || 1
    })
    issueIds = [newId]
  }
  return { hasIssue, issueIds, message: hasIssue ? '检测完成，已生成 NCR/质量异常单' : '检测完成，全部合格' }
}

export function mockAnalyzeIssue(issueId) {
  return {
    riskLevel: '中',
    rootCause: '供应商来料加工精度不稳定，可能与近期产线换型有关',
    impactScope: '当前批次已隔离，建议排查同供应商近 3 批来料',
    recommendation: '冻结库存',
    analysisReport: '【演示分析】基于规则引擎：外观/尺寸类异常占比 62%，该供应商 30 天内异常 2 次，建议加严检验并通知采购跟进。',
    aiPowered: false
  }
}

export function mockIssueAction(action, issueId, issues) {
  const issue = issues.find((i) => i.issueId === issueId)
  if (!issue) throw new Error('异常不存在')
  if (action === 'close') {
    issue.status = '已关闭'
    return { message: '异常已关闭' }
  }
  if (action === 'return') {
    issue.status = '处理中'
    return { returnNo: `RT-20260709${String(issueId).slice(-3)}` }
  }
  if (action === 'freeze') {
    issue.status = '处理中'
    return { message: '已通知库存管理员执行批次库存冻结（演示）' }
  }
  if (action === 'isolate') {
    issue.status = '处理中'
    return { message: '已标记禁止上架并转入隔离待退（演示）' }
  }
  if (action === 'repair') {
    issue.status = '处理中'
    return { message: '已登记返修任务（演示）' }
  }
  return { message: '操作已提交' }
}
