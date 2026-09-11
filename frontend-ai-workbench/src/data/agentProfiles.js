const AGENT_PHOTO_BASE = '/agents'

export const agentLayers = [
  { id: 'all', label: '全部', desc: '15 位智能体' },
  { id: 'decision', label: '决策层', desc: '1 位 · 统筹调度' },
  { id: 'strategy', label: '策略层', desc: '4 位 · 流程编排' },
  { id: 'execution', label: '执行层', desc: '7 位 · 业务执行' },
  { id: 'auxiliary', label: '辅助层', desc: '3 位 · 基础支撑' }
]

export const agentProfiles = [
  {
    id: 'WmsAgentOrchestrator',
    label: '总控智能体',
    codeName: 'Orchestrator',
    layer: 'decision',
    level: 'L0',
    title: '多智能体总控调度专家',
    layerLabel: '决策层 · 任务编排与流程终审',
    summary: '接收任务后经 DeepSeek 理解与规划，再按能力目录链式调度领域智能体；业务写库由领域 Agent 基于真实数据完成。',
    avatar: '总',
    avatarColor: '#6b8f7a',
    photo: `${AGENT_PHOTO_BASE}/1.jpg`,
    className: 'WmsAgentOrchestrator',
    packagePath: 'com.upc.wms.agent.core',
    skills: ['任务理解(DeepSeek)', '任务类型识别', '执行链规划', '智能体调度', '步骤与日志记录', '报告汇总'],
    knowledgeTags: ['OrchestratorPlanningService', 'AgentCapabilityCatalog', 'AgentTaskType', 'AgentContext', 'DeepSeek'],
    knowledgeBase:
      '总控智能体是决策层中枢。启动时注册全部 Agent；收到任务后先由 OrchestratorPlanningService 调用 DeepSeek 理解用户目标、识别任务类型、判断业务模块并规划执行链（失败则规则回退）。随后进入 dispatchTask：按能力目录校验职责边界、按数据范围记录输入、执行当前 Agent、合并结构化产出、写入结论与数据库依据，再推进 nextAgent，直至完成或人工介入。库存/订单/质检等核心判断必须以数据库与系统规则为准，DeepSeek 不直接写业务单据。',
    resume: [
      ['2026-07', '接入 DeepSeek 任务理解与执行链规划'],
      ['2026-07', '为 15 位智能体配置能力/数据范围/返回格式契约'],
      ['架构', '规划与执行分离：Orchestrator 编排，领域 Agent 基于真实 DB 执行']
    ],
    taskTypes: ['全部任务类型的入口调度']
  },
  {
    id: 'OrderPlanAgent',
    label: '订单计划智能体',
    codeName: 'PlanMaster',
    layer: 'strategy',
    level: 'L1',
    title: '生产计划与领料编排专家',
    layerLabel: '策略层 · 计划转化与出库触发',
    summary: '将客户订单转化为生产计划，经齐套校验后再生成领料单；出库由仓管员单独触发。',
    avatar: '计',
    avatarColor: '#7a93a8',
    photo: `${AGENT_PHOTO_BASE}/2.jpg`,
    className: 'OrderPlanAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['生产计划解析', '领料单生成', '订单/计划关联', '出库流程触发'],
    knowledgeTags: ['PmcPlanService', 'OrderService', 'PmcRequisitionOrder', 'ORDER_PLAN_REQUISITION'],
    knowledgeBase:
      '订单计划智能体负责 ORDER_PLAN_REQUISITION 流程（客户订单线 PMC 段）。首节点根据已审核 orderId 或 planId 生成生产计划并将 planLines 写入上下文，交由 InventoryAgent 按「已入库+质检合格/放行+未冻结+可用>0」做齐套校验并输出缺料分型；齐套完成后再次进入本智能体，按齐套率生成完整/部分领料单或仅记录缺料预警，最终由 AuditAgent 收尾。出库执行不在 PMC 段内，由仓管员单独触发 REQUISITION_OUTBOUND。',
    resume: [
      ['流程', '客户订单线：审核 → 计划 → 齐套 → 领料'],
      ['协作', '→ InventoryAgent（齐套校验）→ OrderPlanAgent（领料单）→ AuditAgent']
    ],
    taskTypes: ['ORDER_PLAN_REQUISITION']
  },
  {
    id: 'InventoryAgent',
    label: '库存智能体',
    codeName: 'StockGuard',
    layer: 'strategy',
    level: 'L1',
    title: '库存控制与安全库存专家',
    layerLabel: '策略层 · 库存核对与预警',
    summary: 'WMS 核心智能体：冻结/解冻、安全库存检查，以及在各流程中核对库存余额与流水。',
    avatar: '库',
    avatarColor: '#8aa67a',
    photo: `${AGENT_PHOTO_BASE}/3.jpg`,
    className: 'InventoryAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['库存冻结/解冻', '安全库存检查', '库存余额核对', '预警记录生成', '流水追溯'],
    knowledgeTags: ['InventoryService', 'InvInventory', 'InvAlertRecord', 'InvFreezeRecord', 'inv_transaction'],
    knowledgeBase:
      '库存智能体既可作为流程首节点（INVENTORY_FREEZE / INVENTORY_UNFREEZE / SAFETY_STOCK_CHECK），也可作为收货/出库/盘点/移库流程的核对环节。所有库存变更均通过 InventoryService 在事务内完成并生成 inv_transaction 流水。PMC 齐套校验经 KitAvailabilityService：仅已入库、质检 QUALIFIED/RELEASED、库存 AVAILABLE、availableQty>0 计入可用，并输出真实缺料/质量未放行/质量异常/库位不可用四类分型。仓管出库段先校验可用库存，再按 FIFO 推荐批次与库位，交 OutboundAgent 生成出库单。',
    resume: [
      ['核心', 'WMS 库存域唯一智能体'],
      ['齐套', '已入库+质检放行+未冻结+可用>0'],
      ['出库', 'REQUISITION_OUTBOUND：库存校验 → 批次库位推荐 → OutboundAgent']
    ],
    taskTypes: ['INVENTORY_FREEZE', 'INVENTORY_UNFREEZE', 'SAFETY_STOCK_CHECK', 'RECEIPT_INSPECTION_INBOUND', 'ORDER_PLAN_REQUISITION', 'REQUISITION_OUTBOUND', 'STOCKTAKE_ADJUSTMENT', 'INVENTORY_TRANSFER']
  },
  {
    id: 'IntegrationAgent',
    label: '集成智能体',
    codeName: 'BridgeLink',
    layer: 'strategy',
    level: 'L1',
    title: 'ERP/MES 系统集成专家',
    layerLabel: '策略层 · 外部系统消息处理',
    summary: '处理 ERP/MES 等外部系统消息；在安全库存流程中可将预警推送给外部系统。',
    avatar: '集',
    avatarColor: '#9a8aad',
    photo: `${AGENT_PHOTO_BASE}/4.jpg`,
    className: 'IntegrationAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['外部消息接收', '消息状态管理', '安全库存预警推送', 'ERP/MES 对接'],
    knowledgeTags: ['IntegrationService', 'IntIntegrationMessage', 'INTEGRATION_MESSAGE_PROCESS'],
    knowledgeBase:
      '集成智能体支持 INTEGRATION_MESSAGE_PROCESS 与 SAFETY_STOCK_CHECK 两种任务类型。对于外部消息，调用 IntegrationService 处理入站报文并更新状态；对于安全库存预警，将 alerts 序列化后通过 saveOutboundMessage 推送给指定 systemId 的外部系统。',
    resume: [
      ['对接', 'ERP / MES 双向消息通道'],
      ['预警', '安全库存命中后自动推送外部系统']
    ],
    taskTypes: ['INTEGRATION_MESSAGE_PROCESS', 'SAFETY_STOCK_CHECK']
  },
  {
    id: 'AuditAgent',
    label: '审计智能体',
    codeName: 'TraceKeeper',
    layer: 'strategy',
    level: 'L1',
    title: '多智能体协作审计专家',
    layerLabel: '策略层 · 流程收尾与证据归档',
    summary: '记录整个智能体任务的关键业务操作，作为流程的收尾节点（nextAgent 为空）。',
    avatar: '审',
    avatarColor: '#b09a72',
    photo: `${AGENT_PHOTO_BASE}/5.jpg`,
    className: 'AuditAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['审计日志写入', '任务上下文归档', '流程收尾', '证据链完整性'],
    knowledgeTags: ['UserService', 'SysAuditLog', 'AgentDataUtils.toJson'],
    knowledgeBase:
      '审计智能体 support() 返回 true，可为任意流程收尾。执行时将 taskType、taskId 及完整 context.data 序列化写入 SysAuditLog，operationType 为任务类型，objectType 为 AGENT_TASK。审计失败不阻断主流程。返回 nextAgent 为空，标志任务流程完成。',
    resume: [
      ['定位', '所有业务流程的终态节点'],
      ['原则', '无证据不立论 — 全量上下文 JSON 归档']
    ],
    taskTypes: ['全部流程的收尾节点']
  },
  {
    id: 'ReceivingAgent',
    label: '收货智能体',
    codeName: 'ReceiptBot',
    layer: 'execution',
    level: 'L2',
    title: '供应商到货收货专家',
    layerLabel: '执行层 · 收货登记',
    summary: '负责供应商到货后的收货登记，登记完成后将流程交给质检智能体。',
    avatar: '收',
    avatarColor: '#7aab8f',
    photo: `${AGENT_PHOTO_BASE}/6.jpg`,
    className: 'ReceivingAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['到货单识别', '收货单创建', '收货明细生成', '批次号登记'],
    knowledgeTags: ['ReceiptService', 'RecReceiptOrder', 'RecReceiptLine', 'RECEIPT_INSPECTION_INBOUND'],
    knowledgeBase:
      '收货智能体是 RECEIPT_INSPECTION_INBOUND 流程的第一步。将 AgentContext.data 转换为 ReceiptCreateRequest，调用 ReceiptService.createReceipt() 创建收货单，提取 receiptLines 写入上下文，nextAgent 指向 QualityAgent。',
    resume: [
      ['流程', '收货 → 质检 → 入库 → 库存 → 审计'],
      ['输出', 'receiptId, receiptNo, receiptLines, warehouseId']
    ],
    taskTypes: ['RECEIPT_INSPECTION_INBOUND']
  },
  {
    id: 'QualityAgent',
    label: '质检智能体',
    codeName: 'QCInspector',
    layer: 'execution',
    level: 'L2',
    title: '到货质量检验专家',
    layerLabel: '执行层 · 质量判定',
    summary: '负责收货后的质量检验；有合格数量则触发入库，全部不合格则转审计。',
    avatar: '质',
    avatarColor: '#b07a92',
    photo: `${AGENT_PHOTO_BASE}/7.jpg`,
    className: 'QualityAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['质检规则匹配', '合格/不合格判定', '加严抽检', '质量问题登记'],
    knowledgeTags: ['QualityService', 'QuaInspectionOrder', 'QuaInspectionLine'],
    knowledgeBase:
      '质检智能体读取 receiptId 与 receiptLines，调用 QualityService 创建检验单并提交检验结果。默认将全部收货数量判为合格；也支持通过 data.inspection 逐行指定。存在 qualifiedLines 时 nextAgent 为 InboundAgent；全部不合格时生成质量问题并直接转 AuditAgent。',
    resume: [
      ['规则', '支持加严抽检与逐行合格/不合格指定'],
      ['分支', '合格 → 入库；全不合格 → 审计']
    ],
    taskTypes: ['RECEIPT_INSPECTION_INBOUND']
  },
  {
    id: 'InboundAgent',
    label: '入库智能体',
    codeName: 'ShelfMaster',
    layer: 'execution',
    level: 'L2',
    title: '入库上架与库位推荐专家',
    layerLabel: '执行层 · 上架确认',
    summary: '为质检合格物料创建入库单、推荐可用库位并确认上架。',
    avatar: '入',
    avatarColor: '#7899b5',
    photo: `${AGENT_PHOTO_BASE}/8.jpg`,
    className: 'InboundAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['入库单创建', '库位推荐', '上架确认', '库存增加触发'],
    knowledgeTags: ['InboundService', 'WarehouseService', 'WhLocation', 'InboundConfirmRequest'],
    knowledgeBase:
      '入库智能体读取 qualifiedLines，查询可用库位（WarehouseService.listAvailableLocations），创建入库单后逐行分配库位并调用 confirmInbound。库存增加与流水由 InboundService 在同一事务内通过 InventoryService 完成。无可用库位时返回 MANUAL_REQUIRED。',
    resume: [
      ['策略', '轮询分配可用库位并记录 recommendedLocations'],
      ['协作', '→ InventoryAgent 核对 → AuditAgent 收尾']
    ],
    taskTypes: ['RECEIPT_INSPECTION_INBOUND']
  },
  {
    id: 'OutboundAgent',
    label: '出库智能体',
    codeName: 'PickPro',
    layer: 'execution',
    level: 'L2',
    title: '领料出库与拣货复核专家',
    layerLabel: '执行层 · 出库执行',
    summary: '接收领料单，协同库存校验与批次库位推荐后生成出库单与出库明细（仓管出库段）。',
    avatar: '出',
    avatarColor: '#b58a7a',
    photo: `${AGENT_PHOTO_BASE}/9.jpg`,
    className: 'OutboundAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['领料单接收', '出库单生成', '出库明细生成', '拣货任务派发'],
    knowledgeTags: ['OutboundService', 'PmcPlanService', 'OutOrder', 'OutPickingTask', 'REQUISITION_OUTBOUND'],
    knowledgeBase:
      '出库智能体在 REQUISITION_OUTBOUND 中分三阶段执行：① 接收领料单并加载明细，交 InventoryAgent；② 库存校验与批次库位推荐完成后生成出库单；③ 按推荐结果生成出库明细与拣货任务，交 AuditAgent。仓管员分配拣货给生产工人；工人扫码领料与交接；仓管员复核与异常闭环。',
    resume: [
      ['流程', '领料单 → 出库单 → 分配拣货 → 工人扫码'],
      ['协作', 'OutboundAgent ⇄ InventoryAgent → AuditAgent']
    ],
    taskTypes: ['REQUISITION_OUTBOUND']
  },
  {
    id: 'StocktakeAgent',
    label: '盘点智能体',
    codeName: 'CountWise',
    layer: 'execution',
    level: 'L2',
    title: '盘点差异与调整专家',
    layerLabel: '执行层 · 盘点调整',
    summary: '创建盘点单并生成明细；有实盘数量则生成差异、调整单并修正库存。',
    avatar: '盘',
    avatarColor: '#94a872',
    photo: `${AGENT_PHOTO_BASE}/10.jpg`,
    className: 'StocktakeAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['盘点单创建', '实盘录入', '差异分析', '调整单审核', '库存修正'],
    knowledgeTags: ['StocktakeService', 'InvStocktakeOrder', 'InvAdjustmentOrder', 'STOCKTAKE_ADJUSTMENT'],
    knowledgeBase:
      '盘点智能体创建盘点单并生成盘点明细。若上下文已提供 countLines（实盘数量），则依次生成差异、确认差异、创建并审核调整单，由 InventoryService 修正库存后交 InventoryAgent 核对；否则返回 MANUAL_REQUIRED 等待人工录入实盘。',
    resume: [
      ['分支', '有实盘 → 自动调整；无实盘 → 人工介入'],
      ['输出', 'stocktakeId, adjustmentId, affectedItems']
    ],
    taskTypes: ['STOCKTAKE_ADJUSTMENT']
  },
  {
    id: 'TransferAgent',
    label: '移库智能体',
    codeName: 'MoveIt',
    layer: 'execution',
    level: 'L2',
    title: '库位移库执行专家',
    layerLabel: '执行层 · 移库操作',
    summary: '创建移库单并确认移库，源库位扣减、目标库位增加由 TransferService 事务内完成。',
    avatar: '移',
    avatarColor: '#8299b0',
    photo: `${AGENT_PHOTO_BASE}/11.jpg`,
    className: 'TransferAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['移库单创建', '源/目标库位校验', '移库确认', '移库流水生成'],
    knowledgeTags: ['TransferService', 'WhTransferOrder', 'WhTransferLine', 'INVENTORY_TRANSFER'],
    knowledgeBase:
      '移库智能体将 context.data 转换为 TransferCreateRequest，调用 TransferService 创建并确认移库。源库位扣减、目标库位增加与移库流水在同一事务内通过 InventoryService 完成，随后交 InventoryAgent 核对。',
    resume: [
      ['流程', 'TransferAgent → InventoryAgent → AuditAgent'],
      ['输出', 'transferId, transferNo, transferLines']
    ],
    taskTypes: ['INVENTORY_TRANSFER']
  },
  {
    id: 'SmartWarehouseAgent',
    label: '智能仓储智能体',
    codeName: 'IoTWatcher',
    layer: 'execution',
    level: 'L2',
    title: 'IoT/AGV/RFID 事件处理专家',
    layerLabel: '执行层 · 智能设备事件',
    summary: '处理条码/RFID/AGV/IoT 等智能仓储扩展事件，登记设备事件供后续分派。',
    avatar: '智',
    avatarColor: '#72a8a8',
    photo: `${AGENT_PHOTO_BASE}/12.jpg`,
    className: 'SmartWarehouseAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['IoT 事件接收', '设备事件登记', '条码/RFID 解析', 'AGV 调度预留'],
    knowledgeTags: ['SmartWarehouseService', 'IotEvent', 'SMART_WAREHOUSE_EVENT_PROCESS'],
    knowledgeBase:
      '智能仓储智能体处理 SMART_WAREHOUSE_EVENT_PROCESS 任务。从 context 读取 deviceCode、eventType、locationId、payload，构造 IotEvent 实体并调用 SmartWarehouseService.saveIotEvent 登记。后续可扩展分派到库存/移库等智能体。',
    resume: [
      ['阶段', '第一阶段：IoT 设备事件接收与登记'],
      ['扩展', '预留 AGV / RFID / 条码事件分派能力']
    ],
    taskTypes: ['SMART_WAREHOUSE_EVENT_PROCESS']
  },
  {
    id: 'UserAgent',
    label: '用户权限智能体',
    codeName: 'AuthGuard',
    layer: 'auxiliary',
    level: 'L3',
    title: '用户与权限管理专家',
    layerLabel: '辅助层 · 权限校验',
    summary: '负责用户与权限相关操作，不参与自动业务流程编排，可按名称直接调用。',
    avatar: '权',
    avatarColor: '#a8947a',
    photo: `${AGENT_PHOTO_BASE}/13.jpg`,
    className: 'UserAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['用户列表查询', '权限校验', '角色授权检查', '审计日志记录'],
    knowledgeTags: ['UserService', 'SysRole', 'SysPermission'],
    knowledgeBase:
      '用户权限智能体 support() 返回 false，不参与 WmsAgentOrchestrator 的自动流程编排。可由其他智能体或管理接口按 AgentNames.USER 直接调用，返回当前系统用户数量等信息，供权限审计场景使用。',
    resume: [
      ['定位', '辅助智能体，按需调用'],
      ['场景', '权限审计、越权操作巡检']
    ],
    taskTypes: ['按需调用，无固定流程节点']
  },
  {
    id: 'MasterDataAgent',
    label: '基础资料智能体',
    codeName: 'DataValidator',
    layer: 'auxiliary',
    level: 'L3',
    title: '物料与供应商校验专家',
    layerLabel: '辅助层 · 主数据校验',
    summary: '负责物料、供应商等基础资料校验，为其他智能体提供合法性验证。',
    avatar: '资',
    avatarColor: '#94a082',
    photo: `${AGENT_PHOTO_BASE}/14.jpg`,
    className: 'MasterDataAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['物料存在性校验', '供应商合法性', '基础资料查询', '编码规范检查'],
    knowledgeTags: ['MasterDataService', 'MdItem', 'MdSupplier'],
    knowledgeBase:
      '基础资料智能体 support() 返回 false，作为辅助智能体存在。读取 context 中的 itemId，调用 MasterDataService.getItemById 校验物料是否存在，返回 itemCode、itemName。物料不存在时返回 failed 结果。',
    resume: [
      ['定位', '辅助智能体，按需调用'],
      ['能力', '物料/供应商合法性前置校验']
    ],
    taskTypes: ['按需调用，无固定流程节点']
  },
  {
    id: 'WarehouseAgent',
    label: '仓库库位智能体',
    codeName: 'LocFinder',
    layer: 'auxiliary',
    level: 'L3',
    title: '仓库与库位管理专家',
    layerLabel: '辅助层 · 库位推荐',
    summary: '负责仓库与库位相关能力，可为入库/移库智能体提供可用库位推荐。',
    avatar: '位',
    avatarColor: '#7a9e92',
    photo: `${AGENT_PHOTO_BASE}/15.jpg`,
    className: 'WarehouseAgent',
    packagePath: 'com.upc.wms.agent.domain',
    skills: ['可用库位查询', '库位容量评估', '仓库结构解析', '库位推荐'],
    knowledgeTags: ['WarehouseService', 'WhLocation', 'WhWarehouse'],
    knowledgeBase:
      '仓库库位智能体 support() 返回 false，作为辅助智能体。根据 warehouseId 调用 WarehouseService.listAvailableLocations 查询可用库位数量，供入库/移库场景的前置校验或库位推荐使用。',
    resume: [
      ['定位', '辅助智能体，按需调用'],
      ['协作', '为 InboundAgent / TransferAgent 提供库位数据']
    ],
    taskTypes: ['按需调用，无固定流程节点']
  }
]

const ALIAS_TO_ID = {
  总控智能体: 'WmsAgentOrchestrator',
  计划智能体: 'OrderPlanAgent',
  订单计划智能体: 'OrderPlanAgent',
  库存智能体: 'InventoryAgent',
  集成智能体: 'IntegrationAgent',
  审计智能体: 'AuditAgent',
  收货智能体: 'ReceivingAgent',
  质检智能体: 'QualityAgent',
  入库智能体: 'InboundAgent',
  出库智能体: 'OutboundAgent',
  盘点智能体: 'StocktakeAgent',
  移库智能体: 'TransferAgent',
  智能仓储智能体: 'SmartWarehouseAgent',
  用户权限智能体: 'UserAgent',
  权限智能体: 'UserAgent',
  基础资料智能体: 'MasterDataAgent',
  仓库库位智能体: 'WarehouseAgent',
  领料智能体: 'OrderPlanAgent',
  交接智能体: 'OutboundAgent',
  '订单计划·生成计划': 'OrderPlanAgent',
  '订单计划·生成领料单': 'OrderPlanAgent',
  '库存·齐套校验': 'InventoryAgent',
  'OutboundAgent·接收领料单': 'OutboundAgent',
  'InventoryAgent·库存校验': 'InventoryAgent',
  'InventoryAgent·批次库位推荐': 'InventoryAgent',
  'OutboundAgent·生成出库单': 'OutboundAgent',
  'OutboundAgent·生成出库明细': 'OutboundAgent',
  'AuditAgent·审计记录': 'AuditAgent',
  '出库智能体·接收领料单': 'OutboundAgent',
  '库存智能体·库存校验': 'InventoryAgent',
  '库存智能体·批次库位推荐': 'InventoryAgent',
  '出库智能体·生成出库单': 'OutboundAgent',
  '出库智能体·生成出库明细': 'OutboundAgent',
  OutboundAgent: 'OutboundAgent',
  InventoryAgent: 'InventoryAgent',
  AuditAgent: 'AuditAgent'
}

const photoById = Object.fromEntries(agentProfiles.map((agent) => [agent.id, agent.photo]))
const profileById = Object.fromEntries(agentProfiles.map((agent) => [agent.id, agent]))

export function labelToAgentName(label) {
  if (!label) return null
  const normalized = String(label).trim()
  if (ALIAS_TO_ID[normalized]) return ALIAS_TO_ID[normalized]
  if (profileById[normalized]) return normalized
  const matched = agentProfiles.find(
    (agent) => agent.label === normalized || agent.className === normalized
  )
  return matched?.id ?? null
}

export function resolveAgentPhoto(key) {
  if (!key) return photoById.WmsAgentOrchestrator

  const normalized = String(key).trim()
  const aliasId = ALIAS_TO_ID[normalized]
  if (aliasId && photoById[aliasId]) return photoById[aliasId]
  if (photoById[normalized]) return photoById[normalized]

  const byProfile = agentProfiles.find(
    (agent) =>
      agent.id === normalized ||
      agent.label === normalized ||
      agent.className === normalized ||
      agent.codeName === normalized ||
      normalized.includes(agent.className) ||
      normalized.includes(agent.label)
  )
  if (byProfile) return byProfile.photo

  return photoById.WmsAgentOrchestrator
}

export function findAgentProfile(key) {
  if (!key) return null
  const normalized = String(key).trim()
  const aliasId = ALIAS_TO_ID[normalized]
  if (aliasId && profileById[aliasId]) return profileById[aliasId]
  if (profileById[normalized]) return profileById[normalized]
  return (
    agentProfiles.find(
      (agent) =>
        agent.id === normalized ||
        agent.label === normalized ||
        agent.className === normalized ||
        agent.codeName === normalized
    ) ?? null
  )
}
