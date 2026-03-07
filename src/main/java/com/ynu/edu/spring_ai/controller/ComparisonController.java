package com.ynu.edu.spring_ai.controller;

import com.ynu.edu.spring_ai.repository.graph.KnowledgeGraphService;
import com.ynu.edu.spring_ai.service.KgComparisonService;
import com.ynu.edu.spring_ai.service.RagService;
import com.ynu.edu.spring_ai.service.TestDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comparison")
@RequiredArgsConstructor
@Slf4j
public class ComparisonController {
    
    private final RagService ragService;
    private final KgComparisonService kgComparisonService;
    private final TestDataService testDataService;
    
    /**
     * 单条症状对比测试
     */
    @PostMapping("/single")
    public Map<String, Object> compareSingle(@RequestBody Map<String, String> request) {
        String symptoms = request.get("symptoms");
        
        Map<String, Object> ragResult = ragService.recommendWithRAG(symptoms);
        Map<String, Object> kgResult = kgComparisonService.recommendWithKG(symptoms);
        
        return Map.of(
            "input", symptoms,
            "rag", ragResult,
            "knowledgeGraph", kgResult,
            "comparison", compareResults(ragResult, kgResult),
            "timestamp", LocalDateTime.now().toString()
        );
    }
    
    /**
     * 批量测试对比
     */
    @GetMapping("/batch")
    public List<Map<String, Object>> batchComparison(
            @RequestParam(defaultValue = "5") int count) {
        
        List<TestDataService.MedicalCase> testCases = testDataService.loadTestData();
        List<TestDataService.MedicalCase> selected = testCases.stream()
            .limit(Math.min(count, testCases.size()))
            .collect(Collectors.toList());
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (TestDataService.MedicalCase testCase : selected) {
            try {
                Map<String, Object> ragResult = ragService.recommendWithRAG(testCase.getInput());
                Map<String, Object> kgResult = kgComparisonService.recommendWithKG(testCase.getInput());
                
                Map<String, Object> comparison = Map.of(
                    "testCase", Map.of(
                        "input", testCase.getInput(),
                        "expected", testCase.getOutput()
                    ),
                    "rag", ragResult,
                    "knowledgeGraph", kgResult,
                    "comparison", compareResults(ragResult, kgResult)
                );
                
                results.add(comparison);
            } catch (Exception e) {
                log.error("测试用例执行失败: {}", testCase.getInput(), e);
            }
        }
        
        return results;
    }
    
    /**
     * 性能对比
     */
    @PostMapping("/performance")
    public Map<String, Object> performanceComparison(@RequestBody Map<String, String> request) {
        String symptoms = request.get("symptoms");
        int iterations = Integer.parseInt(request.getOrDefault("iterations", "10"));
        
        List<Long> ragTimes = new ArrayList<>();
        List<Long> kgTimes = new ArrayList<>();
        
        for (int i = 0; i < iterations; i++) {
            long ragStart = System.currentTimeMillis();
            ragService.recommendWithRAG(symptoms);
            ragTimes.add(System.currentTimeMillis() - ragStart);
            
            long kgStart = System.currentTimeMillis();
            kgComparisonService.recommendWithKG(symptoms);
            kgTimes.add(System.currentTimeMillis() - kgStart);
        }
        
        return Map.of(
            "iterations", iterations,
            "rag", Map.of(
                "avgTime", ragTimes.stream().mapToLong(Long::longValue).average().orElse(0),
                "minTime", ragTimes.stream().mapToLong(Long::longValue).min().orElse(0),
                "maxTime", ragTimes.stream().mapToLong(Long::longValue).max().orElse(0)
            ),
            "knowledgeGraph", Map.of(
                "avgTime", kgTimes.stream().mapToLong(Long::longValue).average().orElse(0),
                "minTime", kgTimes.stream().mapToLong(Long::longValue).min().orElse(0),
                "maxTime", kgTimes.stream().mapToLong(Long::longValue).max().orElse(0)
            )
        );
    }
    
    private Map<String, Object> compareResults(Map<String, Object> rag, Map<String, Object> kg) {
        // 简单的结果对比
        return Map.of(
            "recommendationLength", Map.of(
                "rag", ((String) rag.get("recommendation")).length(),
                "kg", ((String) kg.get("recommendation")).length()
            ),
            "hasStructure", Map.of(
                "rag", hasStructure((String) rag.get("recommendation")),
                "kg", hasStructure((String) kg.get("recommendation"))
            ),
            "mentionsHerbs", Map.of(
                "rag", mentionsHerbs((String) rag.get("recommendation")),
                "kg", mentionsHerbs((String) kg.get("recommendation"))
            )
        );
    }
    
    private boolean hasStructure(String text) {
        return text.contains("诊断") && text.contains("方剂") && text.contains("中药");
    }
    
    private boolean mentionsHerbs(String text) {
        String[] commonHerbs = {"黄芪", "人参", "当归", "白芍", "茯苓", "白术", "甘草"};
        return Arrays.stream(commonHerbs).anyMatch(text::contains);
    }
}