package com.upc.wms.service;

import com.upc.wms.entity.OrdCustomerOrder;
import com.upc.wms.entity.OrdCustomerOrderLine;

import java.util.List;
import java.util.Map;

/**
 * 客户订单服务接口。
 */
public interface OrderService {

    List<OrdCustomerOrder> listOrders();

    Map<String, Object> getOrderDetail(Long orderId);

    OrdCustomerOrder createOrder(OrdCustomerOrder order, List<OrdCustomerOrderLine> lines);

    OrdCustomerOrder findByOrderNo(String orderNo);

    void approveOrder(Long orderId, Long approvedBy);

    void cancelOrder(Long orderId);
}
