<div align="center">
  <h1>WellLog</h1>
  <p><strong>An Auditable Multi-Agent Platform for Warehouse Operations</strong></p>
  <p>面向制造业仓储流程的可审计多智能体研究原型</p>
  <p>
    <a href="#overview">Overview</a> ·
    <a href="#method">Method</a> ·
    <a href="#system-snapshots">Snapshots</a> ·
    <a href="#quick-start">Quick Start</a>
  </p>
</div>

![WellLog multi-agent platform teaser](assets/teaser.png)

<a id="overview"></a>

## Overview

WellLog 探索如何把大模型的任务规划能力引入仓储管理，同时保留业务系统需要的确定性、权限边界与审计证据。用户用自然语言描述目标；总控智能体生成执行计划，领域智能体在受限数据范围内完成真实业务操作，人工审批与步骤日志贯穿全过程。

项目覆盖收货、质检、入库、出库、库存、盘点和移库等流程，并提供 Vue 工作台、Spring Boot 多智能体内核与独立的视觉质检服务。

<a id="method"></a>

## Method

```mermaid
flowchart LR
    U["Natural-language task"] --> O["Orchestrator"]
    O --> P["Plan & capability routing"]
    P --> A["Domain agents"]
    A --> D[("WMS data")]
    A --> H{"Human approval"}
    H --> A
    A --> T["Trace & audit"]
```

核心设计只有三条：

- **模型负责规划，系统负责执行。** LLM 解析目标并生成候选链路，不直接修改业务单据。
- **能力与数据均有边界。** Agent 通过能力目录声明职责、可见字段、数据表和可执行任务。
- **执行过程可审计。** 任务、步骤、决策、异常和人工确认均被结构化记录。

当前原型包含 1 个总控与 15 个领域智能体，按决策、策略、执行和辅助四层组织；模型不可用时可回退到规则链路。

## System Snapshots

<table>
  <tr>
    <td width="50%" align="center"><img src="assets/demo-workbench.png" alt="AI workbench"><br><sub>自然语言任务工作台</sub></td>
    <td width="50%" align="center"><img src="assets/demo-agent-union.png" alt="Agent registry"><br><sub>智能体能力与组织视图</sub></td>
  </tr>
  <tr>
    <td width="50%" align="center"><img src="assets/dash-assistant.png" alt="Assistant routing"><br><sub>意图分流与人工确认</sub></td>
    <td width="50%" align="center"><img src="assets/dash-agent-monitor.png" alt="Agent monitor"><br><sub>步骤级执行监控</sub></td>
  </tr>
  <tr>
    <td width="50%" align="center"><img src="assets/demo-outbound.png" alt="Outbound workflow"><br><sub>出库协同</sub></td>
    <td width="50%" align="center"><img src="assets/demo-pda-scan.png" alt="PDA picking"><br><sub>PDA 三重校验</sub></td>
  </tr>
  <tr>
    <td width="50%" align="center"><img src="assets/demo-inventory-control.png" alt="Inventory control"><br><sub>库存控制</sub></td>
    <td width="50%" align="center"><img src="assets/demo-quality-compare.png" alt="Visual inspection"><br><sub>OpenCV + SSIM 视觉质检</sub></td>
  </tr>
  <tr>
    <td width="50%" align="center"><img src="assets/dash-analytics.png" alt="Operations analytics"><br><sub>运营分析</sub></td>
    <td width="50%" align="center"><img src="assets/dash-trace.png" alt="Batch traceability"><br><sub>批次证据链</sub></td>
  </tr>
</table>

## Implementation

| Component | Stack |
| --- | --- |
| Multi-agent backend | Java 17 · Spring Boot 3.5 · MyBatis-Plus |
| Web workbench | Vue 3 · Vite · Element Plus · ECharts |
| Data layer | MySQL 8 · Redis 7 |
| Planning model | DeepSeek-compatible Chat API |
| Visual inspection | FastAPI · OpenCV · SSIM |

<a id="quick-start"></a>

## Quick Start

需要 JDK 17、Node.js 18、MySQL 8 与 Redis 7。先创建 `wms_db`，执行 `src/main/resources/sql/` 中的建表与示例数据脚本，并配置：

```bash
DEEPSEEK_API_KEY=your-key
DB_PASSWORD=your-password
JWT_SECRET=replace-with-a-random-secret-at-least-32-characters
```

启动后端与前端：

```bash
./mvnw spring-boot:run

cd frontend-ai-workbench
npm install
npm run dev
```

前端默认运行于 `http://127.0.0.1:5173`，后端端口为 `8088`。视觉质检服务可选：

```bash
cd light-inspection-service
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8002
```

## Reproducibility

```bash
./mvnw test
cd frontend-ai-workbench && npm run build
```

仓库已包含多智能体编排、权限分流与编号格式化测试，以及用于复现实验流程的示例 SQL。当前实现是研究与教学原型，不建议未经安全审查直接用于生产环境。

## Citation

若本项目对你的研究或课程有帮助，可引用本仓库：

```bibtex
@software{welllog_multi_agent_platform,
  title  = {WellLog: An Auditable Multi-Agent Platform for Warehouse Operations},
  author = {WellLog Contributors},
  year   = {2026},
  url    = {https://github.com/es4er/wms-agent-platform}
}
```

## License

本仓库尚未声明开源许可证，默认保留所有权利。第三方依赖遵循各自许可证。
