package com.upc.wms.service;

import com.upc.wms.entity.PmcProductionPlan;
import com.upc.wms.entity.PmcProductionPlanLine;
import com.upc.wms.entity.PmcRequisitionOrder;

import java.util.List;
import java.util.Map;

/**
 * 生产计划与生产领料单服务接口。
 */
public interface PmcPlanService {

    List<PmcProductionPlan> listPlans();

    Map<String, Object> getPlanDetail(Long planId);

    PmcProductionPlan createPlan(PmcProductionPlan plan, List<PmcProductionPlanLine> lines);

    PmcRequisitionOrder createRequisitionByOrder(Long orderId, Long requestedBy, String dept);

    PmcRequisitionOrder createRequisitionByPlan(Long planId, Long requestedBy, String dept);

    PmcRequisitionOrder createRequisitionByPlan(Long planId, Long orderId, Long requestedBy, String dept);

    /** 从客户/MES 订单生成生产计划（含 BOM 物料行） */
    PmcProductionPlan createPlanFromOrder(Long orderId, Long createdBy);

    /**
     * 齐套校验通过后按齐套率生成领料单。
     * <ul>
     *   <li>齐套率 = 100%：完整领料单</li>
     *   <li>90% ≤ 齐套率 &lt; 100%：部分领料单（仅含库存满足的物料行）</li>
     *   <li>齐套率 &lt; 90%：不生成正式领料单</li>
     * </ul>
     */
    PmcRequisitionOrder createRequisitionAfterKitting(Long planId, Long orderId, Long requestedBy, String dept,
                                                      int kittingRate, List<Map<String, Object>> kittingLines);

    /**
     * 将 InventoryAgent 齐套/缺料分析结果回写到生产计划。
     */
    void saveKittingSnapshot(Long planId, int kittingRate,
                             List<Map<String, Object>> kittingLines,
                             List<Map<String, Object>> shortageAnalysis);

    /** 通过领料单反查订单关联的生产计划 ID（若尚未生成领料单则返回 null） */
    Long findPlanIdByOrderId(Long orderId);

    boolean orderHasPlan(Long orderId);

    Map<String, Object> getRequisitionDetail(Long requisitionId);

    List<PmcRequisitionOrder> listRequisitions();
}
