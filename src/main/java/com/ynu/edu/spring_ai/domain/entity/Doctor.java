package com.ynu.edu.spring_ai.domain.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class Doctor {
    private Long id;
    private String username;
    private String password;
    private String name;
    private String dept;

    private Integer totalConsultCount;
    private Integer todayConsultCount;
    private LocalDate lastConsultDate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
