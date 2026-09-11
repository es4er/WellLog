package com.upc.wms.service;

import com.upc.wms.entity.MdBatch;
import com.upc.wms.entity.MdCustomer;
import com.upc.wms.entity.MdItem;
import com.upc.wms.entity.MdItemCategory;
import com.upc.wms.entity.MdSupplier;
import com.upc.wms.entity.MdUom;

import java.util.List;

/**
 * 基础资料服务接口：客户、供应商、单位、物料分类、物料、批次。
 */
public interface MasterDataService {

    List<MdItem> listItems();

    MdItem getItemById(Long itemId);

    MdItem getItemByCode(String itemCode);

    MdItem addItem(MdItem item);

    MdItem updateItem(MdItem item);

    void disableItem(Long itemId);

    List<MdCustomer> listCustomers();

    MdCustomer addCustomer(MdCustomer customer);

    List<MdSupplier> listSuppliers();

    MdSupplier addSupplier(MdSupplier supplier);

    List<MdUom> listUoms();

    List<MdItemCategory> listCategories();

    List<MdBatch> listBatchesByItem(Long itemId);

    MdBatch addBatch(MdBatch batch);

    void updateBatchQualityStatus(Long batchId, String qualityStatus);

    List<MdBatch> traceByCode(String traceCode);
}
