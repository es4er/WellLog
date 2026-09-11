package com.upc.wms.common;

import lombok.Getter;

/**
 * 业务异常，供 Service 层抛出（如库存不足、状态非法、单据不存在等）。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
