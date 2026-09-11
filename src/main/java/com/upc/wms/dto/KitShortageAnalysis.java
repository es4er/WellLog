package com.upc.wms.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 齐套/缺料分析结果。
 * 可用库存口径：已入库 + 质检合格/放行 + 未冻结 + availableQty &gt; 0。
 */
@Data
public class KitShortageAnalysis {
    private Long itemId;
    private BigDecimal requiredQty = BigDecimal.ZERO;
    /** 齐套可用量（合格放行且 AVAILABLE） */
    private BigDecimal kitAvailableQty = BigDecimal.ZERO;
    /** 账面在库总量（含待检/冻结等） */
    private BigDecimal onhandQty = BigDecimal.ZERO;
    /** 待检未放行数量 */
    private BigDecimal pendingInspectionQty = BigDecimal.ZERO;
    /** 质量异常（不合格等）数量 */
    private BigDecimal qualityBlockedQty = BigDecimal.ZERO;
    /** 库位/状态不可用（冻结、非 AVAILABLE）数量 */
    private BigDecimal locationUnavailableQty = BigDecimal.ZERO;
    private boolean kitReady;
    /** REAL_SHORTAGE / QUALITY_PENDING / QUALITY_ABNORMAL / LOCATION_UNAVAILABLE，齐套时为 null */
    private String shortageType;
    private String shortageTypeLabel;
    private BigDecimal shortageQty = BigDecimal.ZERO;

    public static final String REAL_SHORTAGE = "REAL_SHORTAGE";
    public static final String QUALITY_PENDING = "QUALITY_PENDING";
    public static final String QUALITY_ABNORMAL = "QUALITY_ABNORMAL";
    public static final String LOCATION_UNAVAILABLE = "LOCATION_UNAVAILABLE";

    public static String labelOf(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case REAL_SHORTAGE -> "真实缺料";
            case QUALITY_PENDING -> "质量未放行";
            case QUALITY_ABNORMAL -> "质量异常";
            case LOCATION_UNAVAILABLE -> "库位/批次不可用";
            default -> type;
        };
    }
}
