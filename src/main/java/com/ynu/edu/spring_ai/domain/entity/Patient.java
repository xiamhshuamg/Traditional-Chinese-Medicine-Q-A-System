package com.ynu.edu.spring_ai.domain.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class Patient {
    private Long id;
    private String name;
    private String gender;
    private LocalDate birthday;
    private String phone;
    private String idNumber;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
