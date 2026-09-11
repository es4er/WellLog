package com.upc.wms.controller;

import com.upc.wms.common.Result;
import com.upc.wms.entity.MdBatch;
import com.upc.wms.entity.MdCustomer;
import com.upc.wms.entity.MdItem;
import com.upc.wms.entity.MdSupplier;
import com.upc.wms.service.MasterDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/master")
@RequiredArgsConstructor
public class MasterDataController {

    private final MasterDataService masterDataService;

    @GetMapping("/item/list")
    public Result<List<MdItem>> listItems() {
        return Result.success(masterDataService.listItems());
    }

    @GetMapping("/item/{id}")
    public Result<MdItem> itemDetail(@PathVariable("id") Long id) {
        return Result.success(masterDataService.getItemById(id));
    }

    @PostMapping("/item/add")
    public Result<MdItem> addItem(@RequestBody MdItem item) {
        return Result.success(masterDataService.addItem(item));
    }

    @PutMapping("/item/update")
    public Result<MdItem> updateItem(@RequestBody MdItem item) {
        return Result.success(masterDataService.updateItem(item));
    }

    @PutMapping("/item/{id}/disable")
    public Result<Void> disableItem(@PathVariable("id") Long id) {
        masterDataService.disableItem(id);
        return Result.success();
    }

    @GetMapping("/customer/list")
    public Result<List<MdCustomer>> listCustomers() {
        return Result.success(masterDataService.listCustomers());
    }

    @PostMapping("/customer/add")
    public Result<MdCustomer> addCustomer(@RequestBody MdCustomer customer) {
        return Result.success(masterDataService.addCustomer(customer));
    }

    @GetMapping("/supplier/list")
    public Result<List<MdSupplier>> listSuppliers() {
        return Result.success(masterDataService.listSuppliers());
    }

    @PostMapping("/supplier/add")
    public Result<MdSupplier> addSupplier(@RequestBody MdSupplier supplier) {
        return Result.success(masterDataService.addSupplier(supplier));
    }

    @GetMapping("/batch/list")
    public Result<List<MdBatch>> listBatches(@RequestParam Long itemId) {
        return Result.success(masterDataService.listBatchesByItem(itemId));
    }

    @PostMapping("/batch/add")
    public Result<MdBatch> addBatch(@RequestBody MdBatch batch) {
        return Result.success(masterDataService.addBatch(batch));
    }

    @GetMapping("/batch/trace")
    public Result<List<MdBatch>> traceByCode(@RequestParam String traceCode) {
        return Result.success(masterDataService.traceByCode(traceCode));
    }
}
