package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 拣货提交请求：逐行回填实际拣货库存行与数量。
 */
@Data
public class PickingSubmitRequest {
    private Long pickingTaskId;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long pickingLineId;
        private BigDecimal actualPickQty;
    }
}
