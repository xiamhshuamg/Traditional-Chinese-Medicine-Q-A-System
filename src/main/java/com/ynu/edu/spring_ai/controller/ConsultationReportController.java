package com.ynu.edu.spring_ai.controller;

import com.ynu.edu.spring_ai.domain.vo.ConsultationReportVO;
import com.ynu.edu.spring_ai.service.ConsultationReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/consultations")
@RequiredArgsConstructor
public class ConsultationReportController {

    private final ConsultationReportService reportService;

    @GetMapping("/{id}/report")
    public ConsultationReportVO report(@PathVariable("id") Long consultationId) {
        return reportService.build(consultationId);
    }
    @GetMapping(value = "/{id}/report/html", produces = MediaType.TEXT_HTML_VALUE)
    public String reportHtml(@PathVariable("id") Long consultationId) {
        return reportService.buildHtml(consultationId);
    }
    // -------- 原始下载（不编辑）--------
    @GetMapping(value = "/{id}/report/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> reportPdf(@PathVariable("id") Long consultationId) {
        byte[] bytes = reportService.buildPdf(consultationId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=consultation-" + consultationId + ".pdf")
                .body(bytes);
    }
    // -------- 编辑后下载（POST 带 JSON）--------
    @PostMapping(value = "/{id}/report/pdf",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "application/pdf")
    public ResponseEntity<byte[]> reportPdfEdited(@PathVariable("id") Long consultationId,
                                                  @RequestBody ConsultationReportService.ReportDraft draft) {
        byte[] bytes = reportService.buildPdf(consultationId, draft);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=consultation-" + consultationId + ".pdf")
                .body(bytes);
    }
    @GetMapping(value = "/{id}/report/docx",
            produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> reportDocx(@PathVariable("id") Long consultationId) {
        byte[] bytes = reportService.buildDocx(consultationId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=consultation-" + consultationId + ".docx")
                .body(bytes);
    }
    @PostMapping(value = "/{id}/report/docx",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> reportDocxEdited(@PathVariable("id") Long consultationId,
                                                   @RequestBody ConsultationReportService.ReportDraft draft) {
        byte[] bytes = reportService.buildDocx(consultationId, draft);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=consultation-" + consultationId + ".docx")
                .body(bytes);
    }
}
