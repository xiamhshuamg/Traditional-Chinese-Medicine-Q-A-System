package com.ynu.edu.spring_ai.repository.graph;

import com.ynu.edu.spring_ai.service.FileService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@Slf4j
public class KnowledgeGraphService {

    @Data
    public static class KnowledgeGraphResult {
        private boolean hasValidAnalysis;  // 是否有有效的分析结果
        private String analysisText;       // 分析文本
        private List<String> matchedSymptoms; // 匹配到的症状
        private List<String> matchedSyndromes; // 匹配到的证型
    }

    private final Neo4jClient neo4jClient;
    private final FileService fileService;

    /** ① 原文里揪出图谱症状名 */
    public List<String> findSymptomNamesInRawText(String rawText) {
        if (rawText == null || rawText.isBlank()) return List.of();
        String cypher =
                "MATCH (sym:症状) " +
                        "WHERE $t CONTAINS sym.名称 " +
                        "RETURN sym.名称 AS name " +
                        "ORDER BY size(sym.名称) DESC, name ASC";
        return neo4jClient.query(cypher)
                .bind(rawText).to("t")
                .fetch().all().stream()
                .map(r -> (String) r.getOrDefault("name", ""))
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
    }

    /** ② 症状 → 候选证型（聚合打分） */
    public List<Map<String, Object>> findSyndromesBySymptomNames(List<String> symptomNames, int limit) {
        if (symptomNames == null || symptomNames.isEmpty()) return new ArrayList<>();
        String cypher =
                "UNWIND $names AS sname " +
                        "MATCH (sym:症状 {名称: sname})-[r:关联]->(syn:证型) " +
                        "WITH syn, sum(coalesce(r.weight, 1)) AS score " +
                        "RETURN syn.id AS sid, syn.名称 AS sname, score " +
                        "ORDER BY score DESC, sname ASC " +
                        "LIMIT $lim";
        return new ArrayList<>(
                neo4jClient.query(cypher)
                        .bindAll(Map.of("names", symptomNames, "lim", limit))
                        .fetch().all()
        );
    }

    /** ③ 证型 → 典型症状 */
    public List<String> findTypicalSymptomsBySyndromeId(String syndromeId) {
        String cypher =
                "MATCH (sym:症状)-[r:关联]->(syn:证型 {id: $sid}) " +
                        "WITH sym, coalesce(r.weight, 0) AS w " +
                        "RETURN sym.名称 AS name " +
                        "ORDER BY w DESC, name ASC";
        return neo4jClient.query(cypher)
                .bind(syndromeId).to("sid")
                .fetch().all().stream()
                .map(r -> String.valueOf(r.getOrDefault("name", "")))
                .toList();
    }

    /** ④ 证型 → 方剂（含中药与剂量） */
    public List<Map<String, Object>> findFormulasBySyndromeId(String syndromeId) {
        String cypher = ""
                + "MATCH (syn:证型 {id: $sid})-[:治疗]->(f:方剂)\n"
                + "OPTIONAL MATCH (f)-[u:包含]->(h:中药)\n"
                + "WITH f, collect({"
                + "  hid: h.id,"
                + "  hname: h.名称,"
                + "  grams: coalesce(u.剂量, u.weight, h.剂量, h.剂量文本)"
                + "}) AS herbs\n"
                + "RETURN f.id AS fid, f.名称 AS fname, herbs\n"
                + "ORDER BY fname ASC";

        return new ArrayList<>(
                neo4jClient.query(cypher)
                        .bind(syndromeId).to("sid")
                        .fetch()
                        .all()
        );
    }

    /* 知识图谱分析主方法 - 返回结构化结果 */
    public KnowledgeGraphResult analyzeWithKnowledgeGraph(String text) {
        KnowledgeGraphResult result = new KnowledgeGraphResult();
        result.setHasValidAnalysis(false);
        result.setMatchedSymptoms(new ArrayList<>());
        result.setMatchedSyndromes(new ArrayList<>());

        if (text == null || text.isBlank()) {
            result.setAnalysisText("");
            return result;
        }

        try {
            // 1. 提取症状
            List<String> symptomNames = findSymptomNamesInRawText(text);
            if (symptomNames.isEmpty()) {
                result.setAnalysisText("");
                return result;
            }
            // 2. 查询相关证型
            List<Map<String, Object>> syndromes = findSyndromesBySymptomNames(symptomNames, 3);
            if (syndromes.isEmpty()) {
                result.setAnalysisText("");
                return result;
            }
            // 3. 构建详细分析
            StringBuilder sb = new StringBuilder();
            sb.append("知识图谱分析：\n");
            sb.append("识别到的症状：").append(String.join("、", symptomNames)).append("\n");

            List<String> syndromeNames = new ArrayList<>();
            for (Map<String, Object> syndrome : syndromes) {
                String sname = String.valueOf(syndrome.get("sname"));
                syndromeNames.add(sname);
                Double score = (Double) syndrome.get("score");
                sb.append("可能证型：").append(sname).append(" (相关度：").append(score).append(")\n");

                // 查询典型症状
                List<String> typicalSymptoms = findTypicalSymptomsBySyndromeId(
                        String.valueOf(syndrome.get("sid"))
                );
                if (!typicalSymptoms.isEmpty()) {
                    sb.append("典型症状：").append(String.join("、", typicalSymptoms.subList(0, Math.min(3, typicalSymptoms.size())))).append("\n");
                }

                // 查询相关方剂
                List<Map<String, Object>> formulas = findFormulasBySyndromeId(
                        String.valueOf(syndrome.get("sid"))
                );
                if (!formulas.isEmpty()) {
                    sb.append("参考方剂：");
                    for (int i = 0; i < Math.min(2, formulas.size()); i++) {
                        sb.append(formulas.get(i).get("fname"));
                        if (i < Math.min(2, formulas.size()) - 1) sb.append("、");
                    }
                    sb.append("\n");
                }
            }
            result.setMatchedSymptoms(symptomNames);
            result.setMatchedSyndromes(syndromeNames);
            result.setAnalysisText(sb.toString());
            result.setHasValidAnalysis(true);  // 只有匹配到症状和证型才算有效分析

            log.info("知识图谱分析结果 - 有效分析: {}, 症状: {}, 证型: {}", true, symptomNames, syndromeNames);
            return result;

        } catch (Exception e) {
            log.error("知识图谱查询失败", e);
            result.setAnalysisText("");
            return result;
        }
    }

    /** 保持原有的简单方法用于兼容 */
    public String buildHintFromText(String text) {
        KnowledgeGraphResult result = analyzeWithKnowledgeGraph(text);
        return result.getAnalysisText();
    }

    // 修改构造函数，注入FileService
    public KnowledgeGraphService(Neo4jClient neo4jClient, FileService fileService) {
        this.neo4jClient = neo4jClient;
        this.fileService = fileService; // 初始化fileService
    }

    // 修改analyzeFileContent方法，移除static
    public String analyzeFileContent(MultipartFile file) {
        try {
            String content = fileService.extractTextContent(file);
            KnowledgeGraphResult result = analyzeWithKnowledgeGraph(content);
            return result.getAnalysisText();
        } catch (Exception e) {
            log.error("分析文件内容失败", e);
            return "文件内容分析失败: " + e.getMessage();
        }
    }
}