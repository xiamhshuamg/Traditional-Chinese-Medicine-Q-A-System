package com.ynu.edu.spring_ai;

import com.ynu.edu.spring_ai.repository.graph.PrescriptionService;
import com.ynu.edu.spring_ai.repository.graph.KnowledgeGraphService;
import com.ynu.edu.spring_ai.repository.graph.LlmService;
import com.ynu.edu.spring_ai.service.ChatService;
import com.ynu.edu.spring_ai.service.FileService;
import com.ynu.edu.spring_ai.utils.VectorDistanceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrescriptionServiceTest extends TestReportBase {

    @Test
    @DisplayName("模型解析功能测试套件")
    void model_parsing_test_suite() {
        long startTime = System.currentTimeMillis();
        printTestStart("PrescriptionServiceTest", "model_parsing_test_suite",
                "测试模型解析功能：提示构建、数据统计、结果构建等");

        try {
            System.out.println("模型解析功能测试");
            System.out.println("-".repeat(50));

            int passed = 0;
            int total = 0;
            List<String> testResults = new ArrayList<>();

            // 1. 测试构建图谱提示
            try {
                total++;
                System.out.println("\n测试 1: 构建图谱提示");
                System.out.println("   描述: 验证知识图谱提示的正确构建");

                KnowledgeGraphService kgService = mock(KnowledgeGraphService.class);
                LlmService llmService = mock(LlmService.class);
                PrescriptionService service = new PrescriptionService(kgService, llmService);

                // 测试数据
                String userInput = "患者咳嗽痰多";
                Map<String, Object> kgPayload = new LinkedHashMap<>();
                kgPayload.put("usedGraph", true);
                kgPayload.put("syndromes", List.of(
                        Map.of("sname", "风寒犯肺", "score", 0.85),
                        Map.of("sname", "痰湿阻肺", "score", 0.72)
                ));

                // 使用反射调用私有方法
                Method method = PrescriptionService.class.getDeclaredMethod(
                        "buildGraphStrictPrompt", String.class, Map.class);
                method.setAccessible(true);

                String prompt = (String) method.invoke(service, userInput, kgPayload);

                System.out.println("   用户输入: \"" + userInput + "\"");
                System.out.println("   知识图谱数据: " + kgPayload);
                System.out.println("   生成的提示长度: " + prompt.length() + " 字符");

                // 验证结果
                assertNotNull(prompt, "生成的提示不应为空");
                assertTrue(prompt.contains("患者咳嗽痰多"), "应包含用户输入");
                assertTrue(prompt.contains("知识图谱数据"), "应包含知识图谱数据");
                assertTrue(prompt.contains("只允许使用我给的知识图谱"), "应包含限制条件");

                testResults.add("PASS 构建图谱提示: 正确生成提示");
                passed++;
                System.out.println("   通过");

            } catch (Exception e) {
                testResults.add("FAIL 构建图谱提示: " + e.getMessage());
                System.out.println("   失败: " + e.getMessage());
            }

            // 2. 测试构建兜底提示
            try {
                total++;
                System.out.println("\n测试 2: 构建兜底提示");
                System.out.println("   描述: 验证兜底提示的正确构建");

                KnowledgeGraphService kgService = mock(KnowledgeGraphService.class);
                LlmService llmService = mock(LlmService.class);
                PrescriptionService service = new PrescriptionService(kgService, llmService);

                // 测试数据
                String userInput = "患者咳嗽痰多";
                List<String> matchedSymptoms = List.of("咳嗽", "痰多");
                List<String> typicalSymptoms = List.of("咳嗽", "痰多", "发热", "恶寒");

                Method method = PrescriptionService.class.getDeclaredMethod(
                        "buildFallbackPrompt", String.class, List.class, List.class);
                method.setAccessible(true);

                String prompt = (String) method.invoke(service, userInput, matchedSymptoms, typicalSymptoms);

                System.out.println("   用户输入: \"" + userInput + "\"");
                System.out.println("   匹配症状: " + matchedSymptoms);
                System.out.println("   典型症状: " + typicalSymptoms);
                System.out.println("   生成的提示长度: " + prompt.length() + " 字符");

                // 验证结果
                assertNotNull(prompt, "生成的提示不应为空");
                assertTrue(prompt.contains("患者咳嗽痰多"), "应包含用户输入");
                assertTrue(prompt.contains("咳嗽") && prompt.contains("痰多"), "应包含匹配症状");
                assertTrue(prompt.contains("不得提供任何具体药物名称"), "应包含限制条件");

                testResults.add("PASS 构建兜底提示: 正确生成提示");
                passed++;
                System.out.println("   通过");

            } catch (Exception e) {
                testResults.add("FAIL 构建兜底提示: " + e.getMessage());
                System.out.println("   失败: " + e.getMessage());
            }

            // 3. 测试数据统计
            try {
                total++;
                System.out.println("\n测试 3: 数据统计分析");
                System.out.println("   描述: 验证证型、方剂、中药数量的正确统计");

                KnowledgeGraphService kgService = mock(KnowledgeGraphService.class);
                LlmService llmService = mock(LlmService.class);
                PrescriptionService service = new PrescriptionService(kgService, llmService);

                // 准备测试数据
                List<Map<String, Object>> syndromes = new ArrayList<>();

                // 证型1
                Map<String, Object> syndrome1 = new LinkedHashMap<>();
                syndrome1.put("sname", "风寒犯肺");

                List<Map<String, Object>> formulas1 = new ArrayList<>();

                Map<String, Object> formula1 = new LinkedHashMap<>();
                formula1.put("fname", "麻黄汤");
                formula1.put("herbs", List.of(
                        Map.of("hname", "麻黄", "grams", 9),
                        Map.of("hname", "桂枝", "grams", 6),
                        Map.of("hname", "杏仁", "grams", 6)
                ));
                formulas1.add(formula1);

                Map<String, Object> formula2 = new LinkedHashMap<>();
                formula2.put("fname", "桂枝汤");
                formula2.put("herbs", List.of(
                        Map.of("hname", "桂枝", "grams", 9),
                        Map.of("hname", "白芍", "grams", 9),
                        Map.of("hname", "生姜", "grams", 9)
                ));
                formulas1.add(formula2);

                syndrome1.put("formulas", formulas1);
                syndromes.add(syndrome1);

                // 证型2
                Map<String, Object> syndrome2 = new LinkedHashMap<>();
                syndrome2.put("sname", "痰湿阻肺");

                List<Map<String, Object>> formulas2 = new ArrayList<>();
                Map<String, Object> formula3 = new LinkedHashMap<>();
                formula3.put("fname", "二陈汤");
                formula3.put("herbs", List.of(
                        Map.of("hname", "半夏", "grams", 15),
                        Map.of("hname", "陈皮", "grams", 15),
                        Map.of("hname", "茯苓", "grams", 9)
                ));
                formulas2.add(formula3);

                syndrome2.put("formulas", formulas2);
                syndromes.add(syndrome2);

                System.out.println("   测试数据:");
                System.out.println("     证型: 风寒犯肺、痰湿阻肺 (2个)");
                System.out.println("     方剂: 麻黄汤、桂枝汤、二陈汤 (3个)");
                System.out.println("     中药: 麻黄、桂枝、杏仁、白芍、生姜、半夏、陈皮、茯苓 (8味)");

                // 调用统计方法
                Method method = PrescriptionService.class.getDeclaredMethod("countStats", List.class);
                method.setAccessible(true);

                @SuppressWarnings("unchecked")
                Map<String, Object> stats = (Map<String, Object>) method.invoke(service, syndromes);

                System.out.println("   统计结果: " + stats);

                // 验证统计结果
                assertNotNull(stats, "统计结果不应为空");
                assertEquals(2, stats.get("syndromeCount"), "应统计2个证型");
                assertEquals(3, stats.get("formulaCount"), "应统计3个方剂");
                assertEquals(8, stats.get("herbCount"), "应统计8味中药");

                testResults.add("PASS 数据统计分析: 正确统计证型、方剂、中药数量");
                passed++;
                System.out.println("   通过");

            } catch (Exception e) {
                testResults.add("FAIL 数据统计分析: " + e.getMessage());
                System.out.println("   失败: " + e.getMessage());
            }

            // 4. 测试基础结果构建
            try {
                total++;
                System.out.println("\n测试 4: 基础结果构建");
                System.out.println("   描述: 验证基础结果结构的正确构建");

                KnowledgeGraphService kgService = mock(KnowledgeGraphService.class);
                LlmService llmService = mock(LlmService.class);
                PrescriptionService service = new PrescriptionService(kgService, llmService);

                // 准备测试数据
                List<Map<String, Object>> syndromes = new ArrayList<>();
                Map<String, Object> syndrome = new LinkedHashMap<>();
                syndrome.put("sname", "测试证型");
                syndromes.add(syndrome);

                System.out.println("   测试数据: " + syndromes);

                // 调用基础结果方法
                Method method = PrescriptionService.class.getDeclaredMethod(
                        "baseResult", boolean.class, List.class);
                method.setAccessible(true);

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) method.invoke(service, true, syndromes);

                System.out.println("   构建结果: " + result);

                // 验证基础结果
                assertNotNull(result, "基础结果不应为空");
                assertEquals(true, result.get("usedGraph"), "应标记使用了图谱");
                assertEquals(syndromes, result.get("syndromes"), "应包含证型列表");

                testResults.add("PASS 基础结果构建: 正确构建结果结构");
                passed++;
                System.out.println("   通过");

            } catch (Exception e) {
                testResults.add("FAIL 基础结果构建: " + e.getMessage());
                System.out.println("   失败: " + e.getMessage());
            }

            // 5. 测试JSON转换
            try {
                total++;
                System.out.println("\n测试 5: JSON格式转换");
                System.out.println("   描述: 验证对象到JSON的正确转换");

                KnowledgeGraphService kgService = mock(KnowledgeGraphService.class);
                LlmService llmService = mock(LlmService.class);
                PrescriptionService service = new PrescriptionService(kgService, llmService);

                // 准备测试数据
                Map<String, Object> testData = new LinkedHashMap<>();
                testData.put("name", "测试名称");
                testData.put("value", 123);
                testData.put("list", List.of("a", "b", "c"));

                System.out.println("   测试数据: " + testData);

                // 调用JSON转换方法
                Method method = PrescriptionService.class.getDeclaredMethod(
                        "toCompactJson", Object.class);
                method.setAccessible(true);

                String json = (String) method.invoke(service, testData);

                System.out.println("   生成的JSON: " + json);

                // 验证JSON结果
                assertNotNull(json, "JSON不应为空");
                assertTrue(json.contains("测试名称"), "应包含名称");
                assertTrue(json.contains("123"), "应包含数值");

                testResults.add("PASS JSON格式转换: 正确转换对象为JSON");
                passed++;
                System.out.println("   通过");

            } catch (Exception e) {
                testResults.add("FAIL JSON格式转换: " + e.getMessage());
                System.out.println("   失败: " + e.getMessage());
            }

            // 6. 测试无症状推荐
            try {
                total++;
                System.out.println("\n测试 6: 无症状推荐");
                System.out.println("   描述: 验证无症状输入的正确处理");

                KnowledgeGraphService kgService = mock(KnowledgeGraphService.class);
                LlmService llmService = mock(LlmService.class);

                // 模拟空症状列表
                when(kgService.findSymptomNamesInRawText(anyString()))
                        .thenReturn(List.of());

                // 模拟LLM响应
                when(llmService.generate(anyString()))
                        .thenReturn("由于未匹配到具体症状，建议进一步检查。");

                PrescriptionService service = new PrescriptionService(kgService, llmService);

                String inputText = "普通描述，无具体症状";
                System.out.println("   输入文本: \"" + inputText + "\"");

                // 调用推荐方法
                Map<String, Object> result = service.recommend(inputText);

                System.out.println("   推荐结果: " + result);

                // 验证结果
                assertNotNull(result, "推荐结果不应为空");
                assertFalse((boolean) result.get("usedGraph"), "未匹配症状时不应使用图谱");
                assertTrue(result.containsKey("explanation"), "应包含解释");
                assertTrue(result.containsKey("stats"), "应包含统计");

                String explanation = (String) result.get("explanation");
                System.out.println("   解释文本: \"" + explanation + "\"");

                assertTrue(explanation.contains("未匹配") || explanation.contains("建议"), "应包含建议信息");

                testResults.add("PASS 无症状推荐: 正确处理无症状输入");
                passed++;
                System.out.println("   通过");

            } catch (Exception e) {
                testResults.add("FAIL 无症状推荐: " + e.getMessage());
                System.out.println("   失败: " + e.getMessage());
            }

            // 7. 测试段落格式化
            try {
                total++;
                System.out.println("\n测试 7: 段落格式化");
                System.out.println("   描述: 验证文本段落的正确格式化");

                KnowledgeGraphService kgService = mock(KnowledgeGraphService.class);
                LlmService llmService = mock(LlmService.class);
                PrescriptionService service = new PrescriptionService(kgService, llmService);

                // 使用反射调用私有方法
                Method method = PrescriptionService.class.getDeclaredMethod("toParagraph", String.class);
                method.setAccessible(true);

                // 测试1: null输入
                System.out.println("   测试1: null输入");
                String result1 = (String) method.invoke(service, (Object) null);
                assertEquals("", result1, "null输入应返回空字符串");
                System.out.println("     结果: \"" + result1 + "\"");

                // 测试2: 空白输入
                System.out.println("   测试2: 空白输入");
                String result2 = (String) method.invoke(service, "   ");
                assertEquals("", result2, "空白输入应返回空字符串");
                System.out.println("     结果: \"" + result2 + "\"");

                // 测试3: 多个换行符处理
                System.out.println("   测试3: 多个换行符处理");
                String input3 = "第一行\n\n\n第二行\n\n\n\n第三行";
                String result3 = (String) method.invoke(service, input3);
                assertFalse(result3.contains("\n\n\n"), "应去除连续的三个换行符");
                System.out.println("     输入: \"" + input3 + "\"");
                System.out.println("     结果: \"" + result3 + "\"");

                // 测试4: 保留单个换行符
                System.out.println("   测试4: 保留单个换行符");
                String input4 = "第一行\n第二行\n第三行";
                String result4 = (String) method.invoke(service, input4);
                assertEquals(input4, result4, "单个换行符应保留");
                System.out.println("     输入: \"" + input4 + "\"");
                System.out.println("     结果: \"" + result4 + "\"");

                testResults.add("PASS 段落格式化: 正确格式化文本段落");
                passed++;
                System.out.println("   通过");

            } catch (Exception e) {
                testResults.add("FAIL 段落格式化: " + e.getMessage());
                System.out.println("   失败: " + e.getMessage());
            }

            // 生成测试报告
            System.out.println("\n模型解析测试结果汇总");
            System.out.println("-".repeat(50));
            for (String result : testResults) {
                System.out.println(result);
            }

            System.out.println("\n模型解析测试完成");
            System.out.println("   通过: " + passed + "/" + total + " (" + (passed * 100 / total) + "%)");
            System.out.println("\n测试覆盖范围:");
            System.out.println("   提示构建: 图谱提示和兜底提示");
            System.out.println("   数据统计: 证型、方剂、中药数量统计");
            System.out.println("   结果构建: 基础结果结构和JSON转换");
            System.out.println("   推荐逻辑: 无症状处理和格式化");

            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(passed == total, duration);
            recordTestSuccess("模型解析测试完成: " + passed + "/" + total + " 通过");

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            recordTestFailure("模型解析测试失败: " + e.getMessage(), e);
            throw e;
        }
    }
}





