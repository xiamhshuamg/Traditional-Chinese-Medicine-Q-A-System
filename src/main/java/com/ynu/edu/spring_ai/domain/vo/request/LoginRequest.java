package com.ynu.edu.spring_ai.domain.vo.request;

import lombok.Data;

@Data
public class LoginRequest {
    private Long doctorId;
    private String name;
}