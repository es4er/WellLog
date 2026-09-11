/** 仓管员 UI 常量（单据数据由 /warehouse/workbench/overview 提供） */

export const OUTBOUND_STATUS = ['待拣货', '拣货中', '待复核', '待交接', '已完成', '异常']

export const PICKING_TABS = [
  { id: 'pending', label: '待拣货' },
  { id: 'picking', label: '拣货中' },
  { id: 'prep', label: '备料区' },
  { id: 'review', label: '待复核' }
]

export const OUTBOUND_TABS = [
  { id: 'pending', label: '待生成出库单' },
  { id: 'failed', label: '生成失败·待处理' },
  { id: 'generated', label: '已生成出库单' }
]

/** 异常记录按来源分栏，与「生成失败」对齐的是 generate */
export const EXCEPTION_TABS = [
  { id: 'generate', label: '出库生成', source: 'GENERATE' },
  { id: 'picking', label: '拣货异常', source: 'WORKER' },
  { id: 'review', label: '复核异常', source: 'REVIEW' }
]
