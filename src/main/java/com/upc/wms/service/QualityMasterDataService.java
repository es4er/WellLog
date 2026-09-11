package com.upc.wms.service;

import com.upc.wms.dto.QuaInspectStandardSaveRequest;
import com.upc.wms.dto.QuaInspectStandardVO;
import com.upc.wms.entity.MdItem;
import com.upc.wms.entity.QuaInspectItem;

import java.util.List;
import java.util.Map;

public interface QualityMasterDataService {

    List<QuaInspectItem> listInspectItems(String keyword, String itemType, String status);

    QuaInspectItem getInspectItem(Long id);

    QuaInspectItem saveInspectItem(QuaInspectItem item);

    void disableInspectItem(Long id);

    List<QuaInspectStandardVO> listStandards(String keyword, String status, Long mdItemId);

    QuaInspectStandardVO getStandardDetail(Long standardId);

    QuaInspectStandardVO saveStandard(QuaInspectStandardSaveRequest request);

    void disableStandard(Long standardId);

    /** 按物料解析启用中的检验标准；无专属则回退通用标准 */
    QuaInspectStandardVO resolveStandardForItem(Long mdItemId);

    /** 转为检测执行页 standards 列表结构 */
    List<Map<String, Object>> toExecutionStandards(QuaInspectStandardVO standard);

    List<MdItem> listSelectableItems(String keyword);
}
