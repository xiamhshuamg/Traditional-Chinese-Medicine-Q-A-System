package com.ynu.edu.spring_ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
public class TestDataService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public List<MedicalCase> loadTestData() {
        try {
            // 从resources读取测试数据
            InputStream is = getClass().getResourceAsStream("/test_cases.json");
            return objectMapper.readValue(is, 
                objectMapper.getTypeFactory().constructCollectionType(
                    List.class, MedicalCase.class));
        } catch (Exception e) {
            throw new RuntimeException("加载测试数据失败", e);
        }
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MedicalCase {
        private String instruction;
        private String input;  // 患者症状描述
        private ExpectedOutput output;  // 期望输出
        
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class ExpectedOutput {
            private String diagnosis;
            private String syndrome;  // 证型
            private String treatment; // 治法
            private String formula;   // 方剂
            private String herbs;     // 中药
        }
    }
}