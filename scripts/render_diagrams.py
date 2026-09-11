from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs'/'功能设计文档插图'; OUT.mkdir(parents=True,exist_ok=True)
FONT='C:/Windows/Fonts/msyh.ttc'; BOLD='C:/Windows/Fonts/msyhbd.ttc'
def f(n,b=False): return ImageFont.truetype(BOLD if b else FONT,n)
def diagram(name,title,cols,rows):
    W=1800; cw=(W-100)//len(cols); H=210+len(rows)*150
    im=Image.new('RGB',(W,H),'white'); d=ImageDraw.Draw(im)
    d.text((50,28),title,font=f(42,True),fill='black')
    for i,c in enumerate(cols):
        x=50+i*cw; d.rounded_rectangle((x,95,x+cw-18,158),12,fill='white',outline='black',width=3)
        box=d.textbbox((0,0),c,font=f(26,True)); d.text((x+(cw-18-(box[2]-box[0]))/2,108),c,font=f(26,True),fill='black')
    for ri,row in enumerate(rows):
        for ci,cell in enumerate(row):
            if not cell: continue
            x=55+ci*cw; y=185+ri*150
            d.rounded_rectangle((x,y,x+cw-28,y+105),14,fill='white',outline='black',width=3)
            a,b=cell; ba=d.textbbox((0,0),a,font=f(24,True)); bb=d.textbbox((0,0),b,font=f(19))
            d.text((x+(cw-28-(ba[2]-ba[0]))/2,y+20),a,font=f(24,True),fill='black')
            d.text((x+(cw-28-(bb[2]-bb[0]))/2,y+65),b,font=f(19),fill='black')
        for ci in range(len(row)-1):
            if row[ci] and row[ci+1]:
                y=185+ri*150+52; x1=55+ci*cw+cw-28; x2=55+(ci+1)*cw
                d.line((x1+5,y,x2-12,y),fill='black',width=5); d.polygon([(x2-12,y-9),(x2-12,y+9),(x2,y)],fill='black')
    im.save(OUT/(name+'.png'),dpi=(180,180))
    im.save(OUT/(name+'.jpg'),quality=96,subsampling=0,dpi=(180,180))

diagram('01-总体功能架构','传统 WMS + AI 智能增强 + Multi-Agent 协同融合架构',['交互与角色','传统 WMS 业务层','AI 智能增强层','协同与治理层'],[[('管理端 / PDA','六类业务角色'),('入出库 / 库存','生产领料 / 质量'),('预测·推荐·问答','异常识别·报告'),('总控编排','权限·审计·证据')],[('驾驶舱','指标下钻'),('计划协同 / 主数据','接口与消息'),('规则 + LLM','降级与人工确认'),('业务 Agent','任务·步骤·日志')]])
diagram('02-智能入库流程','智能入库业务流程',['业务来源','仓储/质量执行','Agent 增强','结果'],[[('采购订单 / ASN','供应商到货'),('收货登记','数量与标签核验'),('ReceivingAgent','单据一致性检查'),('收货单','进入待检')],[('检验任务','标准 / 抽样'),('质检执行','实测与图像证据'),('QualityAgent','风险与判定辅助'),('质量放行','不合格隔离')],[('合格明细','批次属性'),('上架扫码','物料+库位双校验'),('WarehouseAgent','候选库位评分'),('库存与流水','审计证据链')]])
diagram('03-智能生产领料流程','计划驱动的智能生产领料流程',['PMC','协同决策','仓库执行','生产现场'],[[('生产计划','订单 / BOM / 交期'),('OrderPlanAgent','需求分解'),('待处理领料单','仓管接收'),('备料区领料','工人确认')],[('计划调整','重算齐套'),('InventoryAgent','库存与质量约束'),('批次库位拣选','PDA 扫码'),('补料 / 退料','异常反馈')],[('协同通知','责任与时限'),('Orchestrator','汇总结论'),('复核与交接','库存流水'),('工序执行','完工反馈')]])
diagram('04-Multi-Agent协同','Multi-Agent 分层协同与业务服务调用',['决策层','策略层','执行层','受控资源'],[[('Orchestrator','规划 / 路由 / 汇总'),('计划·库存·集成·审计','约束与策略'),('收货·质检·入出库','盘点·移库·现场'),('Spring Service','事务 / 校验 / 权限')],[('任务状态机','task / step / log'),('能力目录','数据范围 / 写权限'),('工具调用','结构化输入输出'),('MySQL / Redis','知识库 / 外部系统')]])
diagram('05-质量追溯','测井装备物料批次质量追溯链',['来源','检验与库存','生产领用','追溯输出'],[[('供应商 / 采购','送货单与批次'),('收货检验','标准 / 实测 / 图片'),('领料与工单','批次到产品'),('正向追溯','影响工单 / 去向')],[('物料 / BOM','关键件属性'),('冻结 / 移库 / 盘点','全过程流水'),('补料 / 退料','现场异常'),('反向追溯','供应商 / 检验 / 责任')]])
