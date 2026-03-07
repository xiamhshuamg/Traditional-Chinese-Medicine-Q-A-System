package com.ynu.edu.spring_ai.service;

import jakarta.annotation.PostConstruct;  // Spring Boot 3.x 使用这个
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient ragChatClient;
    private final EmbeddingModel embeddingModel;
    private final TestDataService testDataService;

    /**
     * 构建RAG向量库（使用测试数据）
     */
    @PostConstruct  // 现在使用 jakarta.annotation.PostConstruct
    public void buildRagIndex() {
        try {
            List<TestDataService.MedicalCase> cases = testDataService.loadTestData();

            List<Document> documents = cases.stream()
                    .map(caseData -> {
                        String content = String.format(
                                "患者症状: %s\n诊断: %s\n证型: %s\n治法: %s\n方剂: %s\n中药: %s",
                                caseData.getInput(),
                                caseData.getOutput().getDiagnosis(),
                                caseData.getOutput().getSyndrome(),
                                caseData.getOutput().getTreatment(),
                                caseData.getOutput().getFormula(),
                                caseData.getOutput().getHerbs()
                        );
                        return new Document(content, Map.of(
                                "caseId", caseData.hashCode(),
                                "symptoms", extractSymptoms(caseData.getInput()),
                                "syndrome", caseData.getOutput().getSyndrome(),
                                "formula", caseData.getOutput().getFormula()
                        ));
                    })
                    .collect(Collectors.toList());

            vectorStore.add(documents);
            log.info("RAG向量库构建完成，共 {} 条记录", documents.size());
        } catch (Exception e) {
            log.error("构建RAG向量库失败", e);
        }
    }
    
    /**
     * RAG方法推荐处方
     */
    public Map<String, Object> recommendWithRAG(String symptoms) {
        // 1. 检索相似病例
        SearchRequest request = SearchRequest.builder()
            .query(symptoms)
            .topK(3)
            .build();
        
        List<Document> similarCases = vectorStore.similaritySearch(request);
        
        // 2. 构建上下文
        StringBuilder context = new StringBuilder();
        context.append("基于以下相似病例进行分析：\n\n");
        
        for (int i = 0; i < similarCases.size(); i++) {
            Document doc = similarCases.get(i);
            context.append("病例 ").append(i + 1).append(":\n")
                   .append(doc.getText()).append("\n\n");
        }
        
        // 3. 调用LLM生成推荐
        String prompt = String.format("""
            你是一位中医专家，请基于以下相似病例和中医知识，
            为新的患者症状给出诊断和治疗方案。
            
            患者症状：%s
            
            %s
            
            请按以下格式输出：
            1. 诊断（包括证型、治法）
            2. 推荐方剂
            3. 中药组成及用量
            4. 配伍说明
            """, symptoms, context.toString());
        
        String recommendation = ragChatClient.prompt(prompt).call().content();
        
        // 4. 解析结果
        return Map.of(
            "method", "RAG",
            "symptoms", symptoms,
            "similarCasesCount", similarCases.size(),
            "recommendation", recommendation,
            "retrievedCases", similarCases.stream()
                .map(Document::getText)
                .collect(Collectors.toList())
        );
    }
    
    private String extractSymptoms(String text) {
        // 简单的症状提取
        String[] commonSymptoms = {
            "咳嗽", "咳痰", "发热", "头痛", "头晕", "腹痛", "腹泻", 
            "便秘", "失眠", "多梦", "疲倦", "乏力", "口干", "口苦",
            "咽痛", "咽干", "胸闷", "心悸", "腰痛", "关节痛"
        };
        
        List<String> found = Arrays.stream(commonSymptoms)
            .filter(text::contains)
            .collect(Collectors.toList());
        
        return String.join(",", found);
    }
}