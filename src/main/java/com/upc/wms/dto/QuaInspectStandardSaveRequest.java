package com.upc.wms.dto;

import com.upc.wms.entity.QuaInspectStandard;
import com.upc.wms.entity.QuaInspectStandardLine;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuaInspectStandardSaveRequest {
    private QuaInspectStandard standard;
    private List<QuaInspectStandardLine> lines = new ArrayList<>();
}
