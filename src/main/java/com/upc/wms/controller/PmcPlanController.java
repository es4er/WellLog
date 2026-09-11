package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.dto.IntegrationSyncResult;
import com.upc.wms.dto.PmcAssistantChatRequest;
import com.upc.wms.dto.PmcAssistantChatResponse;
import com.upc.wms.dto.PmcCoordinationNoticeRequest;
import com.upc.wms.dto.PmcCoordinationNoticeVO;
import com.upc.wms.dto.PmcWorkbenchOverview;
import com.upc.wms.dto.PlanCreateRequest;
import com.upc.wms.entity.PmcProductionPlan;
import com.upc.wms.entity.PmcRequisitionOrder;
import com.upc.wms.service.BomService;
import com.upc.wms.service.IntegrationService;
import com.upc.wms.service.PmcAssistantService;
import com.upc.wms.service.PmcCoordinationService;
import com.upc.wms.service.PmcPlanService;
import com.upc.wms.service.PmcWorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class PmcPlanController {

    private final PmcPlanService pmcPlanService;
    private final PmcWorkbenchService pmcWorkbenchService;
    private final PmcAssistantService pmcAssistantService;
    private final IntegrationService integrationService;
    private final BomService bomService;
    private final PmcCoordinationService pmcCoordinationService;

    @GetMapping("/pmc/workbench/overview")
    public Result<PmcWorkbenchOverview> workbenchOverview() {
        return Result.success(pmcWorkbenchService.getOverview());
    }

    /**
     * PMC 工作台助手：先判意图再分流（问答 / 动作确认 / 澄清）。
     */
    @PostMapping("/pmc/assistant/chat")
    public Result<PmcAssistantChatResponse> assistantChat(@RequestBody PmcAssistantChatRequest request) {
        return Result.success(pmcAssistantService.chat(request));
    }

    @PostMapping("/pmc/integration/sync-orders")
    public Result<IntegrationSyncResult> syncOrdersFromExternal() {
        return Result.success(integrationService.syncOrdersFromExternal());
    }

    @GetMapping("/plan/list")
    public Result<List<PmcProductionPlan>> listPlans() {
        return Result.success(pmcPlanService.listPlans());
    }

    @GetMapping("/plan/detail")
    public Result<Map<String, Object>> planDetail(@RequestParam Long planId) {
        return Result.success(pmcPlanService.getPlanDetail(planId));
    }

    @PostMapping("/plan/create")
    public Result<PmcProductionPlan> createPlan(@RequestBody PlanCreateRequest request) {
        return Result.success(pmcPlanService.createPlan(request.getPlan(), request.getLines()));
    }

    @GetMapping("/requisition/list")
    public Result<List<PmcRequisitionOrder>> listRequisitions() {
        return Result.success(pmcPlanService.listRequisitions());
    }

    @PostMapping("/requisition/create-by-order")
    public Result<PmcRequisitionOrder> createByOrder(@RequestParam Long orderId,
                                                     @RequestParam(required = false) Long requestedBy,
                                                     @RequestParam(required = false) String dept) {
        return Result.success(pmcPlanService.createRequisitionByOrder(orderId, requestedBy, dept));
    }

    @PostMapping("/requisition/create-by-plan")
    public Result<PmcRequisitionOrder> createByPlan(@RequestParam Long planId,
                                                    @RequestParam(required = false) Long requestedBy,
                                                    @RequestParam(required = false) String dept) {
        return Result.success(pmcPlanService.createRequisitionByPlan(planId, requestedBy, dept));
    }

    @GetMapping("/requisition/detail")
    public Result<Map<String, Object>> requisitionDetail(@RequestParam Long requisitionId) {
        return Result.success(pmcPlanService.getRequisitionDetail(requisitionId));
    }

    /** 按成品查询启用中的 BOM（联调 / 证据悬停） */
    @GetMapping("/bom/by-product")
    public Result<Map<String, Object>> bomByProduct(@RequestParam Long itemId) {
        Map<String, Object> bom = bomService.getBomByProduct(itemId);
        if (bom == null) {
            return Result.error(404, "成品 " + itemId + " 未配置启用中的 BOM");
        }
        return Result.success(bom);
    }

    /** PMC 出库协同：发起催出库 / 补料调拨建议 / 复核跟进通知 */
    @PostMapping("/pmc/coordination/notice")
    public Result<PmcCoordinationNoticeVO> createCoordinationNotice(@RequestBody PmcCoordinationNoticeRequest request) {
        return Result.success(pmcCoordinationService.createNotice(request));
    }

    /** 按目标岗位查询协同通知（仓管收件箱 / 通用） */
    @GetMapping("/pmc/coordination/notices")
    public Result<List<PmcCoordinationNoticeVO>> listCoordinationNotices(
            @RequestParam(required = false, defaultValue = "WAREHOUSE") String targetRole,
            @RequestParam(required = false) String status) {
        return Result.success(pmcCoordinationService.listByTarget(targetRole, status));
    }

    /** 查询某 PMC 已发起的协同通知 */
    @GetMapping("/pmc/coordination/notices/mine")
    public Result<List<PmcCoordinationNoticeVO>> listMyCoordinationNotices(
            @RequestParam(required = false) Long createdBy) {
        return Result.success(pmcCoordinationService.listCreatedBy(createdBy));
    }

    /** 标记协同通知已读 */
    @PostMapping("/pmc/coordination/notice/{noticeId}/read")
    public Result<PmcCoordinationNoticeVO> readCoordinationNotice(@PathVariable Long noticeId) {
        return Result.success(pmcCoordinationService.markRead(noticeId));
    }

    /** 标记协同通知已处理 */
    @PostMapping("/pmc/coordination/notice/{noticeId}/handle")
    public Result<PmcCoordinationNoticeVO> handleCoordinationNotice(@PathVariable Long noticeId,
                                                                    @RequestBody(required = false) Map<String, String> body) {
        String handledBy = body != null ? body.get("handledBy") : null;
        return Result.success(pmcCoordinationService.markHandled(noticeId, handledBy));
    }
}
