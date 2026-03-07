package com.ynu.edu.spring_ai.service;

import com.ynu.edu.spring_ai.repository.graph.KnowledgeGraphService;
import com.ynu.edu.spring_ai.repository.graph.PrescriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KgComparisonService {
    
    private final KnowledgeGraphService knowledgeGraphService;
    private final PrescriptionService prescriptionService;
    private final ChatClient chatClient;
    
    /**
     * 知识图谱方法推荐处方
     */
    public Map<String, Object> recommendWithKG(String symptoms) {
        long startTime = System.currentTimeMillis();
        
        // 1. 知识图谱分析
        KnowledgeGraphService.KnowledgeGraphResult kgResult = 
            knowledgeGraphService.analyzeWithKnowledgeGraph(symptoms);
        
        // 2. 处方推荐
        Map<String, Object> prescriptionResult = prescriptionService.recommend(symptoms);
        
        // 3. 生成解释（如果需要）
        String kgAnalysis = kgResult.isHasValidAnalysis() ? 
            kgResult.getAnalysisText() : "未从知识图谱找到匹配项";
        
        String prompt = String.format("""
            基于知识图谱分析结果，为患者症状提供综合诊断和治疗建议。
            
            患者症状：%s
            
            知识图谱分析结果：
            %s
            
            处方推荐结果：
            %s
            
            请给出完整的诊断和治疗方案。
            """, symptoms, kgAnalysis, prescriptionResult.get("explanation"));
        
        String recommendation = chatClient.prompt(prompt).call().content();
        
        long endTime = System.currentTimeMillis();
        
        return Map.of(
            "method", "KnowledgeGraph",
            "symptoms", symptoms,
            "kgAnalysis", kgResult,
            "prescriptionResult", prescriptionResult,
            "recommendation", recommendation,
            "processingTime", endTime - startTime,
            "hasValidAnalysis", kgResult.isHasValidAnalysis(),
            "matchedSymptoms", kgResult.getMatchedSymptoms(),
            "matchedSyndromes", kgResult.getMatchedSyndromes()
        );
    }
}