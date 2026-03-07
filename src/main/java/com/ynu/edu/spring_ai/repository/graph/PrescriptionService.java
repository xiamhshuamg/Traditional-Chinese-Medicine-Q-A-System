package com.ynu.edu.spring_ai.repository.graph;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PrescriptionService {

    private final KnowledgeGraphService knowledgeGraphService;
    private final LlmService llmService;

    public PrescriptionService(KnowledgeGraphService knowledgeGraphService, LlmService llmService) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.llmService = llmService;
    }

    //主入口：返回包含 usedGraph / syndromes / stats / explanation 的结果
    public Map<String, Object> recommend(String input) {
        //从原文抓“症状”名称
        List<String> symptomNames = knowledgeGraphService.findSymptomNamesInRawText(input);
        // 兜底：一句症状也抓不到
        if (symptomNames.isEmpty()) {
            Map<String, Object> empty = baseResult(false, List.of());
            String explanation = llmService.generate(buildFallbackPrompt(input, List.of(), List.of()));
            empty.put("explanation", toParagraph(explanation));
            empty.put("stats", countStats(castSyndromes(empty.get("syndromes"))));
            return empty;
        }
        //症状 → 候选证型
        List<Map<String, Object>> candidates = knowledgeGraphService.findSyndromesBySymptomNames(symptomNames, 5);
        if (candidates.isEmpty()) {
            Map<String, Object> empty = baseResult(false, List.of());
            String explanation = llmService.generate(buildFallbackPrompt(input, symptomNames, List.of()));
            empty.put("explanation", toParagraph(explanation));
            empty.put("stats", countStats(castSyndromes(empty.get("syndromes"))));
            return empty;
        }
        // (3) 展开证型详情：典型症状、方剂与中药剂量
        List<Map<String, Object>> syndromesList = new ArrayList<>();
        for (Map<String, Object> row : candidates) {
            String sid = String.valueOf(row.getOrDefault("sid", row.getOrDefault("id","")));
            String sname = String.valueOf(row.getOrDefault("sname", row.getOrDefault("name","")));

            List<String> typical = knowledgeGraphService.findTypicalSymptomsBySyndromeId(sid);
            List<Map<String, Object>> formulasRaw = knowledgeGraphService.findFormulasBySyndromeId(sid);

            List<Map<String, Object>> enrichedFormulas = new ArrayList<>();
            for (Map<String, Object> fm : formulasRaw) {
                String fid = String.valueOf(fm.getOrDefault("fid",""));
                String fname = String.valueOf(fm.getOrDefault("fname",""));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> herbs = (List<Map<String, Object>>) fm.getOrDefault("herbs", List.of());
                Map<String, Object> fobj = new LinkedHashMap<>();
                fobj.put("fid", fid);
                fobj.put("fname", fname);
                fobj.put("herbs", herbs);
                enrichedFormulas.add(fobj);
            }

            Map<String, Object> sObj = new LinkedHashMap<>();
            sObj.put("sid", sid);
            sObj.put("sname", sname);
            sObj.put("typicalSymptoms", typical);
            sObj.put("formulas", enrichedFormulas);
            syndromesList.add(sObj);
        }

        // (4) 严格基于图谱的提示词 → LLM
        Map<String, Object> result = baseResult(true, syndromesList);
        String explanation = llmService.generate(buildGraphStrictPrompt(input, result));
        result.put("explanation", toParagraph(explanation));
        result.put("stats", countStats(syndromesList));
        return result;
    }

    // ======= 提示词（严格依图 / 兜底） =======

    public String buildGraphStrictPrompt(String userInput, Map<String, Object> kgPayload) {
        // 关键：强制“只许使用我给的 KG JSON”
        return ""
                + "你是中医处方助手。必须严格基于下方提供的【知识图谱数据】回答：\n"
                + "【要求】\n"
                + "1) 只允许使用我给的知识图谱 JSON，不得引用或想象任何外部知识；\n"
                + "2) 输出包含：候选证型的思路、对应方剂与中药（含剂量，如有）、配伍要点、注意/禁忌；\n"
                + "3) 若 JSON 中无某信息，明确标注“图谱未提供”，不得胡编；\n"
                + "4) 结尾提醒：一切以临床医师判断为准。\n\n"
                + "【用户原文】\n" + userInput + "\n\n"
                + "【知识图谱数据(JSON)】\n" + toCompactJson(kgPayload) + "\n\n"
                + "请给出结构化分段说明。";
    }

    public String buildFallbackPrompt(String userInput, List<String> matchedSymptoms, List<String> typicalSymptoms) {
        return ""
                + "你是中医处方助手。当前未能从知识图谱检索到可用证型/方剂数据。\n"
                + "【要求】\n"
                + "1) 不得提供任何具体药物名称/剂量；\n"
                + "2) 可解释症状理解思路、建议就医科室、可进行的非处方注意事项（如休息、饮水等）；\n"
                + "3) 语气克制，避免绝对化结论。\n\n"
                + "【用户原文】" + userInput + "\n"
                + "【匹配到的症状】" + matchedSymptoms + "\n"
                + "【常见典型症状参考】" + typicalSymptoms + "\n";
    }

    // ======= 工具方法（与 tcm-m3 思路一致） =======

    private Map<String, Object> baseResult(boolean usedGraph, List<Map<String, Object>> syndromes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usedGraph", usedGraph);
        m.put("syndromes", syndromes);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castSyndromes(Object o) {
        return o == null ? List.of() : (List<Map<String, Object>>) o;
    }

    private Map<String, Object> countStats(List<Map<String, Object>> syndromes) {
        int s = syndromes.size(), f = 0, h = 0;
        for (Map<String, Object> syn : syndromes) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fs = (List<Map<String, Object>>) syn.getOrDefault("formulas", List.of());
            f += fs.size();
            for (Map<String, Object> fm : fs) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> herbs = (List<Map<String, Object>>) fm.getOrDefault("herbs", List.of());
                h += herbs.size();
            }
        }
        return Map.of("syndromeCount", s, "formulaCount", f, "herbCount", h);
    }

    private static String toParagraph(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\r\\n?", "\n").trim();
        while (s.contains("\n\n\n")) s = s.replace("\n\n\n", "\n\n");
        return s;
    }

    private static String toCompactJson(Object o) {
        // 简易 JSON：为了避免依赖 ObjectMapper；生产可换成 Jackson
        if (o == null) return "null";
        if (o instanceof String) return "\"" + ((String) o).replace("\"","\\\"") + "\"";
        if (o instanceof Number || o instanceof Boolean) return String.valueOf(o);
        if (o instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toCompactJson(String.valueOf(e.getKey()))).append(":").append(toCompactJson(e.getValue()));
            }
            return sb.append("}").toString();
        }
        if (o instanceof Iterable) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object it : (Iterable<?>) o) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toCompactJson(it));
            }
            return sb.append("]").toString();
        }
        return toCompactJson(String.valueOf(o));
    }
}