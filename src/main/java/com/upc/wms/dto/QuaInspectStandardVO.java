package com.upc.wms.dto;

import com.upc.wms.entity.QuaInspectStandard;
import com.upc.wms.entity.QuaInspectStandardLine;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuaInspectStandardVO extends QuaInspectStandard {
    private String mdItemCode;
    private String mdItemName;
    private Integer lineCount;
    private List<QuaInspectStandardLineVO> lines = new ArrayList<>();

    @Data
    public static class QuaInspectStandardLineVO extends QuaInspectStandardLine {
        private String itemCode;
        private String itemName;
        private String itemType;
    }
}
