package com.upc.wms.service;

import com.upc.wms.dto.PrepLocationOptionVO;
import com.upc.wms.dto.PrepLocationRecommendVO;

import java.util.List;

/**
 * 备料区（生产领料暂存区）库位解析与推荐。
 */
public interface PrepAreaService {

    String ZONE_CODE = "ZONE-PREP";

    /** 兼容历史备件区编码 */
    String LEGACY_ZONE_CODE = "ZONE-SPARE";

    String DEFAULT_LOCATION_CODE = "PREP-01-01";

    /** 备料区库位编码前缀（排除历史 SP-* 备件区库位） */
    String LOCATION_CODE_PREFIX = "PREP-";

    /**
     * 解析备料区默认上架库位 ID。
     */
    Long resolvePrepLocationId(Long warehouseId);

    /**
     * 备料区全部库位及当前占用概况。
     */
    List<PrepLocationOptionVO> listPrepLocations(Long warehouseId);

    /**
     * 为拣货任务推荐备料库位（任务级：整单入同一格）。
     */
    PrepLocationRecommendVO recommendForPickingTask(Long pickingTaskId);

    /**
     * 校验库位属于指定仓库的备料区。
     */
    void validatePrepLocation(Long warehouseId, Long locationId);
}
