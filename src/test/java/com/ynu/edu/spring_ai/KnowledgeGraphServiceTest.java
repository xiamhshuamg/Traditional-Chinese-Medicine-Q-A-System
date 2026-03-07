package com.ynu.edu.spring_ai;

import com.ynu.edu.spring_ai.repository.graph.KnowledgeGraphService;
import com.ynu.edu.spring_ai.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterAll;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeGraphServiceTest extends TestReportBase {

    private static final List<TestResult> testResults = new ArrayList<>();

    @Test
    @DisplayName("知识图谱匹配测试套件")
    void knowledge_graph_matching_test_suite() {
        long startTime = System.currentTimeMillis();
        printTestStart("KnowledgeGraphServiceTest", "knowledge_graph_matching_test_suite",
                "测试知识图谱症状匹配和分析功能");

        try {
            System.out.println("知识图谱匹配测试");
            System.out.println("-".repeat(50));

            // 1. 测试空输入
            long test1Start = System.currentTimeMillis();
            try {
                System.out.println("\n测试 1: 空输入处理");
                System.out.println("   描述: null输入应返回空列表");

                Neo4jClient client = mock(Neo4jClient.class);
                FileService fileService = mock(FileService.class);

                KnowledgeGraphService svc = new KnowledgeGraphService(client, fileService);
                List<String> symptoms = svc.findSymptomNamesInRawText(null);

                System.out.println("   输入: null");
                System.out.println("   返回症状数: " + symptoms.size());
                System.out.println("   期望: 空列表 (0个症状)");

                assertTrue(symptoms.isEmpty(), "null输入应返回空列表");

                long duration = System.currentTimeMillis() - test1Start;
                testResults.add(new TestResult("test_01", "01-空输入处理", "PASS", duration));
                System.out.println("   通过");

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - test1Start;
                testResults.add(new TestResult("test_01", "01-空输入处理", "FAIL", duration));
                System.out.println("   失败: " + e.getMessage());
                throw e;
            }

            // 2. 测试空白输入
            long test2Start = System.currentTimeMillis();
            try {
                System.out.println("\n测试 2: 空白输入处理");
                System.out.println("   描述: 空白输入应返回空列表");

                Neo4jClient client = mock(Neo4jClient.class);
                FileService fileService = mock(FileService.class);

                KnowledgeGraphService svc = new KnowledgeGraphService(client, fileService);
                List<String> symptoms = svc.findSymptomNamesInRawText("   ");

                System.out.println("   输入: \"   \" (空格)");
                System.out.println("   返回症状数: " + symptoms.size());
                System.out.println("   期望: 空列表 (0个症状)");

                assertTrue(symptoms.isEmpty(), "空白输入应返回空列表");

                long duration = System.currentTimeMillis() - test2Start;
                testResults.add(new TestResult("test_02", "02-空白输入处理", "PASS", duration));
                System.out.println("   通过");

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - test2Start;
                testResults.add(new TestResult("test_02", "02-空白输入处理", "FAIL", duration));
                System.out.println("   失败: " + e.getMessage());
                throw e;
            }

            // 3. 测试正常症状提取
            long test3Start = System.currentTimeMillis();
            try {
                System.out.println("\n测试 3: 正常症状提取");
                System.out.println("   描述: 从文本中提取'咳嗽、痰多、发热'3个症状");

                Neo4jClient client = mock(Neo4jClient.class, RETURNS_DEEP_STUBS);
                FileService fileService = mock(FileService.class);

                // 模拟返回症状
                when(client.query(anyString()).bind(any()).to("t").fetch().all())
                        .thenReturn(List.of(
                                Map.of("name", "咳嗽"),
                                Map.of("name", "痰多"),
                                Map.of("name", "发热")
                        ));

                KnowledgeGraphService svc = new KnowledgeGraphService(client, fileService);
                String inputText = "患者咳嗽痰多，伴有发热";
                List<String> out = svc.findSymptomNamesInRawText(inputText);

                System.out.println("   输入: \"" + inputText + "\"");
                System.out.println("   返回症状: " + out);
                System.out.println("   返回症状数: " + out.size());
                System.out.println("   期望: 包含'咳嗽、痰多、发热'的3个症状");

                assertEquals(3, out.size(), "应提取3个症状");
                assertTrue(out.contains("咳嗽"), "应包含咳嗽");
                assertTrue(out.contains("痰多"), "应包含痰多");
                assertTrue(out.contains("发热"), "应包含发热");

                long duration = System.currentTimeMillis() - test3Start;
                testResults.add(new TestResult("test_03", "03-正常症状提取", "PASS", duration));
                System.out.println("   通过");

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - test3Start;
                testResults.add(new TestResult("test_03", "03-正常症状提取", "FAIL", duration));
                System.out.println("   失败: " + e.getMessage());
                throw e;
            }

            // 4. 测试去重和过滤
            long test4Start = System.currentTimeMillis();
            try {
                System.out.println("\n测试 4: 症状去重和过滤");
                System.out.println("   描述: 重复症状应去重，空值应过滤");

                Neo4jClient client = mock(Neo4jClient.class, RETURNS_DEEP_STUBS);
                FileService fileService = mock(FileService.class);

                when(client.query(anyString()).bind(any()).to("t").fetch().all())
                        .thenReturn(List.of(
                                Map.of("name", "咳嗽"),
                                Map.of("name", "咳嗽"),
                                Map.of("name", ""),
                                Map.of("name", "发热")
                        ));

                KnowledgeGraphService svc = new KnowledgeGraphService(client, fileService);
                String inputText = "咳嗽咳嗽发热";
                List<String> out = svc.findSymptomNamesInRawText(inputText);

                System.out.println("   输入: \"" + inputText + "\"");
                System.out.println("   返回症状: " + out);
                System.out.println("   返回症状数: " + out.size());
                System.out.println("   期望: 去重后保留'咳嗽、发热'2个症状");

                assertEquals(2, out.size(), "应去除重复和空值，保留2个症状");
                assertTrue(out.contains("咳嗽"), "应包含咳嗽");
                assertTrue(out.contains("发热"), "应包含发热");

                long duration = System.currentTimeMillis() - test4Start;
                testResults.add(new TestResult("test_04", "04-症状去重和过滤", "PASS", duration));
                System.out.println("   通过");

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - test4Start;
                testResults.add(new TestResult("test_04", "04-症状去重和过滤", "FAIL", duration));
                System.out.println("   失败: " + e.getMessage());
                throw e;
            }

            // 5. 测试无匹配分析
            long test5Start = System.currentTimeMillis();
            try {
                System.out.println("\n测试 5: 无匹配症状分析");
                System.out.println("   描述: 无匹配症状时应返回无效分析");

                Neo4jClient client = mock(Neo4jClient.class, RETURNS_DEEP_STUBS);
                FileService fileService = mock(FileService.class);

                when(client.query(anyString()).bind(any()).to("t").fetch().all())
                        .thenReturn(List.of());

                KnowledgeGraphService svc = new KnowledgeGraphService(client, fileService);
                String inputText = "普通文本无特殊症状";
                KnowledgeGraphService.KnowledgeGraphResult result =
                        svc.analyzeWithKnowledgeGraph(inputText);

                System.out.println("   输入: \"" + inputText + "\"");
                System.out.println("   分析是否有效: " + result.isHasValidAnalysis());
                System.out.println("   分析文本: \"" + result.getAnalysisText() + "\"");
                System.out.println("   匹配症状数: " + result.getMatchedSymptoms().size());
                System.out.println("   匹配证型数: " + result.getMatchedSyndromes().size());
                System.out.println("   期望: 无效分析，空列表");

                assertFalse(result.isHasValidAnalysis(), "无匹配时应标记为无效分析");
                assertEquals("", result.getAnalysisText(), "无匹配时分析文本应为空");
                assertTrue(result.getMatchedSymptoms().isEmpty(), "匹配症状列表应为空");
                assertTrue(result.getMatchedSyndromes().isEmpty(), "匹配证型列表应为空");

                long duration = System.currentTimeMillis() - test5Start;
                testResults.add(new TestResult("test_05", "05-无匹配症状分析", "PASS", duration));
                System.out.println("   通过");

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - test5Start;
                testResults.add(new TestResult("test_05", "05-无匹配症状分析", "FAIL", duration));
                System.out.println("   失败: " + e.getMessage());
                throw e;
            }

            System.out.println("\n知识图谱匹配测试完成");
            System.out.println("   通过: " + testResults.stream().filter(r -> "PASS".equals(r.status)).count() + 
                             "/" + testResults.size() + " (" + 
                             (testResults.stream().filter(r -> "PASS".equals(r.status)).count() * 100 / testResults.size()) + "%)");

            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(testResults.stream().allMatch(r -> "PASS".equals(r.status)), duration);
            recordTestSuccess("知识图谱匹配测试完成: " + testResults.stream().filter(r -> "PASS".equals(r.status)).count() + 
                            "/" + testResults.size() + " 通过");

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            recordTestFailure("知识图谱匹配测试失败: " + e.getMessage(), e);
            throw e;
        }
    }
    
    @AfterAll
    static void printTestSummary() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("KnowledgeGraphServiceTest 测试结果汇总");
        System.out.println("=".repeat(80));
        System.out.printf("%-5s %-35s %-30s %-10s %-10s\n", 
                         "序号", "测试方法", "测试描述", "状态", "耗时(ms)");
        System.out.println("-".repeat(80));
        
        int passed = 0;
        int failed = 0;
        long totalTime = 0;
        
        for (int i = 0; i < testResults.size(); i++) {
            TestResult result = testResults.get(i);
            System.out.printf("%-5d %-35s %-30s %-10s %-10d\n", 
                             i + 1, 
                             result.methodName, 
                             result.displayName, 
                             result.status, 
                             result.duration);
            
            if ("PASS".equals(result.status)) {
                passed++;
            } else {
                failed++;
            }
            totalTime += result.duration;
        }
        
        System.out.println("-".repeat(80));
        System.out.printf("总计: %d 个测试用例 | 通过: %d | 失败: %d | 成功率: %.1f%% | 总耗时: %dms\n",
                         testResults.size(), passed, failed, 
                         (passed * 100.0 / testResults.size()), totalTime);
        System.out.println("=".repeat(80));
    }
    
    static class TestResult {
        String methodName;
        String displayName;
        String status;
        long duration;
        
        TestResult(String methodName, String displayName, String status, long duration) {
            this.methodName = methodName;
            this.displayName = displayName;
            this.status = status;
            this.duration = duration;
        }
    }
}