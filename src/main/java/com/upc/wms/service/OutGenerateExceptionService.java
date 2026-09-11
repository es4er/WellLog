package com.upc.wms.service;

import com.upc.wms.entity.OutGenerateException;

import java.util.List;
import java.util.Map;

/**
 * 出库单生成异常：Agent 缺料/失败时登记，人工处理后可重试生成。
 */
public interface OutGenerateExceptionService {

    /**
     * 根据 Agent 任务上下文登记或更新一条 OPEN 异常（同一领料单合并为一条）。
     */
    OutGenerateException recordFromAgent(Long taskId, String taskType, String status,
                                         String message, Map<String, Object> contextData,
                                         Map<String, Object> resultData);

    /**
     * 人工标记已处理。
     */
    OutGenerateException resolve(Long exceptionId, String result);

    /**
     * 出库单生成成功后，自动关闭该领料单下未处理的生成异常。
     */
    void autoResolveByRequisition(Long requisitionId, String outboundNo);

    List<OutGenerateException> listAll();

    List<OutGenerateException> listOpen();

    OutGenerateException getById(Long exceptionId);
}
