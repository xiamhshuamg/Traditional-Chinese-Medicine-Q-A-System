package com.ynu.edu.spring_ai.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConsultMessage {
    private Long id;
    private Long consultationId;

    private String senderType;   // DOCTOR / PATIENT / AI / SYSTEM
    private Long senderId;       // 可选
    private String role;         // user / assistant / system（给大模型区分）
    private String content;

    private LocalDateTime createdAt;
}
