package com.ynu.edu.spring_ai;

import com.ynu.edu.spring_ai.service.ChatService;
import com.ynu.edu.spring_ai.repository.graph.KnowledgeGraphService;
import org.springframework.ai.chat.client.ChatClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterAll;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatServiceTest extends TestReportBase {

    private ChatService chatService;
    private ChatClient chatClient;
    private KnowledgeGraphService knowledgeGraphService;

    private static final List<TestResult> testResults = new ArrayList<>();

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        knowledgeGraphService = mock(KnowledgeGraphService.class);
        chatService = new ChatService(chatClient, knowledgeGraphService);
    }

    @Test
    @DisplayName("01-清理响应中的多余符号")
    void wb01_cleanResponseWithExtraSymbols() {
        long startTime = System.currentTimeMillis();
        printTestStart("ChatServiceTest", "wb01_cleanResponseWithExtraSymbols",
                "清理响应中的多余符号（---, ***, ###, ___）");

        try {
            System.out.println("输入文本：--- 测试 ---\\n*** 重要 ***\\n### 标题 ###\\n___ 内容 ___");

            // 测试清理响应
            String input = "--- 测试 ---\n*** 重要 ***\n### 标题 ###\n___ 内容 ___";
            String cleaned = chatService.cleanResponse(input);

            System.out.println("输出文本：" + cleaned);

            // 验证多余的符号被移除
            printAssertion("应移除 --- 符号", !cleaned.contains("---"));
            printAssertion("应移除 *** 符号", !cleaned.contains("***"));
            printAssertion("应移除 ### 符号", !cleaned.contains("###"));
            printAssertion("应移除 ___ 符号", !cleaned.contains("___"));
            printAssertion("应保留'测试'文本", cleaned.contains("测试"));
            printAssertion("应保留'重要'文本", cleaned.contains("重要"));
            printAssertion("应保留'标题'文本", cleaned.contains("标题"));
            printAssertion("应保留'内容'文本", cleaned.contains("内容"));

            // 使用JUnit断言
            assertAll(
                    () -> assertFalse(cleaned.contains("---"), "应移除 --- 符号"),
                    () -> assertFalse(cleaned.contains("***"), "应移除 *** 符号"),
                    () -> assertFalse(cleaned.contains("###"), "应移除 ### 符号"),
                    () -> assertFalse(cleaned.contains("___"), "应移除 ___ 符号"),
                    () -> assertTrue(cleaned.contains("测试"), "应保留文本内容"),
                    () -> assertTrue(cleaned.contains("重要"), "应保留重要内容"),
                    () -> assertTrue(cleaned.contains("标题"), "应保留标题内容"),
                    () -> assertTrue(cleaned.contains("内容"), "应保留内容部分")
            );

            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(true, duration);
            recordTestSuccess("成功清理响应中的多余符号");
            testResults.add(new TestResult("wb01_cleanResponseWithExtraSymbols", "01-清理响应中的多余符号", "PASS", duration));

        } catch (AssertionError e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            System.out.println("断言失败: " + e.getMessage());
            recordTestFailure("清理响应测试失败: " + e.getMessage(), e);
            testResults.add(new TestResult("wb01_cleanResponseWithExtraSymbols", "01-清理响应中的多余符号", "FAIL", duration));
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            System.out.println("异常发生: " + e.getMessage());
            recordTestFailure("清理响应测试失败: " + e.getMessage(), e);
            testResults.add(new TestResult("wb01_cleanResponseWithExtraSymbols", "01-清理响应中的多余符号", "FAIL", duration));
            throw e;
        }
    }

    @Test
    @DisplayName("02-处理null输入")
    void wb02_cleanResponseWithNull() {
        long startTime = System.currentTimeMillis();
        printTestStart("ChatServiceTest", "wb02_cleanResponseWithNull",
                "处理null输入应返回空字符串");

        try {
            System.out.println("输入文本：null");

            String cleaned = chatService.cleanResponse(null);

            System.out.println("输出文本：\"" + cleaned + "\"");

            printAssertion("null输入应返回空字符串", "".equals(cleaned));

            assertEquals("", cleaned, "null输入应返回空字符串");

            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(true, duration);
            recordTestSuccess("正确处理null输入");
            testResults.add(new TestResult("wb02_cleanResponseWithNull", "02-处理null输入", "PASS", duration));

        } catch (AssertionError e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            System.out.println("断言失败: " + e.getMessage());
            recordTestFailure("null处理测试失败: " + e.getMessage(), e);
            testResults.add(new TestResult("wb02_cleanResponseWithNull", "02-处理null输入", "FAIL", duration));
            throw e;
        }
    }

    @Test
    @DisplayName("03-处理空字符串输入")
    void wb03_cleanResponseWithEmptyString() {
        long startTime = System.currentTimeMillis();
        printTestStart("ChatServiceTest", "wb03_cleanResponseWithEmptyString",
                "处理空字符串输入应返回空字符串");

        try {
            System.out.println("输入文本：\"\" (空字符串)");

            String cleaned = chatService.cleanResponse("");

            System.out.println("输出文本：\"" + cleaned + "\"");

            printAssertion("空字符串输入应返回空字符串", "".equals(cleaned));

            assertEquals("", cleaned, "空字符串输入应返回空字符串");

            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(true, duration);
            recordTestSuccess("正确处理空字符串输入");
            testResults.add(new TestResult("wb03_cleanResponseWithEmptyString", "03-处理空字符串输入", "PASS", duration));

        } catch (AssertionError e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            System.out.println("断言失败: " + e.getMessage());
            recordTestFailure("空字符串处理测试失败: " + e.getMessage(), e);
            testResults.add(new TestResult("wb03_cleanResponseWithEmptyString", "03-处理空字符串输入", "FAIL", duration));
            throw e;
        }
    }

    @Test
    @DisplayName("04-正常文本保持不变")
    void wb04_cleanResponseWithValidText() {
        long startTime = System.currentTimeMillis();
        printTestStart("ChatServiceTest", "wb04_cleanResponseWithValidText",
                "正常文本（无多余符号）应保持不变");

        try {
            String input = "这是正常文本，没有多余符号。\n第二行内容。";
            System.out.println("输入文本：" + input);

            String cleaned = chatService.cleanResponse(input);

            System.out.println("输出文本：" + cleaned);

            printAssertion("正常文本应保持不变", input.equals(cleaned));

            assertEquals(input, cleaned, "正常文本应保持不变");

            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(true, duration);
            recordTestSuccess("正常文本清理后保持不变");
            testResults.add(new TestResult("wb04_cleanResponseWithValidText", "04-正常文本保持不变", "PASS", duration));

        } catch (AssertionError e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            System.out.println("断言失败: " + e.getMessage());
            recordTestFailure("正常文本处理测试失败: " + e.getMessage(), e);
            testResults.add(new TestResult("wb04_cleanResponseWithValidText", "04-正常文本保持不变", "FAIL", duration));
            throw e;
        }
    }

    @Test
    @DisplayName("05-混合内容清理")
    void wb05_cleanResponseWithMixedContent() {
        long startTime = System.currentTimeMillis();
        printTestStart("ChatServiceTest", "wb05_cleanResponseWithMixedContent",
                "混合内容（文本和符号混合）应正确清理");

        try {
            String input = "开头---中间***结尾###还有___";
            System.out.println("输入文本：" + input);

            String cleaned = chatService.cleanResponse(input);

            System.out.println("输出文本：" + cleaned);

            printAssertion("应移除连续---", !cleaned.contains("---"));
            printAssertion("应移除连续***", !cleaned.contains("***"));
            printAssertion("应移除连续###", !cleaned.contains("###"));
            printAssertion("应移除连续___", !cleaned.contains("___"));
            printAssertion("应保留'开头'", cleaned.contains("开头"));
            printAssertion("应保留'中间'", cleaned.contains("中间"));
            printAssertion("应保留'结尾'", cleaned.contains("结尾"));
            printAssertion("应保留'还有'", cleaned.contains("还有"));

            assertAll(
                    () -> assertFalse(cleaned.contains("---"), "应移除连续---"),
                    () -> assertFalse(cleaned.contains("***"), "应移除连续***"),
                    () -> assertFalse(cleaned.contains("###"), "应移除连续###"),
                    () -> assertFalse(cleaned.contains("___"), "应移除连续___"),
                    () -> assertTrue(cleaned.contains("开头"), "应保留开头"),
                    () -> assertTrue(cleaned.contains("中间"), "应保留中间"),
                    () -> assertTrue(cleaned.contains("结尾"), "应保留结尾"),
                    () -> assertTrue(cleaned.contains("还有"), "应保留还有")
            );

            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(true, duration);
            recordTestSuccess("混合内容清理正确");
            testResults.add(new TestResult("wb05_cleanResponseWithMixedContent", "05-混合内容清理", "PASS", duration));

        } catch (AssertionError e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            System.out.println("断言失败: " + e.getMessage());
            recordTestFailure("混合内容清理测试失败: " + e.getMessage(), e);
            testResults.add(new TestResult("wb05_cleanResponseWithMixedContent", "05-混合内容清理", "FAIL", duration));
            throw e;
        }
    }

    @Test
    @DisplayName("06-只有符号的处理")
    void wb06_cleanResponseWithOnlySymbols() {
        long startTime = System.currentTimeMillis();
        printTestStart("ChatServiceTest", "wb06_cleanResponseWithOnlySymbols",
                "只有连续符号（---***###___）应返回空字符串");

        try {
            String input = "---***###___";
            System.out.println("输入文本：" + input);

            String cleaned = chatService.cleanResponse(input);

            System.out.println("输出文本：\"" + cleaned + "\"");

            printAssertion("只有符号应返回空字符串", "".equals(cleaned));

            assertEquals("", cleaned, "只有符号应返回空字符串");

            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(true, duration);
            recordTestSuccess("只有符号的输入处理正确");
            testResults.add(new TestResult("wb06_cleanResponseWithOnlySymbols", "06-只有符号的处理", "PASS", duration));

        } catch (AssertionError e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            System.out.println("断言失败: " + e.getMessage());
            recordTestFailure("纯符号输入测试失败: " + e.getMessage(), e);
            testResults.add(new TestResult("wb06_cleanResponseWithOnlySymbols", "06-只有符号的处理", "FAIL", duration));
            throw e;
        }
    }

    @Test
    @DisplayName("07-空格和符号混合处理")
    void wb07_cleanResponseWithSpacesAndSymbols() {
        long startTime = System.currentTimeMillis();
        printTestStart("ChatServiceTest", "wb07_cleanResponseWithSpacesAndSymbols",
                "空格和符号混合（只有空格和符号）应返回空字符串");

        try {
            String input = "   ---    ***   \n   ###   ___   ";
            System.out.println("输入文本：\"" + input + "\"");

            String cleaned = chatService.cleanResponse(input);

            System.out.println("输出文本：\"" + cleaned + "\"");

            printAssertion("只有空格和符号应返回空字符串", "".equals(cleaned));

            assertEquals("", cleaned, "只有空格和符号应返回空字符串");

            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(true, duration);
            recordTestSuccess("空格和符号混合处理正确");
            testResults.add(new TestResult("wb07_cleanResponseWithSpacesAndSymbols", "07-空格和符号混合处理", "PASS", duration));

        } catch (AssertionError e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            System.out.println("断言失败: " + e.getMessage());
            recordTestFailure("空格符号混合测试失败: " + e.getMessage(), e);
            testResults.add(new TestResult("wb07_cleanResponseWithSpacesAndSymbols", "07-空格和符号混合处理", "FAIL", duration));
            throw e;
        }
    }

    @Test
    @DisplayName("08-单个破折号处理")
    void wb08_cleanResponseWithSingleDash() {
        long startTime = System.currentTimeMillis();
        printTestStart("ChatServiceTest", "wb08_cleanResponseWithSingleDash",
                "单个破折号应保留");

        try {
            String input = "这是一个-测试";
            System.out.println("输入文本：" + input);

            String cleaned = chatService.cleanResponse(input);

            System.out.println("输出文本：" + cleaned);

            printAssertion("单个破折号应保留", cleaned.contains("-"));
            printAssertion("文本应保持不变", input.equals(cleaned));

            assertTrue(cleaned.contains("-"), "单个破折号应保留");
            assertEquals(input, cleaned, "单个破折号文本应保持不变");

            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(true, duration);
            recordTestSuccess("单个破折号正确处理");
            testResults.add(new TestResult("wb08_cleanResponseWithSingleDash", "08-单个破折号处理", "PASS", duration));

        } catch (AssertionError e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            System.out.println("断言失败: " + e.getMessage());
            recordTestFailure("单个破折号测试失败: " + e.getMessage(), e);
            testResults.add(new TestResult("wb08_cleanResponseWithSingleDash", "08-单个破折号处理", "FAIL", duration));
            throw e;
        }
    }

    @Test
    @DisplayName("09-两个符号的处理")
    void wb09_cleanResponseWithDoubleSymbols() {
        long startTime = System.currentTimeMillis();
        printTestStart("ChatServiceTest", "wb09_cleanResponseWithDoubleSymbols",
                "两个符号（**）应保留");

        try {
            String input = "这是一**测试**";
            System.out.println("输入文本：" + input);

            String cleaned = chatService.cleanResponse(input);

            System.out.println("输出文本：" + cleaned);

            printAssertion("两个符号应保留", cleaned.contains("**"));

            assertTrue(cleaned.contains("**"), "两个符号应保留");

            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(true, duration);
            recordTestSuccess("两个符号正确处理");
            testResults.add(new TestResult("wb09_cleanResponseWithDoubleSymbols", "09-两个符号的处理", "PASS", duration));

        } catch (AssertionError e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            System.out.println("断言失败: " + e.getMessage());
            recordTestFailure("两个符号测试失败: " + e.getMessage(), e);
            testResults.add(new TestResult("wb09_cleanResponseWithDoubleSymbols", "09-两个符号的处理", "FAIL", duration));
            throw e;
        }
    }

    @Test
    @DisplayName("10-空格处理验证")
    void wb10_cleanResponsePreservesWhitespace() {
        long startTime = System.currentTimeMillis();
        printTestStart("ChatServiceTest", "wb10_cleanResponsePreservesWhitespace",
                "文本内部的空格应保留，首尾空格应去除");

        try {
            String input = "   前面有空格   中间有空格   后面有空格   ";
            System.out.println("输入文本：\"" + input + "\"");

            String cleaned = chatService.cleanResponse(input);

            System.out.println("输出文本：\"" + cleaned + "\"");

            printAssertion("应正确处理空格", "前面有空格   中间有空格   后面有空格".equals(cleaned.trim()));

            assertEquals("前面有空格   中间有空格   后面有空格", cleaned.trim(), "应正确处理空格");

            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(true, duration);
            recordTestSuccess("空格处理正确");
            testResults.add(new TestResult("wb10_cleanResponsePreservesWhitespace", "10-空格处理验证", "PASS", duration));

        } catch (AssertionError e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            System.out.println("断言失败: " + e.getMessage());
            recordTestFailure("空格处理测试失败: " + e.getMessage(), e);
            testResults.add(new TestResult("wb10_cleanResponsePreservesWhitespace", "10-空格处理验证", "FAIL", duration));
            throw e;
        }
    }

    @AfterAll
    static void printTestSummary() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ChatServiceTest 测试结果汇总");
        System.out.println("=".repeat(80));
        System.out.printf("%-5s %-35s %-30s %-10s %-10s\n",
                "序号", "测试方法", "测试描述", "状态", "耗时(ms)");
        System.out.println("-".repeat(80));

        // 按方法名排序（wb01, wb02, ..., wb10）
        testResults.sort(Comparator.comparing(r -> {
            String methodName = r.methodName;
            // 提取数字部分（例如从"wb01_cleanResponseWithExtraSymbols"提取"01"）
            String numStr = methodName.substring(2, 4); // 取第2-3个字符（01, 02, ...）
            return Integer.parseInt(numStr);
        }));

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