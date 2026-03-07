package com.ynu.edu.spring_ai.domain.vo.request;

import lombok.Data;

@Data
public class HerbItemVORequest {
    private String name;
    private String dose;  // "10"
    private String unit;  // "g" / "克"
    private String note;  // 炮制/加减等备注
}
