/** 系统管理员模块演示数据（对齐系统控制中心原型） */

export const adminClock = '2026-07-10 10:30:45'

export const adminKpis = {
  onlineUsers: { value: 23, hint: '今日登录 56 次' },
  apiCalls: { value: '2,391', hint: '成功率 99.8%', spark: [42, 55, 48, 62, 58, 70, 65, 72] },
  agents: { value: 12, hint: '全部运行正常' },
  alerts: { value: 2, hint: '待处理告警' },
  cpu: { value: '42%', hint: '运行平稳', spark: [38, 40, 41, 39, 42, 44, 41, 42] },
  memory: { value: '61%', hint: '运行平稳', spark: [55, 57, 58, 60, 59, 61, 62, 61] }
}

export const onlineByRole = [
  { role: 'PMC计划员', count: 4, color: '#5b8def' },
  { role: '仓管员', count: 8, color: '#3d9b6e' },
  { role: '库存管理员', count: 3, color: '#e0a045' },
  { role: '生产工人', count: 6, color: '#7b6fd6' },
  { role: '质检员', count: 2, color: '#d96b5c' }
]

export const adminUsers = [
  { id: 'U001', name: '周计划', account: 'pmc', role: 'PMC计划员', status: '在线', lastLogin: '2026-07-10 09:12', dept: '计划部' },
  { id: 'U002', name: '沈砚', account: 'warehouse', role: '仓管员', status: '在线', lastLogin: '2026-07-10 08:45', dept: '仓储部' },
  { id: 'U003', name: '韩库存', account: 'inventory', role: '库存管理员', status: '在线', lastLogin: '2026-07-10 09:01', dept: '仓储部' },
  { id: 'U004', name: '程哲', account: 'quality', role: '质检员', status: '离线', lastLogin: '2026-07-09 18:20', dept: '质量部' },
  { id: 'U005', name: '赵工', account: 'worker', role: '生产工人', status: '在线', lastLogin: '2026-07-10 07:55', dept: '生产部' },
  { id: 'U006', name: '林管理员', account: 'admin', role: '系统管理员', status: '在线', lastLogin: '2026-07-10 10:28', dept: '信息部' },
  { id: 'U007', name: '王仓管', account: 'wh02', role: '仓管员', status: '在线', lastLogin: '2026-07-10 08:10', dept: '仓储部' },
  { id: 'U008', name: '李计划', account: 'pmc02', role: 'PMC计划员', status: '离线', lastLogin: '2026-07-08 16:40', dept: '计划部' }
]

export const adminRoles = [
  { id: 'R001', name: '系统管理员', users: 2, perms: 86, updatedAt: '2026-07-01', desc: '全系统配置与审计权限' },
  { id: 'R002', name: 'PMC计划员', users: 6, perms: 42, updatedAt: '2026-06-28', desc: '订单计划、齐套与领料协同' },
  { id: 'R003', name: '仓管员', users: 12, perms: 38, updatedAt: '2026-06-25', desc: '收货、出库、拣货与异常处理' },
  { id: 'R004', name: '质检员', users: 8, perms: 28, updatedAt: '2026-06-20', desc: '检验任务、异常与质量分析' },
  { id: 'R005', name: '库存管理员', users: 5, perms: 34, updatedAt: '2026-06-18', desc: '库存控制、盘点与库位' },
  { id: 'R006', name: '生产工人', users: 48, perms: 12, updatedAt: '2026-06-15', desc: '领料扫码与现场反馈' }
]

export const permissionAuditStats = [
  { label: '今日权限变更', value: 12, tone: 'info' },
  { label: '新增用户', value: 3, tone: 'ok' },
  { label: '异常登录', value: 2, tone: 'warn' },
  { label: '权限申请', value: 5, tone: 'info' }
]

export const permissionChanges = [
  { time: '10:28:12', user: '林管理员', action: '修改权限', target: '仓管员', ip: '10.12.3.21', status: '成功' },
  { time: '10:15:44', user: '林管理员', action: '新增角色', target: '临时质检', ip: '10.12.3.21', status: '成功' },
  { time: '09:52:03', user: '周计划', action: '申请权限', target: '出库协同-导出', ip: '10.12.8.55', status: '待审批' },
  { time: '09:31:18', user: '沈砚', action: '临时提权', target: '异常强制关闭', ip: '10.12.5.10', status: '已回收' },
  { time: '08:47:29', user: '林管理员', action: '禁用账号', target: 'wh_guest', ip: '10.12.3.21', status: '成功' },
  { time: '08:12:06', user: '系统', action: '自动回收', target: '临时权限×3', ip: '—', status: '成功' }
]

export const loginAudits = [
  { time: '10:28:01', user: '林管理员', account: 'admin', result: '成功', ip: '10.12.3.21', device: 'Chrome / Win', risk: '正常' },
  { time: '10:05:33', user: '未知', account: 'admin', result: '失败', ip: '203.88.12.44', device: 'Firefox / Linux', risk: '异常' },
  { time: '09:12:18', user: '周计划', account: 'pmc', result: '成功', ip: '10.12.8.55', device: 'Edge / Win', risk: '正常' },
  { time: '08:45:02', user: '沈砚', account: 'warehouse', result: '成功', ip: '10.12.5.10', device: 'Chrome / Win', risk: '正常' },
  { time: '08:22:41', user: '未知', account: 'warehouse', result: '失败', ip: '118.24.9.77', device: 'Unknown', risk: '异常' },
  { time: '07:55:10', user: '赵工', account: 'worker', result: '成功', ip: '10.12.9.31', device: 'PDA / Android', risk: '正常' },
  { time: '07:40:55', user: '韩库存', account: 'inventory', result: '成功', ip: '10.12.5.22', device: 'Chrome / Win', risk: '正常' }
]

export const integrationServices = [
  { name: 'ERP', status: '已连接', latency: 120, tone: 'ok' },
  { name: 'MES', status: '已连接', latency: 95, tone: 'ok' },
  { name: 'PLM', status: '已连接', latency: 180, tone: 'ok' },
  { name: 'OA', status: '已连接', latency: 210, tone: 'warn' },
  { name: 'AI 服务', status: '已连接', latency: 340, tone: 'ok' },
  { name: '短信服务', status: '已连接', latency: 88, tone: 'ok' }
]

export const topologyNodes = {
  app: { name: 'WMS 应用服务', status: 'ok' },
  spring: { name: 'Spring Boot', status: 'ok' },
  mysql: { name: 'MySQL', status: 'ok' },
  redis: { name: 'Redis', status: 'ok' },
  minio: { name: 'MinIO', status: 'warn' },
  gateway: { name: 'AI 服务网关', status: 'ok' },
  qwen: { name: 'Qwen', status: 'ok' },
  deepseek: { name: 'DeepSeek', status: 'ok' },
  gemini: { name: 'Gemini', status: 'ok' }
}

export const agentMonitors = [
  { name: 'PMC Agent', status: '正常', calls: 186, latency: '1.2s', tokens: '128K' },
  { name: '库存 Agent', status: '正常', calls: 142, latency: '0.9s', tokens: '96K' },
  { name: '质检 Agent', status: '忙碌', calls: 98, latency: '1.8s', tokens: '84K' },
  { name: '生产 Agent', status: '正常', calls: 76, latency: '1.1s', tokens: '52K' },
  { name: '通用助手', status: '正常', calls: 210, latency: '0.7s', tokens: '156K' }
]

export const aiOpsFeed = [
  { level: '告警', time: '10:26', text: '检测到数据库连接池使用率偏高（78%），建议关注慢查询。' },
  { level: '预警', time: '10:12', text: 'ERP 接口近 15 分钟平均响应 210ms，略高于基线。' },
  { level: '正常', time: '09:58', text: '全部 Agent 运行正常，Token 消耗处于预期区间。' },
  { level: '预警', time: '09:40', text: 'MinIO 对象存储磁盘使用率 72%，建议安排扩容评估。' },
  { level: '正常', time: '09:15', text: '昨夜自动备份已完成，校验通过。' }
]

export const messageCenter = [
  { id: 'M001', level: '告警', title: '数据库连接池偏高', time: '10:26', read: false, source: 'AI 运维' },
  { id: 'M002', level: '告警', title: '异常登录尝试（admin）', time: '10:05', read: false, source: '登录审计' },
  { id: 'M003', level: '预警', title: 'OA 接口响应变慢', time: '09:50', read: false, source: '接口监控' },
  { id: 'M004', level: '通知', title: '系统备份任务已完成', time: '09:15', read: true, source: '系统备份' },
  { id: 'M005', level: '通知', title: '临时权限已自动回收 ×3', time: '08:12', read: true, source: '权限审计' },
  { id: 'M006', level: '预警', title: 'MinIO 磁盘使用率 72%', time: '09:40', read: true, source: '系统配置' }
]

export const systemLogs = [
  { level: 'INFO', time: '10:30:12', text: '用户林管理员登录成功' },
  { level: 'WARN', time: '10:26:08', text: '数据库连接池使用率 78%' },
  { level: 'ERROR', time: '10:05:33', text: '异常登录失败：account=admin ip=203.88.12.44' },
  { level: 'INFO', time: '09:58:20', text: 'Agent 健康检查通过（12/12）' },
  { level: 'WARN', time: '09:50:11', text: 'OA 接口平均响应 210ms' },
  { level: 'INFO', time: '09:15:02', text: '夜间全量备份完成，校验通过' },
  { level: 'INFO', time: '08:45:02', text: '用户沈砚登录成功' },
  { level: 'ERROR', time: '08:22:41', text: '异常登录失败：account=warehouse ip=118.24.9.77' }
]

export const traceFlow = ['订单', '采购', '收货', '质检', '入库', '领料', '生产', '出库']

export const recentTraces = [
  { id: 'TR-20260710-018', keyword: 'SO-88421', stage: '领料', time: '10:18', status: '进行中' },
  { id: 'TR-20260710-017', keyword: 'BATCH-Q3921', stage: '质检', time: '09:55', status: '已完成' },
  { id: 'TR-20260710-016', keyword: 'MAT-A102', stage: '入库', time: '09:32', status: '已完成' },
  { id: 'TR-20260710-015', keyword: 'SO-88405', stage: '出库', time: '09:10', status: '已完成' },
  { id: 'TR-20260709-088', keyword: 'PO-2291', stage: '收货', time: '昨天 18:40', status: '已完成' }
]

export const healthScore = {
  score: 96,
  stars: 5,
  dims: [
    { label: '系统性能', value: 95 },
    { label: '数据安全', value: 98 },
    { label: '接口健康', value: 92 },
    { label: 'Agent 运行', value: 99 },
    { label: '日志完整性', value: 96 }
  ]
}

export const backupJobs = [
  { id: 'BK-240', type: '全量备份', target: 'MySQL + MinIO', started: '2026-07-10 02:00', finished: '02:48', size: '18.6 GB', status: '成功' },
  { id: 'BK-239', type: '增量备份', target: 'MySQL', started: '2026-07-09 02:00', finished: '02:12', size: '1.2 GB', status: '成功' },
  { id: 'BK-238', type: '全量备份', target: 'MySQL + MinIO', started: '2026-07-09 02:00', finished: '02:51', size: '18.4 GB', status: '成功' },
  { id: 'BK-237', type: '配置快照', target: '系统配置', started: '2026-07-08 23:00', finished: '23:01', size: '12 MB', status: '成功' },
  { id: 'BK-236', type: '增量备份', target: 'MySQL', started: '2026-07-08 02:00', finished: '02:09', size: '0.9 GB', status: '失败' }
]

export const configItems = [
  { key: '会话超时', value: '120 分钟', group: '安全' },
  { key: '密码策略', value: '强密码 + 90 天轮换', group: '安全' },
  { key: 'API 限流', value: '1200 req/min', group: '接口' },
  { key: 'Agent 并发', value: '16', group: 'AI' },
  { key: '日志保留', value: '90 天', group: '治理' },
  { key: '备份窗口', value: '每日 02:00', group: '治理' }
]
