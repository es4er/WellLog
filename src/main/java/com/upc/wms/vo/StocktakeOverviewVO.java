package com.upc.wms.vo;

import lombok.Data;

@Data
public class StocktakeOverviewVO {
    private Integer totalCount;
    private Integer draftCount;
    private Integer pendingCount;
    private Integer countingCount;
    private Integer differencePendingCount;
    private Integer adjustmentCreatedCount;
    private Integer completedCount;
}
