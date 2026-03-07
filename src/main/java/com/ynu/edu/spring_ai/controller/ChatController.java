package com.ynu.edu.spring_ai.controller;

import com.ynu.edu.spring_ai.domain.entity.ConsultMessage;
import com.ynu.edu.spring_ai.repository.graph.KnowledgeGraphService;
import com.ynu.edu.spring_ai.service.ConsultationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatClient chatClient;
    private final ConsultationService consultationService;
    private final KnowledgeGraphService knowledgeGraphService;

    @GetMapping(value = "/chat/plain", produces = MediaType.TEXT_PLAIN_VALUE)
    public String chatPlain(@RequestParam("consultationId") Long consultationId,
                            @RequestParam(value = "prompt", required = false, defaultValue = "") String prompt,
                            @RequestParam(value = "silent", required = false, defaultValue = "false") boolean silent) {

        String trimmed = prompt == null ? "" : prompt.trim();

        // silent 且没补充：给模型一个“内部触发问题”，但不入库、不显示
        String effectivePrompt = trimmed;
        if (silent && effectivePrompt.isBlank()) {
            effectivePrompt = "请基于刚上传的病历（系统消息里的【病历原文】与【病历解析结果】）进行辨证分析，并给出方药/加减建议；如信息不足，请列出需要补充的四诊要点。";
        }

        // ✅ 只有非 silent 且确实有输入时，才记录医生输入
        if (!silent && !trimmed.isBlank()) {
            consultationService.appendMessage(consultationId, "DOCTOR", null, "user", trimmed);
        }

        List<ConsultMessage> history = consultationService.getMessages(consultationId);

        StringBuilder sb = new StringBuilder();
        sb.append("你是一名资深中医，请用简洁清晰的语言回答，避免复杂markdown。\n")
                .append("以下是同一问诊的历史对话（包含系统资料）：\n");

        for (ConsultMessage m : history) {
            String role = (m.getRole() == null) ? "user" : m.getRole();
            if ("system".equals(role) || "SYSTEM".equalsIgnoreCase(m.getSenderType())) sb.append("系统：");
            else if ("assistant".equals(role)) sb.append("AI：");
            else sb.append("医生：");
            sb.append(m.getContent()).append("\n");
        }

        // ✅ silent 时把“内部问题”补到 prompt 末尾，让“回答医生最后一次输入”有意义
        if (silent) {
            sb.append("医生：").append(effectivePrompt).append("\n");
        }
        sb.append("\n请基于以上全部信息，回答医生最后一次输入。\n");

        // 知识图谱：只在真的有医生输入时跑
        if (!silent && !trimmed.isBlank()) {
            KnowledgeGraphService.KnowledgeGraphResult kgResult = knowledgeGraphService.analyzeWithKnowledgeGraph(trimmed);
            if (kgResult.isHasValidAnalysis()) {
                sb.append("\n").append(kgResult.getAnalysisText()).append("\n");
                consultationService.appendMessage(consultationId,
                        "SYSTEM", null, "system", "知识图谱分析：" + kgResult.getAnalysisText());
            }
        }

        String answer = chatClient.prompt(sb.toString()).call().content();
        String cleanAnswer = cleanResponse(answer);

        consultationService.appendMessage(consultationId, "AI", null, "assistant", cleanAnswer);
        return cleanAnswer;
    }

    /** 清理回复中的多余符号 */
    private String cleanResponse(String answer) {
        if (answer == null) return "";

        // 移除过多的 ---、***、### 等符号
        String cleaned = answer
                .replaceAll("-{3,}", "")  // 移除连续的 ---
                .replaceAll("\\*{3,}", "") // 移除连续的 ***
                .replaceAll("#{3,}", "")  // 移除连续的 ###
                .replaceAll("_{3,}", "")  // 移除连续的 ___
                .trim();

        // 如果清理后为空，返回原答案
        return cleaned.isEmpty() ? answer : cleaned;
    }
    // ChatController.java 中

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam("consultationId") Long consultationId,
                                 @RequestParam(value = "prompt", required = false, defaultValue = "") String prompt,
                                 jakarta.servlet.http.HttpServletResponse resp) {

        // 1. 基本 SSE 头设置：关闭缓存 / 代理缓冲，让浏览器能"边下边渲染"
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("X-Accel-Buffering", "no");
        resp.setHeader("Connection", "keep-alive");

        // 本次医生实际输入内容（可能为空）
        String typed = (prompt == null) ? "" : prompt.trim();

        // 2. 只有医生真的有输入时才写一条 user 消息到数据库
        //    （如果是上传病历后自动 silent 触发，就不会重复写一条空消息）
        if (!typed.isBlank()) {
            consultationService.appendMessage(consultationId, "DOCTOR", null, "user", typed);
        }

        // 3. 取出当前问诊的历史消息（包括之前的 system / AI / DOCTOR）
        List<ConsultMessage> history = consultationService.getMessages(consultationId);

        // 4. 如果本次医生没输入（typed 为空），就用一条"内部问题"触发模型
        //    这样 silent 场景下模型也知道自己要做的是"基于病历的综合分析"
        String effectiveQuestion = typed.isBlank()
                ? "请基于系统资料中的病历解析/知识图谱匹配进行辨证分析并给出方药加减建议；如信息不足，列出需要补充的四诊要点。"
                : typed;

        // 5. 创建 SseEmitter - 确保在知识图谱分析前已声明
        SseEmitter emitter = new SseEmitter(0L);

        // 6. 知识图谱分析：只在医生真的有输入时跑一次
        KnowledgeGraphService.KnowledgeGraphResult kgResult = null;
        if (!typed.isBlank()) {
            try {
                kgResult = knowledgeGraphService.analyzeWithKnowledgeGraph(typed);
                // 知识图谱分析立即发送结果，更新前端显示
                if (kgResult != null && kgResult.isHasValidAnalysis()) {
                    // 立即把知识图谱分析结果发给前端
                    String analysisText = kgResult.getAnalysisText();
                    // 立即推送分析结果给前端
                    send(emitter, "system", "知识图谱分析：" + analysisText); // 立即发送
                    // 同时写入一条 system 消息：MessageList.vue 会用它来显示"知识图谱：已命中"
                    consultationService.appendMessage(
                            consultationId,
                            "SYSTEM", null, "system",
                            "知识图谱分析：" + analysisText // 立即添加此分析到聊天记录中
                    );
                }
            } catch (Exception e) {
                log.warn("知识图谱分析失败：{}", e.getMessage());
            }
        }

        // 7. 拼接给大模型的完整 prompt：
        StringBuilder fullPrompt = new StringBuilder();
        fullPrompt.append("你是一名资深中医。请用中文输出。\n")
                .append("以下是完整的问诊对话历史，请基于全部历史信息综合分析：\n\n");

        // 添加历史消息（包括之前的问诊记录）
        for (ConsultMessage m : history) {
            String role = (m.getRole() == null) ? "user" : m.getRole();

            if ("system".equals(role) || "SYSTEM".equalsIgnoreCase(m.getSenderType())) {
                fullPrompt.append("系统：");
            } else if ("assistant".equals(role)) {
                fullPrompt.append("AI：");
            } else {
                fullPrompt.append("医生：");
            }
            fullPrompt.append(m.getContent()).append("\n");
        }

        // 7.2 如果这次知识图谱命中了，把分析结果也当作一条"系统提示"喂给模型
        //     注意：这里只需要添加到提示中，不需要重复发送或入库（因为前面已经做了）
        if (kgResult != null && kgResult.isHasValidAnalysis()) {
            String kgText = kgResult.getAnalysisText();
            fullPrompt.append("系统：知识图谱分析：").append(kgText).append("\n");
        }

        // 6.3 把本轮医生问题补在最后，并约定好流式输出格式
        fullPrompt.append("\n医生：").append(effectiveQuestion).append("\n\n")
                .append("""
                    【重要指令】
                    1. 请基于以上所有对话历史进行综合分析
                    2. 不要只看最后一条，要结合所有历史症状和诊断
                    3. 如果患者有多个问题，请分别分析并给出综合建议

                    请严格按以下格式输出（用于前端流式展示）：

                    <<<THOUGHT>>>  
                    【辨证思路】
                    患者以[主诉]为主诉，伴有[伴随症状]。四诊合参：患者[症状特点]，[舌象描述]，[脉象描述]。[病机分析]，[病理演变]。参考知识图谱，[相关中医理论支持]。综合分析，证属[证型]，治以[治法]，方选[基础方]加减。

                    <<<END_THOUGHT>>>
                    <<<ANSWER>>>
                    【证型分析】
                    • 主要证型：[证型1] - [证型1特点]
                    • 次要证型：[证型2] - [证型2特点]
                    • 可能兼夹：[兼夹证型] - [兼夹证型说明]

                    【治法原则】
                    • 核心治法：[核心治法] - [核心治法说明]
                    • 辅助治法：[辅助治法] - [辅助治法说明]
                    • 注意事项：[治法注意事项]

                    【方药方案】
                    根据辨证结果，提供以下方药方案：
                    方案一：[基础方1]加减 - 适用于偏[证型特点1]的情况
                    组成：
                      [药物1] [用量]g - [功效1]
                      [药物2] [用量]g - [功效2]
                      [药物3] [用量]g - [功效3]
                      [药物4] [用量]g - [功效4]
                      [药物5] [用量]g - [功效5]
                    方案二：[基础方2]加减 - 适用于偏[证型特点2]的情况
                    组成：
                      [药物1] [用量]g - [功效1]
                      [药物2] [用量]g - [功效2]
                      [药物3] [用量]g - [功效3]
                      [药物4] [用量]g - [功效4]
                      [药物5] [用量]g - [功效5]
                    【随症加减】
                    根据患者具体症状表现，可作如下化裁：
                    1. 若[症状A]明显，加[药物A] [用量]g，以[功效A]。
                    2. 若[症状B]明显，加[药物B] [用量]g，以[功效B]。
                    3. 若[症状C]明显，加[药物C] [用量]g，以[功效C]。
                    4. 若[症状D]明显，加[药物D] [用量]g，以[功效D]。
                    【用药注意】
                    1. [注意事项1]
                    2. [注意事项2]
                    3. [注意事项3]
                    4. [注意事项4]
                    【饮食调护】
                      宜食：[适宜食物1]、[适宜食物2]、[适宜食物3]
                      忌食：[禁忌食物1]、[禁忌食物2]、[禁忌食物3]
                      食疗方：[食疗方建议]
                    【预后与调摄】
                      预后判断：[预后描述]
                      生活调摄：[生活建议]
                      复诊建议：[复诊时间]，观察[观察指标]
                    【需补充四诊信息】
                    为进一步完善辨证，建议明确以下问题：
                    1. [问题1]？（了解[目的1]）
                    2. [问题2]？（明确[目的2]）
                    3. [问题3]？（判断[目的3]）
                    4. [问题4]？（鉴别[目的4]）

                    <<<END_ANSWER>>>
            """);

        // ====================== 下面是你原有的流式解析逻辑 ======================

        final String START_T = "<<<THOUGHT>>>";
        final String END_T = "<<<END_THOUGHT>>>";
        final String START_A = "<<<ANSWER>>>";
        final String END_A = "<<<END_ANSWER>>>";

        // 用状态机解析模型返回的流：先找到 THOUGHT，再找到 ANSWER
        enum Mode { SEEK_T, THINK, SEEK_A, ANSWER }

        java.util.concurrent.atomic.AtomicReference<Mode> mode =
                new java.util.concurrent.atomic.AtomicReference<>(Mode.SEEK_T);

        StringBuilder pending   = new StringBuilder(); // 缓冲区：临时堆积还没解析完的字符
        StringBuilder thoughtAll = new StringBuilder(); // 最终思路文本
        StringBuilder answerAll  = new StringBuilder(); // 最终建议文本
        java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean(false);
// 新增：标记“这一轮 AI 消息是否已经写入数据库（完整 / 兜底都算）”
        java.util.concurrent.atomic.AtomicBoolean saved = new java.util.concurrent.atomic.AtomicBoolean(false);
        // 真正去调用 Spring AI 的流式接口，并把大块内容再切成 60 字的小块，让前端更像“逐字打出来”
        reactor.core.publisher.Flux<String> stream = chatClient
                .prompt(fullPrompt.toString())
                .stream()
                .content()
                .flatMapIterable(s -> splitSmall(s, 60)); // splitSmall 是你底下那个工具方法

        reactor.core.Disposable sub = stream.subscribe(chunk -> {
            try {
                // 每收到一块内容先放到 pending 里，统一按标记解析
                pending.append(chunk);

                while (true) {
                    Mode m = mode.get();

                    // 1) 先找 <<<THOUGHT>>>
                    if (m == Mode.SEEK_T) {
                        int i = pending.indexOf(START_T);
                        if (i < 0) {
                            // 防止极端情况 pending 无限长
                            if (pending.length() > 4000) pending.setLength(0);
                            break;
                        }
                        pending.delete(0, i + START_T.length());
                        mode.set(Mode.THINK);
                        continue;
                    }

                    // 2) 处于 “思路” 阶段：找 <<<END_THOUGHT>>>
                    if (m == Mode.THINK) {
                        int e = pending.indexOf(END_T);
                        if (e < 0) {
                            // 还没结束，就把当前内容作为 think 事件推给前端
                            flushEmitter(emitter, "think", pending, thoughtAll);
                            break;
                        }
                        String part = pending.substring(0, e);
                        send(emitter, "think", part);
                        thoughtAll.append(part);
                        pending.delete(0, e + END_T.length());
                        mode.set(Mode.SEEK_A);
                        continue;
                    }

                    // 3) 找 <<<ANSWER>>>
                    if (m == Mode.SEEK_A) {
                        int i = pending.indexOf(START_A);
                        if (i < 0) break;
                        pending.delete(0, i + START_A.length());
                        mode.set(Mode.ANSWER);
                        continue;
                    }

                    // 4) 处于 “答案” 阶段：找 <<<END_ANSWER>>>
                    if (m == Mode.ANSWER) {
                        int e = pending.indexOf(END_A);
                        if (e < 0) {
                            flushEmitter(emitter, "answer", pending, answerAll);
                            break;
                        }

                        String part = pending.substring(0, e);
                        send(emitter, "answer", part);
                        answerAll.append(part);
                        pending.delete(0, e + END_A.length());

                        // 把完整“思路 + 建议”保存成一条 assistant 消息（供刷新/回放使用）
                        String savedText = "【思路概述】\n" + thoughtAll + "\n\n【建议】\n" + answerAll;
                        consultationService.appendMessage(consultationId, "AI", null, "assistant", savedText);
                        saved.set(true); // 标记本轮 AI 消息已落库（完整）

                        finished.set(true);
                        send(emitter, "done", "[DONE]");
                        emitter.complete();
                        break;
                    }
                }
            } catch (Exception ex) {
                finished.set(true);
                safeErrorEnd(emitter, ex);
            }
        }, err -> {
            finished.set(true);
            safeErrorEnd(emitter, err);
        }, () -> {
            // 流自然结束时的兜底：保证前端一定能收到 done
            if (finished.get()) return;

            try {
                // 把缓冲里剩余内容（去掉标记）当作 answer 推出去，避免“刷新就没了”
                String rest = pending.toString()
                        .replace(START_T, "").replace(END_T, "")
                        .replace(START_A, "").replace(END_A, "");

                if (!rest.isBlank()) {
                    send(emitter, "answer", rest);
                    answerAll.append(rest);
                }

                String savedText = "【思路概述】\n" + thoughtAll + "\n\n【建议】\n" + answerAll;
                consultationService.appendMessage(consultationId, "AI", null, "assistant", savedText);
                saved.set(true);

                send(emitter, "done", "[DONE]");
                emitter.complete();
            } catch (Exception ex) {
                safeErrorEnd(emitter, ex);
            }
        });

// 连接被前端关闭 / 超时等情况时，记得注销订阅 + 兜底落库
        Runnable savePartialIfNeeded = () -> {
            // 如果这一轮还没写过 AI 消息，并且已经有部分内容，就把“已生成部分”落库
            if (!saved.get() && (thoughtAll.length() > 0 || answerAll.length() > 0)) {
                String savedText = "【思路概述】\n" + thoughtAll + "\n\n【建议】\n" + answerAll;
                consultationService.appendMessage(consultationId, "AI", null, "assistant", savedText);
                saved.set(true);
            }
        };

        emitter.onCompletion(() -> {
            sub.dispose();
            savePartialIfNeeded.run();
        });

        emitter.onTimeout(() -> {
            sub.dispose();
            savePartialIfNeeded.run();
            try { send(emitter, "done", "[DONE]"); } catch (Exception ignored) {}
            emitter.complete();
        });

        return emitter;
    }



    private static java.util.List<String> splitSmall(String s, int step) {
        if (s == null || s.isEmpty()) return java.util.List.of();
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < s.length(); i += step) {
            out.add(s.substring(i, Math.min(i + step, s.length())));
        }
        return out;
    }

    private static void flushEmitter(SseEmitter emitter, String evt, StringBuilder pending, StringBuilder sink) throws Exception {
        if (pending.length() == 0) return;
        String part = pending.toString();
        send(emitter, evt, part);
        sink.append(part);
        pending.setLength(0);
    }

    private static void send(SseEmitter emitter, String evt, String data) throws Exception {
        if (data == null || data.isEmpty()) return;
        emitter.send(SseEmitter.event().name(evt).data(data));
    }

    private static void safeErrorEnd(SseEmitter emitter, Throwable err) {
        try {
            emitter.send(SseEmitter.event().name("answer").data("\n\n（服务端异常：" + (err == null ? "" : err.getMessage()) + "）"));
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        } catch (Exception ignored) {}
        try { emitter.complete(); } catch (Exception ignored) {}
    }

}