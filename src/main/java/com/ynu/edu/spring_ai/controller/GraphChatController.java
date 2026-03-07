package com.ynu.edu.spring_ai.controller;

import com.ynu.edu.spring_ai.repository.graph.PrescriptionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai/graph")
@CrossOrigin
public class GraphChatController {

    private final PrescriptionService prescriptionService;

    public GraphChatController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    /** 纯文本输入，返回 JSON（包含 usedGraph/syndromes/stats/explanation） */
    @PostMapping(value = "/recommend", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> recommendText(@RequestBody String symptomText) {
        Map<String, Object> result = prescriptionService.recommend(symptomText == null ? "" : symptomText.trim());
        return ResponseEntity.ok(result);
    }

    /** 表单/查询串风格，返回 explanation 纯文本（适合现有前端快速接） */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public String chat(@RequestParam String prompt) {
        Map<String, Object> result = prescriptionService.recommend(prompt);
        Object exp = result.get("explanation");
        return exp == null ? "" : String.valueOf(exp);
    }
}