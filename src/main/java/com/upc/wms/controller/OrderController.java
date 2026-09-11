package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.dto.OrderCreateRequest;
import com.upc.wms.entity.OrdCustomerOrder;
import com.upc.wms.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/list")
    public Result<List<OrdCustomerOrder>> list() {
        return Result.success(orderService.listOrders());
    }

    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam Long orderId) {
        return Result.success(orderService.getOrderDetail(orderId));
    }

    @PostMapping("/create")
    public Result<OrdCustomerOrder> create(@RequestBody OrderCreateRequest request) {
        return Result.success(orderService.createOrder(request.getOrder(), request.getLines()));
    }

    @PostMapping("/approve")
    public Result<Void> approve(@RequestParam Long orderId, @RequestParam Long approvedBy) {
        orderService.approveOrder(orderId, approvedBy);
        return Result.success();
    }

    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestParam Long orderId) {
        orderService.cancelOrder(orderId);
        return Result.success();
    }
}
