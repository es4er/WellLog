package com.upc.wms.agent;

import com.upc.wms.agent.capability.AgentCapabilityCatalog;
import com.upc.wms.agent.capability.OrchestratorPlan;
import com.upc.wms.agent.capability.OrchestratorPlanningService;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.WmsAgentOrchestrator;
import com.upc.wms.agent.domain.AuditAgent;
import com.upc.wms.agent.domain.InboundAgent;
import com.upc.wms.agent.domain.InventoryAgent;
import com.upc.wms.agent.domain.QualityAgent;
import com.upc.wms.agent.domain.ReceivingAgent;
import com.upc.wms.agent.log.AgentDecisionLogService;
import com.upc.wms.agent.log.AgentTaskLogService;
import com.upc.wms.agent.vo.AgentTaskVO;
import com.upc.wms.common.BusinessException;
import com.upc.wms.config.AgentProperties;
import com.upc.wms.entity.AgentTask;
import com.upc.wms.entity.AgentTaskStep;
import com.upc.wms.entity.InInboundOrder;
import com.upc.wms.entity.InvInventory;
import com.upc.wms.entity.QuaInspectionLine;
import com.upc.wms.entity.QuaInspectionOrder;
import com.upc.wms.entity.RecReceiptLine;
import com.upc.wms.entity.RecReceiptOrder;
import com.upc.wms.entity.WhLocation;
import com.upc.wms.service.InboundService;
import com.upc.wms.service.InventoryService;
import com.upc.wms.service.KitAvailabilityService;
import com.upc.wms.service.OutGenerateExceptionService;
import com.upc.wms.service.QualityService;
import com.upc.wms.service.ReceiptService;
import com.upc.wms.service.UserService;
import com.upc.wms.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多智能体架构核心验证：使用真实的领域 Agent + Mock 的 Service / 日志服务，
 * 验证总控智能体能正确注册智能体、按 nextAgent 链式调度并在 Agent 间传递上下文，
 * 无需依赖 MySQL / Redis / 登录鉴权即可运行。
 */
class WmsAgentOrchestratorTest {

    private ReceiptService receiptService;
    private QualityService qualityService;
    private InboundService inboundService;
    private WarehouseService warehouseService;
    private InventoryService inventoryService;
    private KitAvailabilityService kitAvailabilityService;
    private UserService userService;

    private AgentTaskLogService taskLogService;
    private AgentDecisionLogService decisionLogService;
    private OutGenerateExceptionService outGenerateExceptionService;

    private WmsAgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        receiptService = mock(ReceiptService.class);
        qualityService = mock(QualityService.class);
        inboundService = mock(InboundService.class);
        warehouseService = mock(WarehouseService.class);
        inventoryService = mock(InventoryService.class);
        kitAvailabilityService = mock(KitAvailabilityService.class);
        userService = mock(UserService.class);

        taskLogService = mock(AgentTaskLogService.class);
        decisionLogService = mock(AgentDecisionLogService.class);
        outGenerateExceptionService = mock(OutGenerateExceptionService.class);

        // 日志服务打桩：返回可用的任务 / 步骤对象
        AgentTask task = new AgentTask();
        task.setId(1L);
        task.setTaskNo("AT-TEST-0001");
        task.setStatus("RUNNING");
        when(taskLogService.createTask(anyString(), any(), any(), any())).thenReturn(task);

        AtomicLong stepId = new AtomicLong(1);
        when(taskLogService.startStep(anyLong(), org.mockito.ArgumentMatchers.anyInt(), anyString(), any(), any()))
                .thenAnswer(inv -> {
                    AgentTaskStep step = new AgentTaskStep();
                    step.setId(stepId.getAndIncrement());
                    step.setStartTime(LocalDateTime.now());
                    return step;
                });

        AgentTaskVO vo = new AgentTaskVO();
        vo.setTaskId(1L);
        vo.setTaskNo("AT-TEST-0001");
        vo.setStatus("SUCCESS");
        when(taskLogService.getTaskVO(anyLong())).thenReturn(vo);

        // 真实的领域 Agent（注入 Mock Service）
        List<com.upc.wms.agent.core.Agent> agents = List.of(
                new ReceivingAgent(receiptService),
                new QualityAgent(qualityService),
                new InboundAgent(inboundService, warehouseService),
                new InventoryAgent(inventoryService, kitAvailabilityService, mock(com.upc.wms.service.PmcPlanService.class)),
                new AuditAgent(userService)
        );

        OrchestratorPlanningService planningService = mock(OrchestratorPlanningService.class);
        when(planningService.plan(anyString(), any(), any(), any())).thenAnswer(inv -> {
            String type = inv.getArgument(0);
            return OrchestratorPlan.ruleBased(
                    type,
                    AgentNames.RECEIVING,
                    List.of(AgentNames.RECEIVING, AgentNames.QUALITY, AgentNames.INBOUND,
                            AgentNames.INVENTORY, AgentNames.AUDIT),
                    List.of("收货", "质检", "入库", "库存", "审计"),
                    "测试规则链");
        });

        AgentProperties agentProperties = new AgentProperties();
        agentProperties.setEnabled(true);
        agentProperties.setTraceEnabled(true);

        orchestrator = new WmsAgentOrchestrator(
                agents,
                taskLogService,
                decisionLogService,
                outGenerateExceptionService,
                planningService,
                new AgentCapabilityCatalog(),
                agentProperties);
        orchestrator.init();

        // 公共业务打桩
        RecReceiptOrder receiptOrder = new RecReceiptOrder();
        receiptOrder.setReceiptId(100L);
        receiptOrder.setReceiptNo("RC-100");
        receiptOrder.setWarehouseId(1L);
        when(receiptService.createReceipt(any())).thenReturn(receiptOrder);

        RecReceiptLine receiptLine = new RecReceiptLine();
        receiptLine.setReceiptLineId(1000L);
        receiptLine.setItemId(1001L);
        receiptLine.setBatchId(500L);
        receiptLine.setReceivedQty(new BigDecimal("50"));
        Map<String, Object> receiptDetail = new HashMap<>();
        receiptDetail.put("lines", List.of(receiptLine));
        when(receiptService.getReceiptDetail(100L)).thenReturn(receiptDetail);

        QuaInspectionOrder inspectionOrder = new QuaInspectionOrder();
        inspectionOrder.setInspectionId(200L);
        inspectionOrder.setInspectionNo("QC-200");
        inspectionOrder.setInspectionResult("QUALIFIED");
        when(qualityService.submitInspection(any())).thenReturn(inspectionOrder);

        WhLocation location = new WhLocation();
        location.setLocationId(10L);
        location.setLocationCode("A-01");
        when(warehouseService.listAvailableLocations(1L)).thenReturn(List.of(location));

        InInboundOrder inboundOrder = new InInboundOrder();
        inboundOrder.setInboundId(300L);
        inboundOrder.setInboundNo("IN-300");
        when(inboundService.createInboundOrder(anyLong(), anyLong(), any())).thenReturn(inboundOrder);
        when(inboundService.confirmInbound(any())).thenReturn(inboundOrder);

        InvInventory inventory = new InvInventory();
        inventory.setInventoryId(9001L);
        inventory.setItemId(1001L);
        inventory.setBatchId(500L);
        inventory.setOnhandQty(new BigDecimal("50"));
        when(inventoryService.queryInventoryByBatch(500L)).thenReturn(List.of(inventory));
    }

    @Test
    @DisplayName("收货质检入库全流程：5 个智能体依次协作并成功完成")
    void receiptInspectionInbound_happyPath() {
        // 质检合格明细
        QuaInspectionLine qualifiedLine = new QuaInspectionLine();
        qualifiedLine.setInspectionLineId(2000L);
        qualifiedLine.setItemId(1001L);
        qualifiedLine.setBatchId(500L);
        qualifiedLine.setQualifiedQty(new BigDecimal("50"));
        Map<String, Object> inspectionDetail = new HashMap<>();
        inspectionDetail.put("lines", List.of(qualifiedLine));
        when(qualityService.getInspectionDetail(200L)).thenReturn(inspectionDetail);

        AgentTask result = orchestrator.startTask(
                "RECEIPT_INSPECTION_INBOUND", "收货质检入库任务", "REC-100", buildReceiptData(), 1L);

        // 五个智能体各执行一步
        verify(taskLogService, times(1)).startStep(anyLong(), eq(1), eq(AgentNames.RECEIVING), any(), any());
        verify(taskLogService, times(1)).startStep(anyLong(), eq(2), eq(AgentNames.QUALITY), any(), any());
        verify(taskLogService, times(1)).startStep(anyLong(), eq(3), eq(AgentNames.INBOUND), any(), any());
        verify(taskLogService, times(1)).startStep(anyLong(), eq(4), eq(AgentNames.INVENTORY), any(), any());
        verify(taskLogService, times(1)).startStep(anyLong(), eq(5), eq(AgentNames.AUDIT), any(), any());

        // 各 Service 被正确调用
        verify(receiptService, times(1)).createReceipt(any());
        verify(qualityService, times(1)).submitInspection(any());
        verify(inboundService, times(1)).createInboundOrder(eq(100L), eq(1L), eq(1L));
        verify(inboundService, times(1)).confirmInbound(any());
        verify(inventoryService, times(1)).queryInventoryByBatch(500L);
        verify(userService, times(1)).recordAuditLog(any());

        // 任务成功收尾
        verify(taskLogService, times(1)).finishTask(eq(1L), eq("SUCCESS"), isNull());
        assertEquals("SUCCESS", result.getStatus());
    }

    @Test
    @DisplayName("质检全部不合格：跳过入库，直接进入审计并成功收尾")
    void receiptInspection_allUnqualified_skipInbound() {
        // 质检结果为 0 合格
        QuaInspectionLine unqualifiedLine = new QuaInspectionLine();
        unqualifiedLine.setInspectionLineId(2000L);
        unqualifiedLine.setItemId(1001L);
        unqualifiedLine.setBatchId(500L);
        unqualifiedLine.setQualifiedQty(BigDecimal.ZERO);
        Map<String, Object> inspectionDetail = new HashMap<>();
        inspectionDetail.put("lines", List.of(unqualifiedLine));
        when(qualityService.getInspectionDetail(200L)).thenReturn(inspectionDetail);

        Map<String, Object> data = buildReceiptData();
        Map<String, Object> ov = new HashMap<>();
        ov.put("receiptLineId", 1000);
        ov.put("qualifiedQty", 0);
        ov.put("unqualifiedQty", 50);
        ov.put("lineResult", "UNQUALIFIED");
        data.put("inspection", List.of(ov));

        orchestrator.startTask("RECEIPT_INSPECTION_INBOUND", "收货质检入库任务", "REC-100", data, 1L);

        // 走过收货、质检、审计三步；入库与库存被跳过
        verify(taskLogService, times(1)).startStep(anyLong(), eq(1), eq(AgentNames.RECEIVING), any(), any());
        verify(taskLogService, times(1)).startStep(anyLong(), eq(2), eq(AgentNames.QUALITY), any(), any());
        verify(taskLogService, times(1)).startStep(anyLong(), eq(3), eq(AgentNames.AUDIT), any(), any());
        verify(inboundService, never()).confirmInbound(any());
        verify(inboundService, never()).createInboundOrder(anyLong(), anyLong(), any());
        verify(userService, times(1)).recordAuditLog(any());
        verify(taskLogService, times(1)).finishTask(eq(1L), eq("SUCCESS"), isNull());
    }

    @Test
    @DisplayName("非法任务类型：直接抛出业务异常")
    void invalidTaskType_throws() {
        assertThrows(BusinessException.class, () ->
                orchestrator.startTask("NOT_A_REAL_TYPE", "x", "x", new HashMap<>(), 1L));
    }

    private Map<String, Object> buildReceiptData() {
        Map<String, Object> data = new HashMap<>();
        data.put("supplierId", 1);
        data.put("warehouseId", 1);
        data.put("receivedBy", 1);
        List<Map<String, Object>> lines = new ArrayList<>();
        Map<String, Object> line = new HashMap<>();
        line.put("itemId", 1001);
        line.put("batchNo", "B-1");
        line.put("receivedQty", 50);
        lines.add(line);
        data.put("lines", lines);
        return data;
    }
}
