package com.upc.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.upc.wms.entity.InvStocktakeOrder;
import com.upc.wms.vo.StocktakeListVO;
import com.upc.wms.vo.StocktakeOverviewVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface InvStocktakeOrderMapper extends BaseMapper<InvStocktakeOrder> {

    List<StocktakeListVO> selectByFilter(
        @Param("warehouseId") Long warehouseId,
        @Param("status") String status,
        @Param("keyword") String keyword
    );

    StocktakeOverviewVO selectOverview(@Param("warehouseId") Long warehouseId);
}
