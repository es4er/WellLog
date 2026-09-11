package com.upc.wms.dto;

import com.upc.wms.entity.OrdCustomerOrder;
import com.upc.wms.entity.OrdCustomerOrderLine;
import lombok.Data;

import java.util.List;

@Data
public class OrderCreateRequest {
    private OrdCustomerOrder order;
    private List<OrdCustomerOrderLine> lines;
}
