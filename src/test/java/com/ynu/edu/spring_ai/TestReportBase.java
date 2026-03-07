package com.ynu.edu.spring_ai;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 测试报告基类 - 提供统一的测试记录和报告功能
 */
public class TestReportBase {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * 记录测试成功
     * @param message 成功消息
     */
    protected void recordTestSuccess(String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        System.out.println("[" + timestamp + "] PASS " + message);
    }

    /**
     * 记录测试失败
     * @param message 失败消息
     * @param e 异常对象
     */
    protected void recordTestFailure(String message, Throwable e) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        System.out.println("[" + timestamp + "] FAIL " + message);
        if (e != null) {
            System.out.println("    错误类型: " + e.getClass().getSimpleName());
            System.out.println("    错误信息: " + e.getMessage());
            // 只打印栈跟踪的第一行，避免过多信息
            if (e.getStackTrace() != null && e.getStackTrace().length > 0) {
                System.out.println("    错误位置: " + e.getStackTrace()[0]);
            }
        }
    }

    /**
     * 打印测试开始信息
     * @param testClass 测试类名
     * @param testMethod 测试方法名
     * @param description 测试描述
     */
    protected void printTestStart(String testClass, String testMethod, String description) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("测试开始");
        System.out.println("- 测试类: " + testClass);
        System.out.println("- 测试方法: " + testMethod);
        System.out.println("- 测试描述: " + description);
        System.out.println("-".repeat(60));
    }

    /**
     * 打印测试结束信息
     * @param passed 是否通过
     * @param durationMs 耗时(毫秒)
     */
    protected void printTestEnd(boolean passed, long durationMs) {
        System.out.println("-".repeat(60));
        System.out.println("测试结果: " + (passed ? "PASS" : "FAIL"));
        System.out.println("执行耗时: " + durationMs + "ms");
        System.out.println("=".repeat(60) + "\n");
    }

    /**
     * 打印测试断言信息
     * @param assertion 断言描述
     * @param result 断言结果
     */
    protected void printAssertion(String assertion, boolean result) {
        System.out.println("   " + (result ? "PASS" : "FAIL") + " " + assertion);
    }
}