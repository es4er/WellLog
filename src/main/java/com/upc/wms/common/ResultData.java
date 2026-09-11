package com.upc.wms.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一接口返回结果（配合 ResultCode 使用）。
 */
@Data
public class ResultData<T> implements Serializable {

    private int code;
    private String message;
    private T data;

    public static <T> ResultData<T> success() {
        return build(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> ResultData<T> success(T data) {
        return build(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> ResultData<T> fail(ResultCode resultCode) {
        return build(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> ResultData<T> fail(int code, String message) {
        return build(code, message, null);
    }

    private static <T> ResultData<T> build(int code, String message, T data) {
        ResultData<T> result = new ResultData<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
}
