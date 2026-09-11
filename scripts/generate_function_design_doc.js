const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, ImageRun,
  Header, Footer, AlignmentType, HeadingLevel, BorderStyle, WidthType,
  ShadingType, PageNumber, PageBreak, TableOfContents, LevelFormat
} = require('docx');

const ROOT = path.resolve(__dirname, '..');
const OUT_DIR = path.join(ROOT, 'docs');
const ASSET_DIR = path.join(OUT_DIR, '功能设计文档插图');
fs.mkdirSync(ASSET_DIR, { recursive: true });

const C = { navy:'000000', blue:'000000', light:'FFFFFF', pale:'FFFFFF', teal:'000000', green:'000000', orange:'000000', red:'000000', gray:'000000', line:'000000', white:'FFFFFF', black:'000000' };
const contentWidth = 8845;
const border = { style: BorderStyle.SINGLE, size: 8, color: C.line };
const borders = { top:border, bottom:border, left:border, right:border, insideHorizontal:border, insideVertical:border };

function esc(s){ return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
async function diagram(name, title, columns, rows, arrows=[]) {
  const w=1400, h=Math.max(460, 180+rows.length*115);
  const colW=(w-100)/columns.length;
  let svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}"><rect width="100%" height="100%" fill="#F8FBFE"/><style>text{font-family:'Microsoft YaHei','Noto Sans CJK SC',Arial}.t{font-size:30px;font-weight:700;fill:#17365D}.h{font-size:21px;font-weight:700;fill:#fff}.n{font-size:18px;fill:#17365D}.s{font-size:15px;fill:#555}.a{stroke:#2F75B5;stroke-width:4;fill:none;marker-end:url(#m)}</style><defs><marker id="m" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="#2F75B5"/></marker></defs><text x="50" y="50" class="t">${esc(title)}</text>`;
  columns.forEach((c,i)=>{ const x=50+i*colW; svg+=`<rect x="${x}" y="80" rx="10" width="${colW-18}" height="48" fill="#2F75B5"/><text x="${x+(colW-18)/2}" y="112" text-anchor="middle" class="h">${esc(c)}</text>`; });
  rows.forEach((r,ri)=>r.forEach((cell,ci)=>{ if(!cell)return; const x=55+ci*colW,y=150+ri*115; svg+=`<rect x="${x}" y="${y}" rx="12" width="${colW-28}" height="78" fill="#fff" stroke="#8EB6D8" stroke-width="2"/><text x="${x+(colW-28)/2}" y="${y+33}" text-anchor="middle" class="n">${esc(cell[0])}</text><text x="${x+(colW-28)/2}" y="${y+59}" text-anchor="middle" class="s">${esc(cell[1]||'')}</text>`; }));
  arrows.forEach(a=>{ const [c1,r1,c2,r2]=a; const x1=55+c1*colW+(colW-28), y1=150+r1*115+39, x2=55+c2*colW, y2=150+r2*115+39; svg+=`<path d="M${x1} ${y1} C${(x1+x2)/2} ${y1},${(x1+x2)/2} ${y2},${x2} ${y2}" class="a"/>`; });
  svg+='</svg>';
  const p=path.join(ASSET_DIR, name+'.svg');
  fs.writeFileSync(p, svg, 'utf8'); return p;
}

function run(text, opt={}) { return new TextRun({ text:String(text), font:opt.font||{eastAsia:'FangSong_GB2312',ascii:'Times New Roman',hAnsi:'Times New Roman',cs:'Times New Roman'}, size:opt.size||28, bold:!!opt.bold, color:C.black, italics:!!opt.italics }); }
function p(text='', opt={}) { return new Paragraph({ alignment:opt.align||AlignmentType.JUSTIFIED, spacing:{ line:opt.line||560, lineRule:'exact', before:opt.before||0, after:opt.after??0 }, indent:opt.indent===false?undefined:{ firstLine:560 }, keepNext:!!opt.keepNext, children:[run(text,opt)] }); }
function h(text, level=1, pageBreakBefore=false) { const fonts=[null,{eastAsia:'SimHei',ascii:'Times New Roman',hAnsi:'Times New Roman'},{eastAsia:'KaiTi_GB2312',ascii:'Times New Roman',hAnsi:'Times New Roman'},{eastAsia:'FangSong_GB2312',ascii:'Times New Roman',hAnsi:'Times New Roman'},{eastAsia:'FangSong_GB2312',ascii:'Times New Roman',hAnsi:'Times New Roman'}]; return new Paragraph({ heading:[null,HeadingLevel.HEADING_1,HeadingLevel.HEADING_2,HeadingLevel.HEADING_3,HeadingLevel.HEADING_4][level], pageBreakBefore, spacing:{line:640,lineRule:'exact',before:0,after:0}, children:[run(text,{bold:level<=2,size:28,font:fonts[level]})] }); }
function bullet(text, level=0){ return new Paragraph({ numbering:{reference:'bullet-list',level}, spacing:{line:330,after:70}, children:[run(text)] }); }
function pageBreak(){ return new Paragraph({ children:[new PageBreak()] }); }
const transparentPng = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=','base64');
function img(file, caption, width=650, height=320){ const jpg=file.replace(/\.svg$/i,'.jpg'); return [new Paragraph({alignment:AlignmentType.CENTER,spacing:{line:240,lineRule:'auto',before:0,after:0},children:[new ImageRun({type:'jpg',data:fs.readFileSync(jpg),transformation:{width,height},altText:{title:caption,description:caption,name:caption}})]}),new Paragraph({alignment:AlignmentType.CENTER,spacing:{line:560,lineRule:'exact',after:0},children:[run(caption,{size:24})]})]; }
function cell(text,width,header=false,last=false){ const none={style:BorderStyle.NONE,size:0,color:'FFFFFF'}; const top=header?border:none, bottom=(header||last)?border:none; return new TableCell({width:{size:width,type:WidthType.DXA},borders:{top,bottom,left:none,right:none,insideHorizontal:none,insideVertical:none},margins:{top:60,bottom:60,left:80,right:80},children:[new Paragraph({spacing:{line:420,lineRule:'exact',after:0},children:[run(text,{size:24,bold:header})]})]}); }
function table(headers, rows, widths){ const total=widths.reduce((a,b)=>a+b,0); const scaled=widths.map(x=>Math.floor(x*contentWidth/total)); scaled[scaled.length-1]+=contentWidth-scaled.reduce((a,b)=>a+b,0); return new Table({width:{size:contentWidth,type:WidthType.DXA},columnWidths:scaled,borders:{top:border,bottom:border,left:{style:BorderStyle.NONE,size:0,color:'FFFFFF'},right:{style:BorderStyle.NONE,size:0,color:'FFFFFF'},insideHorizontal:{style:BorderStyle.NONE,size:0,color:'FFFFFF'},insideVertical:{style:BorderStyle.NONE,size:0,color:'FFFFFF'}},rows:[new TableRow({tableHeader:true,children:headers.map((x,i)=>cell(x,scaled[i],true,false))}),...rows.map((r,ri)=>new TableRow({children:r.map((x,i)=>cell(x,scaled[i],false,ri===rows.length-1))}))]}); }
function field(label, value){ return new Paragraph({spacing:{line:340,after:85},indent:{left:0},children:[run(label+'：',{bold:true,color:C.navy}),run(value)]}); }

const modules=[
 {name:'入库管理',goal:'建立采购到货至合格物料上架的闭环，确保数量、质量、批次、库位和库存账一致。',agent:'ReceivingAgent、QualityAgent、InboundAgent、WarehouseAgent、InventoryAgent、AuditAgent', funcs:[
  ['采购到货与收货登记','采购订单/ASN、供应商送货单、物料标签','收货单、待检任务','RecReceiptOrder、RecReceiptLine、MdSupplier、MdItem','校验供应商、订单、物料、批次和到货数量；生成收货单号；记录包装及外观初检；重复送货单拦截。'],
  ['来料质量检验','收货单、检验标准、抽样规则、实测值与图片','检验单、合格/不合格判定、质量异常','QuaInspectionOrder、QuaInspectionLine、QuaInspectStandard、QuaQualityIssue','按物料/供应商匹配标准；支持尺寸、外观、性能和图像比对；不合格批次隔离并禁止入库。'],
  ['入库单生成','合格收货明细、批次、数量、质量放行信息','入库单及上架明细','InInboundOrder、InInboundLine、MdBatch','仅接收已放行数量；按收货行拆分批次；建立收货—检验—入库引用链；状态初始化为待上架。'],
  ['智能库位分配','物料属性、尺寸重量、危险/防爆要求、空闲库容、周转频率','候选库位、评分及推荐理由','WhWarehouse、WhZone、WhLocation、InvInventory','硬约束先过滤，再按同品聚集、路径、容量、ABC 分类和先进先出便利性评分；人工确认后锁定。'],
  ['上架确认','入库单、目标库位、PDA 扫码、实际上架数量','库存增加、库存流水、上架完成凭证','InvInventory、InvTransaction、BarcodeLabel','扫描物料和库位双校验；使用幂等键防重复；事务内更新库存和流水；差异转异常处理。'] ]},
 {name:'出库管理',goal:'完成申请、审核、分配、拣选、复核和交接，确保正确物料、正确批次、正确数量交付。',agent:'OutboundAgent、InventoryAgent、SmartWarehouseAgent、WorkerFeedbackAgent、AuditAgent', funcs:[
  ['出库申请与审核','领料单/销售出库需求、用途、需求时间、明细','已审核出库需求','PmcRequisitionOrder、PmcRequisitionLine、OutOrder','校验来源单据和权限；关键物料或超量需求进入审批；驳回需记录原因。'],
  ['库存分配与拣货任务生成','审核需求、可用库存、冻结量、质量状态、批次效期','分配方案、拣货任务与路线','OutOrderLine、OutPickingTask、OutPickingLine、InvInventory','只分配合格且未冻结库存；默认 FIFO/FEFO；减少跨区与拆零；生成可执行库位序列。'],
  ['PDA 扫码拣货','任务号、人员、物料码、批次码、库位码、数量','扫描结果、任务进度、异常记录','OutPickingTask、OutPickingLine、WorkerScanRecord','校验任务归属、扫描顺序、批次和数量；离线补传需序列号去重；错料立即声光/界面提示。'],
  ['出库复核','已拣物料、容器、批次、数量、复核员','复核记录、差异处置','OutReviewTask、OutReviewLine、OutReviewException','复核员与拣货员职责分离；差异禁止放行；支持少拣、错拣、破损、标签异常分类。'],
  ['发货/备料区交接确认','复核通过明细、交接对象、备料区、签收信息','出库完成、库存扣减、交接凭证','OutOrder、InvTransaction、WorkerNotification','事务内扣减占用和实物库存；生产领料进入备料区并通知工人；销售发货记录承运信息。'] ]},
 {name:'库存管理',goal:'提供实时、可追溯、可控制的库存台账，支撑齐套、质量隔离、盘点和补货决策。',agent:'InventoryAgent、StocktakeAgent、TransferAgent、WarehouseAgent、IntegrationAgent', funcs:[
  ['库存查询与批次追溯','物料、仓库、库区、库位、批次、质量/冻结状态','多维库存余额与事件链','InvInventory、MdBatch、InvTransaction','按现存、可用、占用、冻结口径统一查询；批次串联收货、检验、入库、移库、领料和退料。'],
  ['库存盘点','盘点范围、盘点方式、账面快照、实盘数','盘点单、差异单、准确率','InvStocktakeOrder、InvStocktakeLine、InvStocktakeDifference','冻结盘点范围快照；支持明盘/盲盘；复盘后确认差异；并发出入库按业务规则控制。'],
  ['库存调整','差异来源、调整原因、审批意见','调整单、库存流水、审计记录','InvAdjustmentOrder、InvAdjustmentLine、InvTransaction','盘盈盘亏必须引用差异或异常；阈值分级审批；禁止直接修改库存余额。'],
  ['移库与库位管理','源/目标库位、物料批次、数量、库位状态','移库单、位置变化记录','WhTransferOrder、WhTransferLine、WhLocation','校验目标容量与属性；源库存锁定；扫码出库位和入库位；失败可补偿。'],
  ['安全库存与补货预警','上下限、消耗速度、在途量、计划需求','预警、补货建议、协同通知','InvSafetyStockRule、InvAlertRecord、PmcCoordinationNotice','静态阈值结合计划净需求和交期；预警去重、升级、关闭；建议不直接替代采购审批。'],
  ['库存冻结/解冻','批次、数量、原因、质量异常或审计指令','冻结记录、可用量变化','InvFreezeRecord、InvInventory、QuaQualityIssue','按批次/库位冻结；冻结量不得超过现存；解冻要求问题关闭或授权审批；全程留痕。'] ]},
 {name:'生产领料管理',goal:'打通生产计划、齐套分析、仓库备料和现场领用，降低停线与错领风险。',agent:'OrderPlanAgent、InventoryAgent、OutboundAgent、SmartWarehouseAgent、WorkerFeedbackAgent', funcs:[
  ['生产工单接收','ERP/MES 工单、产品、数量、日期、版本','本地生产计划/工单','PmcProductionPlan、PmcProductionPlanLine、IntIntegrationMessage','验证外部单号、BOM 版本和时间；按消息幂等键接收；失败进入重试与人工补偿。'],
  ['物料需求与齐套分析','工单、BOM、损耗率、库存质量状态、在途量','净需求、齐套率、短缺分类','MdBomHeader、MdBomLine、InvInventory','需求量=计划量×单位用量×损耗；区分真实短缺、待检、质量异常、冻结和库位不可用。'],
  ['领料申请','齐套结论、工单、物料需求、领料时间','领料单及审批状态','PmcRequisitionOrder、PmcRequisitionLine','齐套满足时按计划生成；替代料或超额领料进入审批；保留工单/BOM 版本快照。'],
  ['配料与领料执行','领料单、推荐批次库位、PDA 扫码、备料区','已配套物料、交接通知','OutPickingTask、OutPickingLine、WorkerNotification','仓库按批次库位拣选复核；按工单/工序装入容器；备料区扫描交接，避免跨工单混料。'],
  ['补料与退料','工单、缺料/损耗原因、现场数量、退料质量状态','补料单、退料入库单、异常闭环','WorkerReplenishRequest、WorkerException、InvTransaction','补料需关联原工单和原因；超阈值通知 PMC；退料重新判定质量和批次后入库。'] ]},
 {name:'质量管理',goal:'覆盖来料、仓储及领用环节的质量控制，实现不合格品隔离、处置与全链追溯。',agent:'QualityAgent、InventoryAgent、ReceivingAgent、AuditAgent', funcs:[
  ['检验标准管理','物料类别、检验项目、上下限、抽样方案、版本','生效检验标准','QuaInspectItem、QuaInspectStandard、QuaInspectStandardLine','标准版本化；新版本审批后生效；历史检验引用原版本；停用标准不得用于新任务。'],
  ['来料/过程检验执行','待检任务、抽样数、实测值、图片','检验结果与判定','QuaInspectionOrder、QuaInspectionLine','自动带出标准；逐项判定；关键项失败触发整批不合格；支持视觉检测服务的比对结果作为证据。'],
  ['质量异常与缺陷记录','不合格项、缺陷类型、严重度、责任方','质量问题单、隔离/冻结动作','QuaQualityIssue、InvFreezeRecord','异常自动关联供应商、批次和检验；严重缺陷先隔离后分析；状态为开放、处置中、已关闭。'],
  ['不合格品处置','问题单、评审意见、数量、责任部门','退货、返修、让步接收或报废结果','QuaReturnOrder、QuaReturnLine、QuaQualityIssue','按权限选择处置；让步接收需特别授权；返修后必须复检；处置数量守恒。'],
  ['质量追溯与趋势分析','批次/物料/供应商/缺陷/时间范围','追溯图、趋势、供应商质量指标','QuaInspectionOrder、QuaQualityIssue、InvTransaction','反向追溯来源，正向追溯去向；聚合一次合格率、缺陷 Pareto、供应商批次不良率。'] ]},
 {name:'计划协同管理',goal:'把客户订单、生产计划、物料准备、仓库执行与异常协同统一到可跟踪的计划链。',agent:'OrderPlanAgent、InventoryAgent、IntegrationAgent、AuditAgent', funcs:[
  ['订单与 PMC 计划','客户订单、交期、优先级、产能约束','生产计划与计划行','OrdCustomerOrder、OrdCustomerOrderLine、PmcProductionPlan','订单审核后转计划；维护来源关系；计划版本调整保留前后差异。'],
  ['物料需求计划','生产计划、BOM、库存、在途、已分配量','物料需求和缺口清单','MdBomLine、InvInventory、PmcProductionPlanLine','按计划日期滚动计算净需求；对关键长周期物料提前预警；输出可解释计算口径。'],
  ['生产准备与协同通知','短缺、待检、备料进度、责任角色','催检、补货、备料协同通知','PmcCoordinationNotice、InvAlertRecord','按异常类型路由质检、仓库或库存人员；通知有已读、处理、关闭状态；超时升级。'],
  ['计划执行跟踪与调整','计划节点、领料/备料完成度、异常、实际进度','时间线、偏差、调整建议','PmcProductionPlan、PmcRequisitionOrder、OutOrder','汇集计划—领料—出库—交接事件；识别交期风险；调整计划需重新齐套分析。'] ]},
 {name:'数据分析与驾驶舱',goal:'面向不同角色提供可信指标、下钻明细和可执行的异常入口。',agent:'InventoryAgent、OrderPlanAgent、QualityAgent、AuditAgent、Orchestrator Agent', funcs:[
  ['库存分析','库存余额、周转、库龄、冻结和预警数据','周转率、呆滞料、库龄结构、预警清单','InvInventory、InvTransaction、InvAlertRecord','统一时间和组织口径；支持仓库/物料/批次下钻；指标链接到业务单据证据。'],
  ['入出库与作业效率统计','收货、上架、拣货、复核、出库事件','吞吐量、及时率、人效、异常率','RecReceiptOrder、InInboundOrder、OutPickingTask、OutOrder','按创建、完成和作业时间计算；排除撤销单；展示趋势和瓶颈环节。'],
  ['库位利用率与热力图','库位容量、占用、访问频次','容量/库位利用率、冷热区','WhLocation、InvInventory、WorkerScanRecord','区分物理占用与可用容量；按访问频次识别热区；为库位优化提供建议。'],
  ['生产物料与齐套分析','计划需求、可用库存、待检/冻结和领料进度','齐套率、短缺类型、停线风险','PmcProductionPlan、PmcRequisitionOrder、InvInventory','齐套率按可放行数量计算；展示短缺责任链和预计解决时间。'],
  ['质量趋势与智能报告','检验、缺陷、处置和供应商数据','趋势、Pareto、风险摘要、建议','QuaInspectionOrder、QuaQualityIssue','统计一次合格率和缺陷分布；Agent 生成摘要必须引用真实指标与单据，不得改写原始数据。'] ]}
];

const roles=[
 ['系统管理员','用户、角色、权限、登录审计、接口与 Agent 监控、系统配置、日志和备份','只配置和监督，不越权代替业务岗位完成质量放行或库存调整。'],
 ['PMC计划员','订单审核、生产计划、BOM 需求、齐套分析、领料单和协同通知','可调整计划与提交领料，不直接执行仓库出库。'],
 ['仓管员','收货、上架、出库、拣货、复核、异常和备料区交接','可执行实物作业，不得自行批准重大库存调整。'],
 ['库存管理员','库存控制、冻结/解冻、盘点、差异、调整、移库与安全库存','库存调整按阈值审批，质量冻结不得无授权解除。'],
 ['质检员','待检任务、检验执行、标准、质量异常、不合格处置和追溯','检验与处置分权；关键让步接收要求更高权限。'],
 ['生产工人','今日任务、领料交接、扫码确认、补料、退料、工序反馈和完工','只访问本人/班组任务，不可更改库存台账。']
];

const agents=[
 ['WmsAgentOrchestrator','总控/决策','识别意图、选择任务类型、生成执行链、聚合结果；本身不直接写业务数据。'],
 ['OrderPlanAgent','策略','订单转计划、BOM 需求、齐套判断和领料方案。'],['InventoryAgent','策略','可用库存、质量/冻结约束、批次分配、安全库存。'],
 ['IntegrationAgent','策略','ERP/MES 消息校验、幂等、重试和结果回传。'],['AuditAgent','策略','全过程审计、证据链和越权风险检查。'],
 ['ReceivingAgent','执行','ASN/采购到货转收货单。'],['QualityAgent','执行','检验、质量判定、异常处置建议。'],['InboundAgent','执行','合格收货转入库和上架明细。'],
 ['OutboundAgent','执行','领料/出库单、批次分配和拣货任务。'],['StocktakeAgent','执行','盘点、差异和调整建议。'],['TransferAgent','执行','移库校验与执行。'],
 ['SmartWarehouseAgent','执行','条码/RFID/AGV/IoT 事件和 PDA 扫描。'],['WorkerFeedbackAgent','执行','现场异常、缺料与反馈闭环。'],
 ['UserAgent','辅助','用户权限只读分析。'],['MasterDataAgent','辅助','物料、供应商等基础资料校验。'],['WarehouseAgent','辅助','仓库、库区、库位可用性与推荐。']
];

async function main(){
 const arch=await diagram('01-总体功能架构','传统 WMS + AI 智能增强 + Multi-Agent 协同融合架构',['交互与角色','传统 WMS 业务层','AI 智能增强层','协同与治理层'],[[['管理端 / PDA','六类业务角色'],['入出库 / 库存','生产领料 / 质量'],['预测·推荐·问答','异常识别·报告'],['总控编排','权限·审计·证据']], [['驾驶舱','指标下钻'],['计划协同 / 主数据','接口与消息'],['规则 + LLM','降级与人工确认'],['业务 Agent','任务·步骤·日志']]],[[0,0,1,0],[1,0,2,0],[2,0,3,0]]);
 const inbound=await diagram('02-智能入库流程','智能入库业务流程',['业务来源','仓储/质量执行','Agent 增强','结果'],[[['采购订单/ASN','供应商到货'],['收货登记','数量与标签核验'],['ReceivingAgent','单据一致性检查'],['收货单','进入待检']], [['检验任务','标准/抽样'],['质检执行','实测与图像证据'],['QualityAgent','风险与判定辅助'],['质量放行','不合格隔离']], [['合格明细','批次属性'],['上架扫码','物料+库位双校验'],['WarehouseAgent','候选库位评分'],['库存与流水','审计证据链']]],[[0,0,1,0],[1,0,2,0],[2,0,3,0],[0,1,1,1],[1,1,2,1],[2,1,3,1],[0,2,1,2],[1,2,2,2],[2,2,3,2]]);
 const picking=await diagram('03-智能生产领料流程','计划驱动的智能生产领料流程',['PMC','协同决策','仓库执行','生产现场'],[[['生产计划','订单/BOM/交期'],['OrderPlanAgent','需求分解'],['待处理领料单','仓管接收'],['备料区领料','工人确认']], [['计划调整','重算齐套'],['InventoryAgent','库存与质量约束'],['批次库位拣选','PDA 扫码'],['补料/退料','异常反馈']], [['协同通知','责任与时限'],['Orchestrator','汇总结论'],['复核与交接','库存流水'],['工序执行','完工反馈']]],[[0,0,1,0],[1,0,2,0],[2,0,3,0],[0,1,1,1],[1,1,2,1],[2,1,3,1]]);
 const agentArch=await diagram('04-Multi-Agent协同','Multi-Agent 分层协同与业务服务调用',['决策层','策略层','执行层','受控资源'],[[['Orchestrator','规划/路由/汇总'],['计划·库存·集成·审计','约束与策略'],['收货·质检·入出库','盘点·移库·现场'],['Spring Service','事务/校验/权限']], [['任务状态机','task/step/log'],['能力目录','数据范围/写权限'],['工具调用','结构化输入输出'],['MySQL / Redis','知识库/外部系统']]],[[0,0,1,0],[1,0,2,0],[2,0,3,0],[0,1,1,1],[1,1,2,1],[2,1,3,1]]);
 const trace=await diagram('05-质量追溯','测井装备物料批次质量追溯链',['来源','检验与库存','生产领用','追溯输出'],[[['供应商/采购','送货单与批次'],['收货检验','标准/实测/图片'],['领料与工单','批次到产品'],['正向追溯','影响工单/去向']], [['物料/BOM','关键件属性'],['冻结/移库/盘点','全过程流水'],['补料/退料','现场异常'],['反向追溯','供应商/检验/责任']]],[[0,0,1,0],[1,0,2,0],[2,0,3,0],[0,1,1,1],[1,1,2,1],[2,1,3,1]]);
 const children=[];
 children.push(new Paragraph({spacing:{line:640,lineRule:'exact',before:2200,after:0},alignment:AlignmentType.CENTER,children:[run('测井装备制造企业智能仓储管理系统（WMS）',{size:28,font:{eastAsia:'FangSong_GB2312',ascii:'Times New Roman',hAnsi:'Times New Roman'}})]}));
 children.push(new Paragraph({spacing:{line:640,lineRule:'exact',before:320,after:0},alignment:AlignmentType.CENTER,children:[run('系统功能设计文档',{bold:false,size:32,font:{eastAsia:'FZXiaoBiaoSong-B05S',ascii:'Times New Roman',hAnsi:'Times New Roman'}})]}));
 children.push(new Paragraph({alignment:AlignmentType.CENTER,spacing:{line:560,lineRule:'exact',before:500,after:0},children:[run('传统 WMS 业务系统 + AI 智能增强能力 + Multi-Agent 协同决策',{size:28})]}));
 children.push(new Paragraph({spacing:{line:560,lineRule:'exact',before:1200,after:0},alignment:AlignmentType.CENTER,children:[run('项目路径：C:\\Users\\es4er\\Desktop\\soft\\wms',{size:24})]}));
 children.push(new Paragraph({alignment:AlignmentType.CENTER,spacing:{line:560,lineRule:'exact'},children:[run('文档版本：V1.1    编制日期：2026年7月15日',{size:24})]})); children.push(pageBreak());
 children.push(h('文档控制',1));
 children.push(table(['项目','内容'],[['文档名称','系统功能设计文档'],['适用系统','测井装备制造企业智能仓储管理系统（WMS）'],['技术基线','Spring Boot 3.5.16、Java 17、MyBatis-Plus、MySQL、Redis、Vue/Vite 管理端'],['编制依据','项目源码、控制器接口、实体模型、SQL 脚本、前端角色菜单及 Agent 能力目录'],['设计定位','传统企业级 WMS 为业务主体；AI 提供建议、解释与异常辅助；Multi-Agent 负责跨域任务编排和协同决策'],['状态说明','“现有基线”表示源码已有结构或接口；“设计补充”表示为完整企业级落地提出的功能要求。']], [1900,7126]));
 children.push(h('修订记录',2)); children.push(table(['版本','日期','说明','编制'],[['V1.0','2026-07-15','基于当前项目源码形成首版功能设计','Codex']], [1200,1600,4826,1400]));
 children.push(h('目录',1,true)); children.push(new TableOfContents('目录（在 Word 中右键更新域可刷新页码）',{hyperlink:true,headingStyleRange:'1-3'})); children.push(pageBreak());

 children.push(h('第一部分 系统功能总体设计',1));
 children.push(h('1.1 建设背景与范围',2));
 children.push(p('本系统面向测井装备制造企业。物料具有多品种、小批量、关键件价值高、批次质量要求严、生产齐套约束强等特点。系统以采购收货、质量检验、入库上架、库存控制、生产领料、出库复核、盘点调整和质量追溯为主线，覆盖仓库、PMC、质量、生产现场及系统管理角色。'));
 children.push(p('系统设计不把 Agent 作为独立业务平台。所有库存增减、质量放行、单据状态和权限判断仍由确定性的 WMS 业务服务执行；AI 负责自然语言理解、风险识别、方案推荐和报告解释；Multi-Agent 在跨模块场景中进行任务拆解、受控调度、结果聚合和证据关联。'));
 children.push(...img(arch,'图1  系统总体融合功能架构',650,360));
 children.push(h('1.2 设计原则',2)); ['业务主导：单据、状态、库存流水和审批链为系统事实源。','人机协同：关键质量判定、库存调整、让步接收、计划变更由授权人员确认。','可解释与可追溯：Agent 输出包含数据来源、规则、建议理由和任务日志。','权限最小化：Agent 按能力目录限制数据范围与写操作，不直接访问越权数据。','可靠降级：LLM 不可用时，核心 WMS 仍按规则运行；智能建议退化为规则结果。','行业适配：强化关键件批次、序列号、防爆/高压属性、检验图像和工单齐套。'].forEach(x=>children.push(bullet(x)));
 children.push(h('1.3 分层功能架构',2,true));
 children.push(table(['层次','主要功能','与其他层关系'],[['WMS业务层','入库、出库、库存、生产领料、质量、计划、主数据、报表、系统管理','提供确定性业务服务和真实数据，是所有操作的权威入口'],['AI智能增强层','意图识别、齐套分析、库位/批次推荐、异常归因、趋势摘要、自然语言问答','调用只读查询或受控工具，将建议返回用户或总控 Agent'],['Multi-Agent协同层','任务分类、步骤规划、Agent 路由、上下文传递、失败补偿、报告聚合','跨模块编排，但不绕过业务服务、权限和审批'],['数据与集成层','MySQL、Redis、知识库、ERP/MES、PDA、RFID/AGV/IoT、视觉检验服务','提供事务数据、缓存、规则知识和外部事件']], [1700,3900,3426]));
 children.push(h('1.4 项目现状与设计补充边界',2));
 children.push(p('当前源码已包含入库、收货、出库、盘点、库存分析、PMC、质量、仓库、工人、用户与 Agent 控制器；包含库存、盘点、质量、计划、领料、仓库、审计、集成和智能体任务实体；前端已按管理员、PMC、仓管、库存、质检、工人提供工作台和菜单。本文在此基础上补充企业级审批、异常闭环、主数据治理、指标口径、非功能控制及 Agent 治理要求。'));

 children.push(h('第二部分 功能模块详细设计',1,true));
 children.push(p('本部分采用统一的九项功能设计模板。每项功能均以 WMS 事务为核心，Agent 能力作为增强项；如果智能服务不可用，业务人员仍可按规则完成基本操作。'));
 let fnNo=0;
 for(const [mi,m] of modules.entries()){
   children.push(h(`${mi+1}. ${m.name}`,2,true)); children.push(field('模块目标',m.goal)); children.push(field('主要用户角色', m.name.includes('计划')?'PMC计划员、仓管员、库存管理员、系统管理员':m.name.includes('质量')?'质检员、库存管理员、仓管员':m.name.includes('生产')?'PMC计划员、仓管员、生产工人':m.name.includes('数据')?'全部授权角色':'仓管员、库存管理员、质检员')); children.push(field('主要智能体',m.agent));
   for(const [fi,f] of m.funcs.entries()){
     fnNo++; children.push(h(`${mi+1}.${fi+1} ${f[0]}`,3,true));
     const role = m.name==='质量管理'?'质检员、质量主管；协同角色为仓管员和库存管理员':m.name==='计划协同管理'?'PMC计划员；协同角色为库存管理员、仓管员和质检员':m.name==='生产领料管理'?'PMC计划员、仓管员、生产工人':m.name==='数据分析与驾驶舱'?'按角色数据权限访问的管理人员和业务人员':'仓管员、库存管理员及相关审批人员';
     children.push(field('1. 功能名称',f[0])); children.push(field('2. 功能目标',`围绕${f[0]}形成标准化、可校验、可追溯的业务闭环，降低测井装备关键物料的错料、混批、短缺和质量放行风险。`));
     children.push(field('3. 业务流程',`${f[1]}准备 → 系统前置校验 → 业务人员执行/确认 → 状态与数量更新 → 异常分流 → 审计留痕与消息通知。`));
     children.push(field('4. 用户角色',role)); children.push(field('5. 输入数据',f[1])); children.push(field('6. 系统处理逻辑',f[4])); children.push(field('7. 输出结果',f[2])); children.push(field('8. 数据对象',f[3]));
     children.push(field('9. Agent 智能增强能力',`${m.agent}在权限和数据范围内完成意图识别、规则/数据检索、异常风险提示、候选方案排序及结论解释。推荐结果必须附理由和证据；涉及状态、数量或质量变化时调用 WMS Service，并由业务规则、事务和人工审批最终生效。`));
     children.push(h('关键规则与异常处理',4));
     ['单据必须具有唯一业务编号、来源单据和状态机，不允许跨状态跳转。','库存数量变化必须生成不可抵赖的库存流水，禁止通过页面直接修改余额。','并发更新采用版本号/条件更新；重复提交使用业务幂等键拦截。','数据不完整、权限不足、批次冻结或质量未放行时终止自动执行并转人工。','Agent 超时或输出不符合结构时回退到确定性规则，并记录降级原因。'].forEach(x=>children.push(bullet(x)));
     children.push(h('验收要点',4)); children.push(table(['验收项','判定标准'],[['正确性',`输入合法时生成${f[2]}，数量及状态与来源单据一致`],['异常性','错料、重复提交、越权、冻结或未放行数据被拦截并给出明确原因'],['追溯性',`可从结果反查${f[3]}及操作人、时间、Agent 任务和审计日志`],['可降级性','关闭智能服务后仍可按 WMS 规则完成人工流程']], [1900,7126]));
   }
 }

 children.push(h('第三部分 角色功能设计',1,true));
 children.push(p('角色权限采用 RBAC 模型，后端通过用户、角色、权限及角色权限关系控制接口，前端菜单只作为可见性辅助，不作为唯一安全边界。管理员可在演示或监督视图查看业务页面，但实际写操作仍需业务权限。'));
 children.push(table(['角色','功能范围','职责边界'],roles,[1700,4300,3026]));
 for(const [i,r] of roles.entries()){
   children.push(h(`3.${i+1} ${r[0]}`,2,true)); children.push(field('工作目标',r[1])); children.push(field('职责边界',r[2]));
   children.push(h('典型功能',3)); const map={
    '系统管理员':['用户新增、禁用、删除与批量操作','角色与权限矩阵维护','登录、接口、库存变更和 Agent 任务审计','系统参数、消息、日志和备份管理'],
    'PMC计划员':['客户订单审核与生产计划生成','BOM 需求展开和齐套率检查','缺料分类、计划调整和领料单提交','向仓库、质量和库存角色发送协同通知'],
    '仓管员':['采购收货和合格物料上架','领料单转出库、批次库位拣选和 PDA 扫码','复核、备料区交接与仓库异常闭环','参与盘点但不越权审批重大调整'],
    '库存管理员':['库存余额、批次、库位和冻结查询','安全库存、补货、移库和库位优化','盘点任务、差异复盘和调整审批','库存准确率与呆滞风险分析'],
    '质检员':['待检任务和检验标准管理','实测值、缺陷、图片及视觉比对证据录入','质量异常隔离、冻结和不合格处置','批次正反向追溯与质量趋势分析'],
    '生产工人':['查看本人今日工单和备料任务','扫描确认物料、批次、数量与交接','提交缺料补料、退料和现场异常','反馈工序转交与完工记录']};
   map[r[0]].forEach(x=>children.push(bullet(x)));
   children.push(h('数据权限与关键控制',3)); children.push(p(`该角色仅可访问被授权仓库、组织、工单或业务范围。敏感操作要求后端再次鉴权并写入 SysAuditLog；Agent 继承发起人的数据权限，不能扩大权限。${r[2]}`));
 }

 children.push(h('第四部分 重点智能业务流程设计',1,true));
 children.push(h('4.1 智能入库流程',2)); children.push(...img(inbound,'图2  智能入库流程及 Agent 参与点',650,360));
 children.push(p('流程控制要点：收货 Agent 只完成来源和数量一致性核验；质检 Agent 可提示抽样风险与图像异常，但质量结论由检验规则和质检员确认；库位 Agent 输出多个候选及评分；最终上架由 PDA 双扫码触发事务，库存 Agent 核验余额和批次状态。'));
 children.push(table(['步骤','责任主体','Agent 增强','业务落库/控制'],[['到货与收货','仓管员','识别重复到货、订单差异','收货单及明细'],['质量检验','质检员','匹配标准、风险提示、图像比对','检验单、问题单、冻结'],['库位推荐','仓管员','过滤容量/属性并排序','候选结果，不直接改库存'],['上架确认','仓管员/PDA','错料错位实时提示','库存余额、流水、审计']], [1700,1800,2700,2826]));
 children.push(h('4.2 智能生产领料流程',2,true)); children.push(...img(picking,'图3  计划驱动的智能生产领料流程',650,360));
 children.push(p('PMC Agent 先按 BOM 和计划量形成总需求；库存 Agent 仅统计合格、未冻结、可访问库位的可用量并分类短缺；总控 Agent 汇总生成备料方案。领料单审批后由出库 Agent 生成批次库位明细，仓管员按 PDA 拣选复核，生产工人在备料区扫码交接。'));
 children.push(h('4.3 智能盘点差异闭环',2,true)); children.push(p('盘点任务创建 → 盘点范围快照 → PDA 盲盘 → StocktakeAgent 聚合差异 → InventoryAgent 检查期间流水、冻结和移库 → 输出差异原因候选 → 库存管理员复盘 → 按阈值审批调整 → 生成库存流水与 AuditAgent 证据链。Agent 不得直接将“疑似原因”写成已确认事实。'));
 children.push(h('4.4 质量异常联动流程',2,true)); children.push(...img(trace,'图4  关键物料批次质量追溯链',650,340)); children.push(p('检验不合格或现场缺陷 → QualityAgent 识别影响批次 → InventoryAgent 冻结可用量 → OrderPlanAgent 重算受影响计划齐套 → 向 PMC/仓库发送协同通知 → 质检员选择退货、返修、报废或让步接收 → 复检/审批 → 解冻或扣减 → 更新追溯链。'));
 children.push(h('4.5 安全库存与计划风险预警',2,true)); children.push(p('系统定时或事件触发读取安全库存规则、近期开工计划、历史消耗、在途和待检数量。InventoryAgent 计算预计可用量，OrderPlanAgent 判断对计划的影响，IntegrationAgent 可生成 ERP/MES 协同消息。预警必须去重并支持确认、处理、升级、关闭；采购或计划变更仍由授权人员执行。'));

 children.push(h('第五部分 Multi-Agent 架构设计',1,true)); children.push(...img(agentArch,'图5  Multi-Agent 分层协同与受控业务调用',650,360));
 children.push(h('5.1 Agent 能力目录',2)); children.push(table(['Agent','层级','业务职责'],agents,[2350,1200,5476]));
 children.push(h('5.2 协同机制',2,true));
 children.push(p('用户通过工作台问答或明确业务动作发起任务。总控 Agent 将请求映射为任务类型，例如收货—质检—入库、订单计划—领料、领料—出库、盘点—调整、库存冻结或智能仓储事件。规划服务依据能力目录生成有序步骤，步骤间只传递白名单字段。每个 Agent 返回结构化结论、处理内容和数据库证据；总控汇总为面向用户的报告。'));
 children.push(table(['协同要素','设计'],[['任务状态','CREATED → PLANNED → RUNNING → WAITING_CONFIRMATION / SUCCEEDED / FAILED / CANCELLED'],['步骤状态','PENDING → RUNNING → SUCCEEDED / FAILED / SKIPPED；失败按策略重试或补偿'],['上下文','任务 ID、发起人、角色、数据范围、业务主键、前序结构化输出、追踪 ID'],['能力约束','supportedTaskTypes、dataScope、dataTables、returnFields、canMutate'],['可观测性','AgentTask、AgentTaskStep、AgentExecutionLog、AgentStatusSnapshot 与业务审计日志关联']], [2100,6926]));
 children.push(h('5.3 Agent 调用 WMS 业务服务',2));
 children.push(p('Agent 通过受控工具/适配器调用 Spring Service，而不是拼接 SQL 或直接修改表。Service 执行参数校验、RBAC、状态机、幂等、乐观锁和事务。只读查询可直接返回事实快照；写操作分为自动允许、需人工确认和禁止三类。质量放行、重大库存调整、让步接收、计划发布等高风险动作默认需要人工确认。'));
 children.push(h('5.4 数据库、缓存与知识库访问',2));
 children.push(table(['资源','访问方式','用途与边界'],[['MySQL','MyBatis-Plus Mapper 经 Service 访问','业务事实、单据、库存、质量、任务与审计；事务数据唯一真源'],['Redis','缓存/短期任务状态/幂等键','不作为库存最终账；缓存失效时回源数据库'],['知识库','经检索服务按版本和权限读取','SOP、检验标准解释、物料知识、异常处置规范；答案附文档版本'],['LLM（项目配置为 DeepSeek）','统一 Chat Service，超时、结构化校验和降级','意图、摘要、解释和建议；不得替代业务规则'],['外部系统','Integration Service + 消息日志','ERP/MES 订单和工单同步、状态回传；幂等与重试']], [1800,2700,4526]));
 children.push(h('5.5 辅助用户决策的呈现规则',2)); ['区分“事实”“规则计算”“模型推断”和“建议”，使用不同标签展示。','建议至少给出业务主键、数据时间、关键指标、约束、备选方案和风险。','高风险建议提供“确认执行/修改参数/转人工/取消”操作，不提供无确认自动落账。','用户可查看 Agent 执行链、每步耗时、输入摘要、输出、失败原因和证据链接。','当数据过期、缺失或相互冲突时，系统必须降低置信度并明确提示。'].forEach(x=>children.push(bullet(x)));
 children.push(h('5.6 安全、治理与异常机制',2,true));
 children.push(table(['风险','控制措施'],[['提示注入/越权查询','系统提示与业务数据隔离；工具参数白名单；继承用户 RBAC 和数据域'],['模型幻觉','结论必须绑定查询证据；业务数值由程序计算；结构化 Schema 校验'],['重复执行','任务幂等键、业务单号唯一索引、步骤去重和数据库事务'],['部分失败','步骤补偿、可重试/不可重试分类、人工接管和恢复点'],['敏感数据泄露','字段脱敏、日志最小化、传输加密、知识库权限过滤'],['不可解释决策','保存规则版本、提示版本、模型版本、输入摘要、输出和人工决定']], [2200,6826]));

 children.push(h('第六部分 数据对象与接口设计补充',1,true));
 children.push(h('6.1 核心数据域',2));
 children.push(table(['数据域','项目对象示例','设计说明'],[['主数据','MdItem、MdSupplier、MdBatch、MdBomHeader/Line、WhWarehouse/Zone/Location','统一编码、版本、有效期和组织范围'],['入库/收货','RecReceiptOrder/Line、InInboundOrder/Line','来源、检验、批次、上架链路'],['出库','OutOrder/Line、OutPickingTask/Line、OutReviewTask/Line/Exception','申请、分配、拣货、复核和交接'],['库存','InvInventory、InvTransaction、InvFreezeRecord、InvAlertRecord','余额+不可变流水；质量与冻结影响可用量'],['盘点/调整','InvStocktakeOrder/Line/Difference、InvAdjustmentOrder/Line','快照、差异、审批、调整流水'],['计划/领料','OrdCustomerOrder、PmcProductionPlan/Line、PmcRequisitionOrder/Line','订单—计划—BOM—领料链'],['质量','QuaInspectionOrder/Line、QuaInspectStandard、QuaQualityIssue、QuaReturnOrder','标准、检验、问题、处置、追溯'],['Agent/审计','AgentTask/Step/ExecutionLog/StatusSnapshot、SysAuditLog','智能任务与业务证据关联']], [1600,3600,3826]));
 children.push(h('6.2 状态机和一致性要求',2,true));
 children.push(p('单据状态采用受控枚举并由领域服务迁移。库存现存量、占用量、冻结量、可用量需满足可用量=现存量−占用量−冻结量（按系统具体口径可扩展质检待定量）。任何数量不得出现无业务原因的负数。跨收货、质检、入库和库存的操作采用本地事务；跨系统同步使用消息状态、重试和最终一致性。'));
 children.push(h('6.3 接口与错误处理',2)); children.push(p('接口遵循 REST 风格，统一响应码、业务消息、追踪 ID 和时间戳。创建/提交类接口接收幂等键；列表接口支持分页、排序和权限过滤；导出任务异步执行。错误分为参数错误、业务冲突、权限不足、资源不存在、并发冲突、外部依赖失败和系统异常，禁止向前端暴露数据库堆栈。'));
 children.push(h('6.4 项目接口基线摘要',2));
 children.push(table(['控制器','现有功能摘要'],[['Receipt / Inbound','收货创建、提交质检、入库创建/确认'],['Outbound / Warehouse','出库创建、拣货分配与提交、复核、确认、仓库工作台与库位'],['Inventory / Stocktake / Transfer','库存查询、冻结、盘点、差异、调整和移库'],['PmcPlan / Order','订单、计划、BOM、领料、协同通知和 PMC 助手'],['Quality','工作台、任务、检验、图像比对、异常、冻结、退货、追溯和标准'],['Worker','工作台、扫码、异常、补料、交接、完工和转序'],['Agent','助手、任务启动、步骤/日志/分析、能力和状态查询'],['User / Audit / Integration','登录、用户角色权限、审计和外部同步']], [2400,6626]));

 children.push(h('第七部分 非功能与验收设计',1,true));
 children.push(h('7.1 性能与容量',2)); children.push(table(['场景','目标建议'],[['常规查询','95% 请求在 2 秒内完成；大屏聚合在 5 秒内完成'],['扫码作业','在线扫码确认在 1 秒级反馈；离线补传可去重'],['批量任务','盘点差异、报表和 Agent 长任务异步化，展示进度'],['并发一致性','库存热点行采用短事务、条件更新和冲突重试'],['Agent 响应','先返回任务 ID/SSE 进度；超时可取消、重试或转人工']], [2500,6526]));
 children.push(h('7.2 可用性、日志与监控',2)); children.push(p('核心 WMS 与 LLM 解耦部署。数据库、Redis、外部接口、视觉检测服务和 LLM 分别设置健康检查与超时。日志包含 traceId、userId、businessNo、agentTaskId，但不记录口令、令牌和完整敏感提示。监控覆盖接口成功率、库存冲突、消息积压、Agent 步骤失败率、降级率和人工接管率。'));
 children.push(h('7.3 安全与审计',2)); children.push(p('登录采用 JWT 等会话机制并支持失效；接口后端鉴权；密码安全存储；关键操作二次确认；角色分离；导出水印；数据库备份与恢复演练。库存、质量、权限和 Agent 写操作记录操作前后值、理由、来源 IP/终端、时间及审批链。'));
 children.push(h('7.4 验收场景',2,true));
 children.push(table(['编号','验收场景','预期结果'],[['AT-01','采购到货含一条不合格明细','合格行可生成入库；不合格行隔离冻结且可追溯'],['AT-02','同一上架请求重复提交','只产生一次库存增加和一条有效业务流水'],['AT-03','领料需求包含待检和冻结批次','齐套分析分类正确，不将其计入可用量'],['AT-04','PDA 扫描错误物料/批次/库位','立即拦截，不更新拣货进度'],['AT-05','盘点期间存在移库流水','差异分析能关联期间流水，审批后才调整'],['AT-06','LLM 服务不可用','人工 WMS 流程可继续，Agent 任务显示降级原因'],['AT-07','低权限用户要求 Agent 解冻库存','能力目录和 RBAC 双重拒绝并记录审计'],['AT-08','质量批次反向追溯','可定位供应商、收货、检验标准版本和处置记录']], [1000,3700,4326]));

 children.push(h('附件 1  功能—角色—Agent 对照矩阵',1,true));
 children.push(table(['功能域','主责角色','协同角色','主要 Agent'],modules.map(m=>[m.name,m.name.includes('计划')?'PMC计划员':m.name.includes('质量')?'质检员':m.name.includes('生产')?'PMC/仓管/工人':m.name.includes('数据')?'各角色':'仓管/库存管理员','系统管理员及上下游岗位',m.agent]),[1500,1900,2200,3426]));
 children.push(h('附件 2  术语与缩写',1,true)); children.push(table(['术语','说明'],[['WMS','Warehouse Management System，仓储管理系统'],['PMC','Production and Material Control，生产与物料控制'],['ASN','Advanced Shipping Notice，预到货通知'],['BOM','Bill of Materials，物料清单'],['PDA','手持数据采集终端'],['FIFO/FEFO','先进先出/先到期先出'],['RBAC','基于角色的访问控制'],['Agent','在受控能力范围内完成分析或业务工具调用的软件智能体'],['Orchestrator','负责意图理解、任务分解、调度和汇总的总控智能体'],['证据链','将建议或操作关联到业务单据、数据快照、规则版本、任务步骤和审计日志的可追溯记录']], [2100,6926]));
 children.push(h('附件 3  实施优先级建议',1,true));
 children.push(table(['阶段','范围','验收重点'],[['一期：业务闭环','主数据、收货质检、入库、库存、领料出库、PDA、盘点、权限','账实一致、批次追溯、单据状态和权限闭环'],['二期：智能增强','齐套分析、库位/批次推荐、预警、异常归因、角色助手','建议可解释、可降级、关键动作人工确认'],['三期：多智能体协同','跨域任务编排、集成、证据链、监控和治理','任务可观测、失败可恢复、Agent 不绕过业务服务'],['四期：设备与优化','RFID/AGV/IoT、视觉检测、预测优化','设备事件幂等、模型指标、持续运营']], [1800,4000,3226]));
 children.push(p('文档结束',{align:AlignmentType.CENTER,indent:false,before:560}));
 children.push(new Paragraph({alignment:AlignmentType.CENTER,spacing:{line:560,lineRule:'exact',before:560},children:[run('测井装备制造企业智能仓储管理系统项目组',{size:28})]}));
 children.push(new Paragraph({alignment:AlignmentType.CENTER,indent:{left:1120},spacing:{line:560,lineRule:'exact'},children:[run('2026年7月15日',{size:28})]}));

 const doc=new Document({
  creator:'Codex', title:'测井装备制造企业智能仓储管理系统（WMS）系统功能设计文档', subject:'传统WMS + AI智能增强 + Multi-Agent协同决策',
  styles:{default:{document:{run:{font:{eastAsia:'FangSong_GB2312',ascii:'Times New Roman',hAnsi:'Times New Roman',cs:'Times New Roman'},size:28,color:C.black},paragraph:{spacing:{line:560,lineRule:'exact'}}}},paragraphStyles:[
   {id:'Heading1',name:'Heading 1',basedOn:'Normal',next:'Normal',quickFormat:true,run:{font:{eastAsia:'SimHei',ascii:'Times New Roman',hAnsi:'Times New Roman'},size:28,bold:true,color:C.black},paragraph:{spacing:{line:640,lineRule:'exact',before:0,after:0},outlineLevel:0}},
   {id:'Heading2',name:'Heading 2',basedOn:'Normal',next:'Normal',quickFormat:true,run:{font:{eastAsia:'KaiTi_GB2312',ascii:'Times New Roman',hAnsi:'Times New Roman'},size:28,bold:true,color:C.black},paragraph:{spacing:{line:640,lineRule:'exact',before:0,after:0},outlineLevel:1}},
   {id:'Heading3',name:'Heading 3',basedOn:'Normal',next:'Normal',quickFormat:true,run:{font:{eastAsia:'FangSong_GB2312',ascii:'Times New Roman',hAnsi:'Times New Roman'},size:28,bold:false,color:C.black},paragraph:{spacing:{line:640,lineRule:'exact',before:0,after:0},outlineLevel:2}},
   {id:'Heading4',name:'Heading 4',basedOn:'Normal',next:'Normal',quickFormat:true,run:{font:{eastAsia:'FangSong_GB2312',ascii:'Times New Roman',hAnsi:'Times New Roman'},size:28,bold:false,color:C.black},paragraph:{spacing:{line:640,lineRule:'exact',before:0,after:0},outlineLevel:3}}
  ]},
  numbering:{config:[{reference:'bullet-list',levels:[{level:0,format:LevelFormat.BULLET,text:'●',alignment:AlignmentType.LEFT,style:{paragraph:{indent:{left:600,hanging:280}}}},{level:1,format:LevelFormat.BULLET,text:'○',alignment:AlignmentType.LEFT,style:{paragraph:{indent:{left:1000,hanging:280}}}}]}]},
  evenAndOddHeaderAndFooters:true,
  sections:[{properties:{page:{size:{width:11906,height:16838},margin:{top:2098,right:1474,bottom:1984,left:1587},pageNumbers:{start:1,format:'decimal'}}},headers:{default:new Header({children:[new Paragraph({border:{bottom:{style:BorderStyle.SINGLE,size:6,color:C.black,space:1}},children:[run('测井装备制造企业智能仓储管理系统（WMS）— 系统功能设计文档',{size:21})]})]}),even:new Header({children:[new Paragraph({border:{bottom:{style:BorderStyle.SINGLE,size:6,color:C.black,space:1}},children:[run('测井装备制造企业智能仓储管理系统（WMS）— 系统功能设计文档',{size:21})]})]})},footers:{default:new Footer({children:[new Paragraph({alignment:AlignmentType.RIGHT,children:[run('-',{size:21,font:'SimSun'}),new TextRun({children:[PageNumber.CURRENT],font:'SimSun',size:21,color:C.black}),run('-',{size:21,font:'SimSun'})]})]}),even:new Footer({children:[new Paragraph({alignment:AlignmentType.LEFT,children:[run('-',{size:21,font:'SimSun'}),new TextRun({children:[PageNumber.CURRENT],font:'SimSun',size:21,color:C.black}),run('-',{size:21,font:'SimSun'})]})]})},children}]
 });
 const out=path.join(OUT_DIR,'测井装备制造企业智能仓储管理系统WMS-系统功能设计文档-V1.1-通用规范排版版.docx');
 fs.writeFileSync(out,await Packer.toBuffer(doc)); console.log(out); console.log('detailedFunctions='+fnNo);
}
main().catch(e=>{console.error(e);process.exit(1)});
