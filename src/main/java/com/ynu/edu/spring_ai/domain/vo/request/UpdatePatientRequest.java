package com.ynu.edu.spring_ai.domain.vo.request;

import lombok.Data;

@Data
public class UpdatePatientRequest {
    private String name;
    private String gender;
    private Integer age;
    private String phone;
    private String idNumber;
}