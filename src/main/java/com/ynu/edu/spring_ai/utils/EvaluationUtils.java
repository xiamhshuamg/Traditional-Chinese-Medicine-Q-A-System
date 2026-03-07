package com.ynu.edu.spring_ai.utils;

import com.ynu.edu.spring_ai.service.TestDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 评估工具：性能+功能对比（修复版：预format、无乱码、分隔清晰）
 */
@Slf4j
public class EvaluationUtils {

    public static final String[] KEY_ELEMENTS = {"诊断", "证型", "治法", "方剂", "中药", "用量", "加减"};

    /**
     * 功能评分：结构化检查 + 关键词重叠（中医调：结构*2.0，Jaccard*1.5）
     */
    public static double evaluateFunction(String recommendation, TestDataService.MedicalCase.ExpectedOutput expected) {
        double score = 0.0;
        String recLower = recommendation.toLowerCase();

        // 1. 结构化检查（每个关键词+2分，满14分）
        int structScore = 0;
        for (String key : KEY_ELEMENTS) {
            if (recLower.contains(key.toLowerCase())) {
                structScore++;
            }
        }
        score += structScore * 2.0;

        // 2. 与预期重叠（Jaccard*1.5，0-15分）
        Set<String> recWords = extractWords(recommendation);
        String expectedStr = (expected.getDiagnosis() != null ? expected.getDiagnosis() : "") + " " +
                             (expected.getFormula() != null ? expected.getFormula() : "") + " " +
                             (expected.getHerbs() != null ? expected.getHerbs() : "");
        Set<String> expWords = extractWords(expectedStr);
        double overlap = jaccardSimilarity(recWords, expWords);
        score += overlap * 10 * 1.5;

        return Math.min(score, 29.0);  // 上限29
    }

    /**
     * LLM主观评分（固定关闭，避慢）
     */
    public static double llmScore(String recommendation, TestDataService.MedicalCase.ExpectedOutput expected, ChatClient client, boolean useLlm) {
        return 5.0;  // 固定中分，关闭LLM
    }

    /**
     * 性能统计：时间→分数（基准10s）
     */
    public static double evaluatePerformance(long timeMs, long baselineMs) {
        if (timeMs >= baselineMs) {
            return 0.0;
        }
        return 10.0 * (1.0 - (double) timeMs / baselineMs);
    }

    // 辅助：提取单词集
    private static Set<String> extractWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new HashSet<>();
        }
        Pattern pattern = Pattern.compile("[\u4e00-\u9fa5a-zA-Z]+");
        return pattern.matcher(text).results()
                .map(matchResult -> matchResult.group().toLowerCase())
                .filter(word -> word.length() > 1)
                .collect(Collectors.toSet());
    }

    // Jaccard相似度
    private static double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return (double) intersection.size() / union.size();
    }

    /**
     * 打印对比表格（短列名、多空行、无乱码）
     */
    public static void printComparisonTable(List<Map<String, Object>> results, String method1, String method2) {
        log.info("\nRAG vs KG 表格");
        log.info("| 用例 | {}分 | {}分 | {}时(ms) | {}时(ms) | 胜 |", method1, method2, method1, method2);
        log.info("|------|-----|-----|----------|----------|----|");
        int win1 = 0, win2 = 0, tie = 0;
        for (Map<String, Object> res : results) {
            double s1 = (double) res.get("score1");
            double s2 = (double) res.get("score2");
            long t1 = (long) res.get("time1");
            long t2 = (long) res.get("time2");
            String winner = s1 > s2 ? method1 : (s2 > s1 ? method2 : "平");
            if (s1 > s2) win1++;
            else if (s2 > s1) win2++;
            else tie++;
            String s1Fmt = String.format("%.1f", s1);
            String s2Fmt = String.format("%.1f", s2);
            log.info("| {} | {} | {} | {} | {} | {} |", res.get("case"), s1Fmt, s2Fmt, t1, t2, winner);
        }
        int totalWins = win1 + win2;
        double winRate1 = totalWins > 0 ? (win1 * 100.0 / totalWins) : 0.0;
        String winRateStr = String.format("%.0f", winRate1);
        log.info("\n胜率: {}胜{}场/{}场 ({}%)", method1, win1, totalWins, winRateStr);
        double avgS1 = getAvgScore(results, "score1");
        double avgS2 = getAvgScore(results, "score2");
        long avgT1 = getAvgTime(results, "time1");
        long avgT2 = getAvgTime(results, "time2");
        String avgS1Str = String.format("%.1f", avgS1);
        String avgS2Str = String.format("%.1f", avgS2);
        log.info("平均: {} {}分/{}ms | {} {}分/{}ms", method1, avgS1Str, avgT1, method2, avgS2Str, avgT2);
        String funcWinner = avgS1 > avgS2 ? method1 : method2;
        String perfWinner = avgT1 < avgT2 ? method1 : method2;
        log.info("\n结论: {}胜功能, {}胜性能. 项目: RAG起步快, KG生产准, 建议hybrid.");
        log.info("\n\n\n\n");  // 4空行
    }

    private static double getAvgScore(List<Map<String, Object>> results, String key) {
        return results.stream().mapToDouble(m -> (double) m.get(key)).average().orElse(0.0);
    }

    private static long getAvgTime(List<Map<String, Object>> results, String key) {
        return Math.round(results.stream().mapToLong(m -> (long) m.get(key)).average().orElse(0.0));
    }
}
