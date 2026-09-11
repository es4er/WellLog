package com.upc.wms.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String userCode;
    private String password;
}
