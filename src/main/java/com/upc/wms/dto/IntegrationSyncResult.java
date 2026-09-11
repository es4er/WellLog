package com.upc.wms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class IntegrationSyncResult {
    private Integer syncedCount;
    private Integer skippedCount;
    private Integer pendingReview;
    private String syncedAt;
    private String source;
    private List<String> orderNos = new ArrayList<>();
    private List<Long> messageIds = new ArrayList<>();
    private String message;
}
