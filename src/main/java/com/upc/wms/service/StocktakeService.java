package com.upc.wms.service;

import com.upc.wms.dto.StocktakeCountRequest;
import com.upc.wms.entity.InvAdjustmentOrder;
import com.upc.wms.entity.InvAdjustmentLine;
import com.upc.wms.entity.InvStocktakeDifference;
import com.upc.wms.entity.InvStocktakeOrder;
import com.upc.wms.vo.StocktakeDetailVO;

import java.util.List;
import java.util.Map;

/**
 * 库存盘点与库存调整服务接口。
 *
 * 核心链路：盘点是发现差异，调整是处理差异。
 * 库存管理员发起盘点 → 仓管员执行盘点并录入实盘 → 系统生成盘点差异
 * → 库存管理员确认差异 → 系统生成盘点调整单 → 调整单审核通过 → 修改 inv_inventory → 写入 inv_transaction
 */
public interface StocktakeService {

    InvStocktakeOrder createStocktake(Long warehouseId, String stocktakeType, String scope, Long createdBy);

    void generateStocktakeLines(Long stocktakeId);

    StocktakeDetailVO getStocktakeDetail(Long stocktakeId);

    void dispatchStocktake(Long stocktakeId, Long executorId);

    void submitCountResult(StocktakeCountRequest request);

    List<InvStocktakeDifference> generateDifference(Long stocktakeId);

    List<InvStocktakeDifference> listDifferences(Long stocktakeId);

    void confirmAllDifferences(Long stocktakeId);

    InvAdjustmentOrder createAdjustment(Long stocktakeId, String reason);

    void approveAdjustment(Long adjustmentId, Long approvedBy);

    void rejectAdjustment(Long adjustmentId);

    List<InvAdjustmentOrder> listAdjustmentOrders(String status);

    Map<String, Object> getAdjustmentDetail(Long adjustmentId);

    List<InvAdjustmentLine> createAdjustmentManually(String reason, List<Map<String, Object>> lines, Long createdBy);

    // Minimal list method returning VO for frontend list view
    List<com.upc.wms.vo.StocktakeListVO> list(Long warehouseId, String status, String keyword);
}
