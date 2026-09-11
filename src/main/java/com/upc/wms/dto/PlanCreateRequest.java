package com.upc.wms.dto;

import com.upc.wms.entity.PmcProductionPlan;
import com.upc.wms.entity.PmcProductionPlanLine;
import lombok.Data;

import java.util.List;

@Data
public class PlanCreateRequest {
    private PmcProductionPlan plan;
    private List<PmcProductionPlanLine> lines;
}
