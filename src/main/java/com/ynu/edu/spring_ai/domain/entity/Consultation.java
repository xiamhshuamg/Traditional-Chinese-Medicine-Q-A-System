package com.ynu.edu.spring_ai.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Consultation {
    private String patientName;
    private String patientGender;
    private Integer patientAge;

    private Long id;
    private Long doctorId;
    private Long patientId;

    private String status;          // ONGOING / FINISHED
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime lastActiveTime;

    private String chiefComplaint;
    private String presentHistory;
    private String tonguePulse;
    private String tcmDiagnosis;
    private String westernDiagnosis;
    private String prescriptionJson;
    private String summary;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
