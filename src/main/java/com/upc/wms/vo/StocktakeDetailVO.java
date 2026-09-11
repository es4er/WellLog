package com.upc.wms.vo;

import com.upc.wms.entity.InvStocktakeOrder;
import lombok.Data;

import java.util.List;

@Data
public class StocktakeDetailVO {
    private InvStocktakeOrder order;
    private List<StocktakeLineVO> lines;
}
