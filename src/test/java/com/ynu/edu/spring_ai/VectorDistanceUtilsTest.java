package com.ynu.edu.spring_ai;

import org.junit.jupiter.api.Test;
import com.ynu.edu.spring_ai.utils.VectorDistanceUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterAll;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class VectorDistanceUtilsTest extends TestReportBase {

    private static final List<TestResult> testResults = new ArrayList<>();
    
    @Test
    @DisplayName("向量计算测试套件")
    void vector_calculation_test_suite() {
        long startTime = System.currentTimeMillis();
        printTestStart("VectorDistanceUtilsTest", "vector_calculation_test_suite", 
                      "测试向量距离计算功能（欧氏距离、余弦相似度）");
        
        try {
            System.out.println("向量计算测试");
            System.out.println("----------------------------------------");
            
            // 1. 欧氏距离测试 - 相同向量
            long test1Start = System.currentTimeMillis();
            try {
                System.out.println("\n测试 1: 相同向量欧氏距离为0");
                
                float[] a = {1.0f, 2.0f, 3.0f};
                float[] b = {1.0f, 2.0f, 3.0f};
                double distance = VectorDistanceUtils.euclideanDistance(a, b);
                
                assertEquals(0.0, distance, 1e-9, "相同向量欧氏距离应为0");
                
                long duration = System.currentTimeMillis() - test1Start;
                testResults.add(new TestResult("test_01", "01-相同向量欧氏距离", "PASS", duration));
                System.out.println("   结果: 通过");
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - test1Start;
                testResults.add(new TestResult("test_01", "01-相同向量欧氏距离", "FAIL", duration));
                System.out.println("   结果: 失败 - " + e.getMessage());
                throw e;
            }
            
            // 2. 欧氏距离测试 - 不同向量
            long test2Start = System.currentTimeMillis();
            try {
                System.out.println("\n测试 2: 不同向量欧氏距离计算");
                
                float[] a = {0.0f, 0.0f};
                float[] b = {3.0f, 4.0f};
                double distance = VectorDistanceUtils.euclideanDistance(a, b);
                
                assertEquals(5.0, distance, 1e-9, "向量(0,0)到(3,4)距离应为5");
                
                long duration = System.currentTimeMillis() - test2Start;
                testResults.add(new TestResult("test_02", "02-不同向量欧氏距离", "PASS", duration));
                System.out.println("   结果: 通过");
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - test2Start;
                testResults.add(new TestResult("test_02", "02-不同向量欧氏距离", "FAIL", duration));
                System.out.println("   结果: 失败 - " + e.getMessage());
                throw e;
            }
            
            // 3. 欧氏距离测试 - 维度不匹配
            long test3Start = System.currentTimeMillis();
            try {
                System.out.println("\n测试 3: 维度不匹配异常");
                
                float[] a = {1.0f, 2.0f};
                float[] b = {1.0f, 2.0f, 3.0f};
                
                Exception exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> VectorDistanceUtils.euclideanDistance(a, b),
                    "维度不匹配时应抛出IllegalArgumentException"
                );
                
                assertTrue(exception.getMessage().contains("维度") || 
                          exception.getMessage().contains("相同的维度"),
                    "异常消息应包含维度信息");
                
                long duration = System.currentTimeMillis() - test3Start;
                testResults.add(new TestResult("test_03", "03-维度不匹配异常", "PASS", duration));
                System.out.println("   结果: 通过");
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - test3Start;
                testResults.add(new TestResult("test_03", "03-维度不匹配异常", "FAIL", duration));
                System.out.println("   结果: 失败 - " + e.getMessage());
                throw e;
            }
            
            // 4. 欧氏距离测试 - 空向量
            long test4Start = System.currentTimeMillis();
            try {
                System.out.println("\n测试 4: 空向量异常");
                
                float[] a = null;
                float[] b = {1.0f, 2.0f};
                
                Exception exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> VectorDistanceUtils.euclideanDistance(a, b),
                    "空向量时应抛出IllegalArgumentException"
                );
                
                long duration = System.currentTimeMillis() - test4Start;
                testResults.add(new TestResult("test_04", "04-空向量异常", "PASS", duration));
                System.out.println("   结果: 通过");
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - test4Start;
                testResults.add(new TestResult("test_04", "04-空向量异常", "FAIL", duration));
                System.out.println("   结果: 失败 - " + e.getMessage());
                throw e;
            }
            
            // 5. 余弦相似度测试 - 相同向量
            long test5Start = System.currentTimeMillis();
            try {
                System.out.println("\n测试 5: 相同向量余弦相似度为1");
                
                float[] a = {1.0f, 0.0f};
                float[] b = {1.0f, 0.0f};
                
                double similarity = VectorDistanceUtils.cosineDistance(a, b);
                
                assertEquals(1.0, similarity, 1e-9, "相同向量余弦相似度应为1");
                
                long duration = System.currentTimeMillis() - test5Start;
                testResults.add(new TestResult("test_05", "05-相同向量余弦相似度", "PASS", duration));
                System.out.println("   结果: 通过");
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - test5Start;
                testResults.add(new TestResult("test_05", "05-相同向量余弦相似度", "FAIL", duration));
                System.out.println("   结果: 失败 - " + e.getMessage());
                throw e;
            }
            
            // 6. 余弦相似度测试 - 正交向量
            long test6Start = System.currentTimeMillis();
            try {
                System.out.println("\n测试 6: 正交向量余弦相似度为0");
                
                float[] a = {1.0f, 0.0f};
                float[] b = {0.0f, 1.0f};
                
                double similarity = VectorDistanceUtils.cosineDistance(a, b);
                
                assertEquals(0.0, similarity, 1e-9, "正交向量余弦相似度应为0");
                
                long duration = System.currentTimeMillis() - test6Start;
                testResults.add(new TestResult("test_06", "06-正交向量余弦相似度", "PASS", duration));
                System.out.println("   结果: 通过");
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - test6Start;
                testResults.add(new TestResult("test_06", "06-正交向量余弦相似度", "FAIL", duration));
                System.out.println("   结果: 失败 - " + e.getMessage());
                throw e;
            }
            
            // 7. 余弦相似度测试 - 零向量
            long test7Start = System.currentTimeMillis();
            try {
                System.out.println("\n测试 7: 零向量余弦相似度异常");
                
                float[] a = {0.0f, 0.0f};
                float[] b = {1.0f, 2.0f};
                
                Exception exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> VectorDistanceUtils.cosineDistance(a, b),
                    "零向量时应抛出异常"
                );
                
                long duration = System.currentTimeMillis() - test7Start;
                testResults.add(new TestResult("test_07", "07-零向量余弦相似度异常", "PASS", duration));
                System.out.println("   结果: 通过");
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - test7Start;
                testResults.add(new TestResult("test_07", "07-零向量余弦相似度异常", "FAIL", duration));
                System.out.println("   结果: 失败 - " + e.getMessage());
                throw e;
            }
            
            // 8. 计算复杂向量
            long test8Start = System.currentTimeMillis();
            try {
                System.out.println("\n测试 8: 复杂向量计算");
                
                float[] a = {1.0f, 2.0f, 3.0f};
                float[] b = {4.0f, 5.0f, 6.0f};
                
                double euclidean = VectorDistanceUtils.euclideanDistance(a, b);
                double cosine = VectorDistanceUtils.cosineDistance(a, b);
                
                // 验证欧氏距离
                double expectedEuclidean = Math.sqrt(9 + 9 + 9); // √(3²+3²+3²) = √27 ≈ 5.196
                assertEquals(expectedEuclidean, euclidean, 1e-9, "复杂向量欧氏距离计算错误");
                
                // 验证余弦相似度在合理范围内
                assertTrue(cosine >= -1.0 && cosine <= 1.0, "余弦相似度应在[-1,1]范围内");
                
                long duration = System.currentTimeMillis() - test8Start;
                testResults.add(new TestResult("test_08", "08-复杂向量计算", "PASS", duration));
                System.out.println("   结果: 通过");
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - test8Start;
                testResults.add(new TestResult("test_08", "08-复杂向量计算", "FAIL", duration));
                System.out.println("   结果: 失败 - " + e.getMessage());
                throw e;
            }
            
            System.out.println("\n向量计算测试完成");
            System.out.println("   通过: " + testResults.stream().filter(r -> "PASS".equals(r.status)).count() + 
                             "/" + testResults.size() + " (" + 
                             (testResults.stream().filter(r -> "PASS".equals(r.status)).count() * 100 / testResults.size()) + "%)");
            
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(testResults.stream().allMatch(r -> "PASS".equals(r.status)), duration);
            recordTestSuccess("向量计算测试完成: " + testResults.stream().filter(r -> "PASS".equals(r.status)).count() + 
                            "/" + testResults.size() + " 通过");
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            printTestEnd(false, duration);
            recordTestFailure("向量计算测试失败: " + e.getMessage(), e);
            throw e;
        }
    }
    
    @AfterAll
    static void printTestSummary() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("VectorDistanceUtilsTest 测试结果汇总");
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