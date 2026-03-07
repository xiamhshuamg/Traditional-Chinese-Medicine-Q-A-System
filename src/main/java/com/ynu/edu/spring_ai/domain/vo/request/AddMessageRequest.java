package com.ynu.edu.spring_ai.domain.vo.request;

import lombok.Data;

@Data
public class AddMessageRequest {
    private String senderType; // DOCTOR / PATIENT / AI / SYSTEM
    private Long senderId;
    private String role;       // user / assistant / system
    private String content;
}