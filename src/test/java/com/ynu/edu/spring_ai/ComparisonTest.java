package com.ynu.edu.spring_ai;

import com.ynu.edu.spring_ai.service.KgComparisonService;
import com.ynu.edu.spring_ai.service.RagService;
import com.ynu.edu.spring_ai.service.TestDataService;
import com.ynu.edu.spring_ai.utils.EvaluationUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.text.DecimalFormat;
import java.util.Map;

/**
 * 详细对比测试：RAG vs 知识图谱
 * - 详细展示每个测试用例的结果
 * - 对比分析两种方法的效果
 * - 提供全面的综合结论
 */
@SpringBootTest
@Slf4j
public class ComparisonTest {

    @Autowired
    private RagService ragService;

    @Autowired
    private KgComparisonService kgComparisonService;

    @Autowired
    private TestDataService testDataService;

    private static final DecimalFormat DF = new DecimalFormat("#.##");

    /**
     * 详细对比测试：RAG vs 知识图谱
     */
    @Test
    void detailedRagVsKgComparison() {
        log.info("\n===========================================");
        log.info("开始 RAG vs 知识图谱 详细对比测试");
        log.info("===========================================\n");

        var testCases = testDataService.loadTestData();
        
        if (testCases == null || testCases.isEmpty()) {
            log.error(" 没有找到测试用例，无法执行对比测试");
            return;
        }

        log.info(" 加载到 {} 个测试用例\n", testCases.size());

        int totalCases = testCases.size();
        int ragWins = 0;
        int kgWins = 0;
        int ties = 0;

        double ragTotalScore = 0.0;
        double kgTotalScore = 0.0;

        long ragTotalTime = 0L;
        long kgTotalTime = 0L;

        // 1. 逐条测试用例对比
        log.info(" 详细测试结果：");
        log.info("------------------------------------------------------------");

        for (int i = 0; i < testCases.size(); i++) {
            TestDataService.MedicalCase testCase = testCases.get(i);
            String input = testCase.getInput();
            TestDataService.MedicalCase.ExpectedOutput expected = testCase.getOutput();

            log.info("\n 用例 {}: {}", i + 1, getShortenedInput(input));

            try {
                // 执行RAG推荐
                long ragStart = System.currentTimeMillis();
                Map<String, Object> ragResult = ragService.recommendWithRAG(input);
                long ragTime = System.currentTimeMillis() - ragStart;
                String ragRec = (String) ragResult.get("recommendation");
                double ragScore = EvaluationUtils.evaluateFunction(ragRec, expected);

                // 执行知识图谱推荐
                long kgStart = System.currentTimeMillis();
                Map<String, Object> kgResult = kgComparisonService.recommendWithKG(input);
                long kgTime = System.currentTimeMillis() - kgStart;
                String kgRec = (String) kgResult.get("recommendation");
                double kgScore = EvaluationUtils.evaluateFunction(kgRec, expected);

                // 统计累计值
                ragTotalScore += ragScore;
                kgTotalScore += kgScore;
                ragTotalTime += ragTime;
                kgTotalTime += kgTime;

                // 判断胜负
                String winner;
                if (ragScore > kgScore) {
                    ragWins++;
                    winner = " RAG 胜出";
                } else if (kgScore > ragScore) {
                    kgWins++;
                    winner = " 知识图谱胜出";
                } else {
                    ties++;
                    winner = " 平局";
                }

                // 输出详细对比
                log.info("    详细对比:");
                log.info("   │ RAG 推荐:");
                log.info("   │   得分: {}/29.0", DF.format(ragScore));
                log.info("   │   耗时: {}ms", ragTime);
                log.info("   │   是否命中相似病例: {}个", ragResult.get("similarCasesCount"));
                log.info("   │");
                log.info("   │ 知识图谱推荐:");
                log.info("   │   得分: {}/29.0", DF.format(kgScore));
                log.info("   │   耗时: {}ms", kgTime);
                log.info("   │   有效分析: {}", kgResult.get("hasValidAnalysis"));
                log.info("   │   匹配症状: {}", kgResult.get("matchedSymptoms"));
                log.info("   │   匹配证型: {}", kgResult.get("matchedSyndromes"));
                log.info("   │");
                log.info("    结果: {} (得分差: {})", winner, DF.format(Math.abs(ragScore - kgScore)));

            } catch (Exception e) {
                log.error("    用例执行失败: {}", e.getMessage());
                log.error("   异常详情:", e);
            }
        }

        log.info("\n===========================================");
        log.info("测试完成，生成综合报告");
        log.info("===========================================\n");

        // 2. 综合统计
        double avgRagScore = ragTotalScore / totalCases;
        double avgKgScore = kgTotalScore / totalCases;
        long avgRagTime = ragTotalTime / totalCases;
        long avgKgTime = kgTotalTime / totalCases;

        int compareCount = ragWins + kgWins;
        double ragWinRate = compareCount > 0 ? (ragWins * 100.0 / compareCount) : 0.0;
        double kgWinRate = compareCount > 0 ? (kgWins * 100.0 / compareCount) : 0.0;

        // 功能表现对比
        String funcWinner;
        String funcAnalysis;
        if (avgRagScore > avgKgScore) {
            funcWinner = "RAG";
            funcAnalysis = String.format("RAG 在功能得分上领先 %.1f 分", avgRagScore - avgKgScore);
        } else if (avgKgScore > avgRagScore) {
            funcWinner = "知识图谱";
            funcAnalysis = String.format("知识图谱在功能得分上领先 %.1f 分", avgKgScore - avgRagScore);
        } else {
            funcWinner = "两者相当";
            funcAnalysis = "两种方法在功能表现上基本相当";
        }

        // 性能表现对比
        String perfWinner;
        String perfAnalysis;
        if (avgRagTime < avgKgTime) {
            perfWinner = "RAG";
            perfAnalysis = String.format("RAG 在响应速度上快 %.1f 秒", (avgKgTime - avgRagTime) / 1000.0);
        } else if (avgKgTime < avgRagTime) {
            perfWinner = "知识图谱";
            perfAnalysis = String.format("知识图谱在响应速度上快 %.1f 秒", (avgRagTime - avgKgTime) / 1000.0);
        } else {
            perfWinner = "两者相当";
            perfAnalysis = "两种方法在性能表现上基本相当";
        }

        // 3. 输出详细报告
        log.info(" 综合统计:");
        log.info("------------------------------------------------------------");
        log.info("测试用例总数: {}", totalCases);
        log.info("有效对比场次: {} (平局 {} 场)", compareCount, ties);
        log.info("");
        log.info(" 胜负统计:");
        log.info("  RAG 胜出: {} 场 (胜率: {}%)", ragWins, DF.format(ragWinRate));
        log.info("  知识图谱胜出: {} 场 (胜率: {}%)", kgWins, DF.format(kgWinRate));
        log.info("");
        log.info(" 平均功能得分 (满分29分):");
        log.info("  RAG: {}/29.0", DF.format(avgRagScore));
        log.info("  知识图谱: {}/29.0", DF.format(avgKgScore));
        log.info("");
        log.info(" 平均处理时间:");
        log.info("  RAG: {}ms (约 {} 秒)", avgRagTime, DF.format(avgRagTime / 1000.0));
        log.info("  知识图谱: {}ms (约 {} 秒)", avgKgTime, DF.format(avgKgTime / 1000.0));
        log.info("");


        // 5. 综合结论与建议
        log.info("💡 综合结论与建议:");
        log.info("------------------------------------------------------------");
        log.info("1. 功能表现: {}", funcAnalysis);
        log.info("   → 功能胜出方: {}", funcWinner);
        log.info("");
        log.info("2. 性能表现: {}", perfAnalysis);
        log.info("   → 性能胜出方: {}", perfWinner);
        log.info("");
        
        if (ragWinRate > 70) {
            log.info(" 核心结论: 在当前测试数据和实现下，RAG 方法在多数情况下表现更优");
//            log.info("   建议: 对于需要快速响应的临床辅助场景，优先考虑 RAG 方法");
        } else if (kgWinRate > 70) {
            log.info(" 核心结论: 在当前测试数据和实现下，知识图谱方法在多数情况下表现更优");
//            log.info("   建议: 对于需要严谨理论依据的教学或研究场景，优先考虑知识图谱方法");
        } else {
            log.info(" 核心结论: 两种方法各有优势，适合不同场景");
//            log.info("   建议: 考虑采用混合方法，结合两者的优势");
        }

        // 6. 测试总结
        log.info("===========================================");
        log.info("测试总结");
        log.info("===========================================");
        log.info(" 测试已完成");
        log.info(" 测试时间: {}", new java.util.Date());
        log.info(" 总体评价: {}", getOverallRating(ragWinRate, avgRagScore, avgRagTime));
        log.info("");
    }

    /**
     * 获取输入内容的简短版本
     */
    private String getShortenedInput(String input) {
        if (input == null) return "无输入";
        if (input.length() <= 50) return input;
        return input.substring(0, 47) + "...";
    }

    /**
     * 获取总体评级
     */
    private String getOverallRating(double ragWinRate, double avgRagScore, long avgRagTime) {
        if (ragWinRate >= 80 && avgRagScore >= 20 && avgRagTime <= 30000) {
            return "优秀 - RAG 方法表现突出";
        } else if (ragWinRate >= 60) {
            return "良好 - RAG 方法具有优势";
        } else if (ragWinRate >= 40) {
            return "中等 - 两种方法各有千秋";
        } else {
            return "待优化 - 需要进一步改进方法";
        }
    }

    /**
     * 快速测试 - 仅输出关键结果
     */
    @Test
    void quickComparison() {
        log.info("\n⚡ 快速对比测试\n");
        
        var testCases = testDataService.loadTestData();
        if (testCases == null || testCases.isEmpty()) {
            log.error(" 没有测试用例");
            return;
        }

        // 只测试前3个用例
        int limit = Math.min(3, testCases.size());
        log.info("测试前 {} 个用例\n", limit);

        int ragWins = 0;
        int kgWins = 0;
        
        for (int i = 0; i < limit; i++) {
            var testCase = testCases.get(i);
            
            try {
                // RAG
                long ragStart = System.currentTimeMillis();
                var ragResult = ragService.recommendWithRAG(testCase.getInput());
                long ragTime = System.currentTimeMillis() - ragStart;
                double ragScore = EvaluationUtils.evaluateFunction(
                    (String) ragResult.get("recommendation"), testCase.getOutput());

                // KG
                long kgStart = System.currentTimeMillis();
                var kgResult = kgComparisonService.recommendWithKG(testCase.getInput());
                long kgTime = System.currentTimeMillis() - kgStart;
                double kgScore = EvaluationUtils.evaluateFunction(
                    (String) kgResult.get("recommendation"), testCase.getOutput());

                // 对比
                String winner;
                if (ragScore > kgScore) {
                    ragWins++;
                    winner = "RAG ✓";
                } else if (kgScore > ragScore) {
                    kgWins++;
                    winner = "KG ✓";
                } else {
                    winner = "平局";
                }

                log.info("用例 {}: {}", i + 1, winner);
                log.info("  RAG: {}/29.0 ({}ms)", DF.format(ragScore), ragTime);
                log.info("  KG:  {}/29.0 ({}ms)", DF.format(kgScore), kgTime);
                log.info("");

            } catch (Exception e) {
                log.error("用例 {} 失败: {}", i + 1, e.getMessage());
            }
        }

        log.info(" 快速测试结果:");
        log.info("RAG 胜出: {} 场", ragWins);
        log.info("知识图谱胜出: {} 场", kgWins);
        
        if (ragWins > kgWins) {
            log.info(" RAG 表现更好");
        } else if (kgWins > ragWins) {
            log.info("知识图谱表现更好");
        } else {
            log.info(" 两种方法表现相当");
        }
    }
}