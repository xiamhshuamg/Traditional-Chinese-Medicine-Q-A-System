package com.ynu.edu.spring_ai.domain.vo;

import com.ynu.edu.spring_ai.domain.vo.request.HerbItemVORequest;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ConsultationReportVO {
    private Long consultationId;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String doctorName;
    private String doctorDept;

    private Long patientId;
    private String patientName;
    private String patientGender;
    private Integer patientAge; // 由 birthday 计算

    // 结构化字段（如果 consultation 表里有就直接用）
    private String chiefComplaint;
    private String presentHistory;
    private String tonguePulse;
    private String tcmDiagnosis;
    private String westernDiagnosis;
    private String summary;

    // 从最后一条 AI 消息里拆出来（你现在会存“【思路概述】...【建议】...”）
    private String aiThought;
    private String aiSuggestion;

    // 药材清单：优先用 consultation.prescriptionJson；没有就从 aiSuggestion 里解析
    private List<HerbItemVORequest> herbs;
}
