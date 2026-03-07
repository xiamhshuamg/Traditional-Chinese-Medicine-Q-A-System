package com.ynu.edu.spring_ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.ynu.edu.spring_ai.domain.entity.ConsultMessage;
import com.ynu.edu.spring_ai.domain.entity.Consultation;
import com.ynu.edu.spring_ai.domain.entity.Doctor;
import com.ynu.edu.spring_ai.domain.entity.Patient;
import com.ynu.edu.spring_ai.domain.vo.ConsultationReportVO;
import com.ynu.edu.spring_ai.domain.vo.request.HerbItemVORequest;
import com.ynu.edu.spring_ai.mapper.DoctorMapper;
import com.ynu.edu.spring_ai.mapper.PatientMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConsultationReportService {

    private final ConsultationService consultationService;
    private final DoctorMapper doctorMapper;
    private final PatientMapper patientMapper;
    private final ObjectMapper objectMapper;

    // ========================= 对外：给 Controller 调用 =========================

    public ConsultationReportVO build(Long consultationId) {
        Consultation c = consultationService.findById(consultationId);
        if (c == null) throw new IllegalArgumentException("问诊不存在: " + consultationId);

        Doctor d = doctorMapper.findById(c.getDoctorId());
        Patient p = (c.getPatientId() == null) ? null : patientMapper.findById(c.getPatientId());

        List<ConsultMessage> msgs = consultationService.getMessages(consultationId);

        // 最后一条 assistant 作为 AI 建议来源
        String lastAi = msgs.stream()
                .filter(m -> "assistant".equalsIgnoreCase(nvl(m.getRole())))
                .reduce((a, b) -> b)
                .map(ConsultMessage::getContent)
                .orElse("");

        ParsedAi parsedAi = splitAiMessage(lastAi);

        ConsultationReportVO vo = new ConsultationReportVO();
        vo.setConsultationId(c.getId());
        vo.setStatus(c.getStatus());
        vo.setStartTime(c.getStartTime());
        vo.setEndTime(c.getEndTime());

        if (d != null) {
            vo.setDoctorName(d.getName());
            vo.setDoctorDept(d.getDept());
        }

        if (p != null) {
            vo.setPatientId(p.getId());
            vo.setPatientName(p.getName());
            vo.setPatientGender(p.getGender());
            vo.setPatientAge(calcAge(p.getBirthday()));
        }

        vo.setChiefComplaint(c.getChiefComplaint());
        vo.setPresentHistory(c.getPresentHistory());
        vo.setTonguePulse(c.getTonguePulse());
        vo.setTcmDiagnosis(c.getTcmDiagnosis());
        vo.setWesternDiagnosis(c.getWesternDiagnosis());
        vo.setSummary(c.getSummary());

        vo.setAiThought(parsedAi.thought);
        vo.setAiSuggestion(parsedAi.suggestion);

        // 药材：优先 prescriptionJson；否则从 aiSuggestion 里粗解析
        vo.setHerbs(parseHerbs(c.getPrescriptionJson(), parsedAi.suggestion));
        return vo;
    }

   //预览/编辑/打印页：医生可以修改四个区块，然后下载 PDF/Word
    public String buildHtml(Long consultationId) {
        ConsultationReportVO r = build(consultationId);

        String defaultSymptoms = buildDefaultSymptoms(r);

        String defaultSyndrome = cleanForReport(firstNonBlank(
                extractBlock(r.getAiSuggestion(), "证型", "辨证", "证候"),
                extractByKeys(r.getAiSuggestion(), "证型", "辨证", "证候"),
                r.getTcmDiagnosis(),
                "（请补充证型/辨证要点）"
        ));

        String defaultPrescription = cleanForReport(buildDefaultPrescription(r));

        String defaultAdvice = cleanForReport(firstNonBlank(
                extractBlock(r.getAiSuggestion(), "用药注意", "注意事项", "医嘱", "建议"),
                "（可填写：煎服法、忌口、复诊提醒等）"
        ));


        // 预览页：contenteditable + 下载按钮（POST 下载“编辑后的”）
        String html = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>处方单预览</title>
<style>
  body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,"PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;color:#111;margin:24px;}
  .h1{font-size:20px;font-weight:800;margin:0 0 6px;}
  .sub{color:#666;font-size:12px;margin:0 0 16px;}
  .toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:0 0 12px;}
  .actions{display:flex;gap:8px;flex-wrap:wrap;}
  .btn{border:1px solid #e5e7eb;background:#fff;border-radius:8px;padding:6px 10px;font-size:12px;cursor:pointer;}
  .btn:hover{background:#f8fafc;}
  .btn.primary{border-color:#111;color:#fff;background:#111;}
  .btn.primary:hover{opacity:.9;}
  .grid{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:10px 0 16px;}
  .card{border:1px solid #e5e7eb;border-radius:10px;padding:10px 12px;}
  .k{color:#666;font-size:12px;}
  .v{font-size:14px;margin-top:2px;word-break:break-word;white-space:pre-wrap;}
  .section{margin-top:12px;}
  .section-title{font-weight:800;margin:0 0 6px;}
  .box{white-space:pre-wrap;background:#f8fafc;border:1px solid #e5e7eb;border-radius:10px;padding:10px 12px;font-size:13px;min-height:90px;outline:none;}
  .hint{color:#666;font-size:12px;margin-top:6px;}
  details{margin-top:12px;}
  pre{white-space:pre-wrap;}
  @media print{ .no-print{display:none !important;} body{margin:0;} }
</style>
</head>
<body>

<div class="no-print toolbar">
  <div style="color:#666;font-size:12px;">可直接编辑下面内容；打印用 Ctrl+P / ⌘P</div>
  <div class="actions">
    <button class="btn" onclick="window.print()">打印（当前编辑）</button>
    <button class="btn" onclick="downloadPdf(false)">下载PDF（原始）</button>
    <button class="btn" onclick="downloadDocx(false)">下载Word（原始）</button>
    <button class="btn primary" onclick="downloadPdf(true)">下载PDF（当前编辑）</button>
    <button class="btn primary" onclick="downloadDocx(true)">下载Word（当前编辑）</button>
    <button class="btn" onclick="resetDraft()">恢复默认</button>
  </div>
</div>

<div class="h1">中医智能问诊 · 处方单预览</div>
<div class="sub">问诊ID：__CID__　状态：__STATUS__</div>

<div class="grid">
  <div class="card"><div class="k">医生</div><div class="v">__DOCTOR__</div></div>
  <div class="card"><div class="k">患者</div><div class="v">__PATIENT__</div></div>
  <div class="card"><div class="k">性别/年龄</div><div class="v">__GENDER_AGE__</div></div>
  <div class="card"><div class="k">时间</div><div class="v">__TIME__</div></div>
</div>

<div class="section">
  <div class="section-title">症状/主诉（可改）</div>
  <div id="symptoms" class="box" contenteditable="true">__SYMPTOMS__</div>
  <div class="hint">建议写：主诉、现病史要点、舌脉/体质等</div>
</div>

<div class="section">
  <div class="section-title">证型（可改）</div>
  <div id="syndrome" class="box" contenteditable="true">__SYNDROME__</div>
</div>

<div class="section">
  <div class="section-title">最终药方（可改）</div>
  <div id="prescription" class="box" contenteditable="true">__PRESCRIPTION__</div>
  <div class="hint">每行一个“药名 + 剂量”，例如：黄芪 15g</div>
</div>

<div class="section">
  <div class="section-title">医嘱/用药注意（可改）</div>
  <div id="advice" class="box" contenteditable="true">__ADVICE__</div>
</div>

<details class="no-print">
  <summary>（调试用）AI 思路/建议原文</summary>
  <h4>AI 思路（摘要）</h4>
  <pre>__AI_THOUGHT__</pre>
  <h4>AI 建议</h4>
  <pre>__AI_SUGGESTION__</pre>
</details>

<script>
  const CID = __CID_NUM__;

  const defaults = {
    symptoms: `__SYMPTOMS_JS__`,
    syndrome: `__SYNDROME_JS__`,
    prescription: `__PRESCRIPTION_JS__`,
    advice: `__ADVICE_JS__`,
  };

  function getDraft(){
    return {
      symptoms: document.getElementById('symptoms').innerText || '',
      syndrome: document.getElementById('syndrome').innerText || '',
      prescription: document.getElementById('prescription').innerText || '',
      advice: document.getElementById('advice').innerText || '',
    };
  }

  function resetDraft(){
    document.getElementById('symptoms').innerText = defaults.symptoms;
    document.getElementById('syndrome').innerText = defaults.syndrome;
    document.getElementById('prescription').innerText = defaults.prescription;
    document.getElementById('advice').innerText = defaults.advice;
  }

  async function downloadBlob(url, filename, method, bodyObj){
    const opt = { method: method || 'GET', headers: {} };
    if (bodyObj) {
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(bodyObj);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) {
      const msg = await resp.text().catch(()=>'');
      alert('下载失败：' + resp.status + '\\n' + msg);
      return;
    }
    const blob = await resp.blob();
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 3000);
  }

  function downloadPdf(useEdited){
    if (!useEdited) {
      location.href = `/api/consultations/${CID}/report/pdf`;
      return;
    }
    downloadBlob(`/api/consultations/${CID}/report/pdf`, `consultation-${CID}.pdf`, 'POST', getDraft());
  }

  function downloadDocx(useEdited){
    if (!useEdited) {
      location.href = `/api/consultations/${CID}/report/docx`;
      return;
    }
    downloadBlob(`/api/consultations/${CID}/report/docx`, `consultation-${CID}.docx`, 'POST', getDraft());
  }
</script>

</body>
</html>
""";

        html = html.replace("__CID__", String.valueOf(r.getConsultationId()))
                .replace("__CID_NUM__", String.valueOf(r.getConsultationId()))
                .replace("__STATUS__", esc(nullToDash(r.getStatus())))
                .replace("__DOCTOR__", esc(nullToDash(r.getDoctorName())) + (isBlank(r.getDoctorDept()) ? "" : "（" + esc(r.getDoctorDept()) + "）"))
                .replace("__PATIENT__", esc(nullToDash(r.getPatientName())))
                .replace("__GENDER_AGE__", esc(nullToDash(r.getPatientGender())) + " / " + (r.getPatientAge() == null ? "-" : r.getPatientAge() + " 岁"))
                .replace("__TIME__", esc(nullToDash(String.valueOf(r.getStartTime()))) + " ～ " + esc(nullToDash(String.valueOf(r.getEndTime()))))
                .replace("__SYMPTOMS__", esc(defaultSymptoms))
                .replace("__SYNDROME__", esc(defaultSyndrome))
                .replace("__PRESCRIPTION__", esc(defaultPrescription))
                .replace("__ADVICE__", esc(defaultAdvice))
                .replace("__AI_THOUGHT__", esc(nvl(r.getAiThought())))
                .replace("__AI_SUGGESTION__", esc(nvl(r.getAiSuggestion())))
                // JS 模板里用反引号，注意把反引号转义掉
                .replace("__SYMPTOMS_JS__", jsStr(defaultSymptoms))
                .replace("__SYNDROME_JS__", jsStr(defaultSyndrome))
                .replace("__PRESCRIPTION_JS__", jsStr(defaultPrescription))
                .replace("__ADVICE_JS__", jsStr(defaultAdvice));

        return html;
    }

    /** ✅ 下载原始 PDF（不编辑） */
    public byte[] buildPdf(Long consultationId) {
        return buildPdf(consultationId, null);
    }

    //下载编辑后的 PDF
//    public byte[] buildPdf(Long consultationId, ReportDraft draft) {
//        ConsultationReportVO r = build(consultationId);
//
//        String symptoms = (draft == null) ? buildDefaultSymptoms(r) : nvl(draft.getSymptoms());
//        String syndrome = (draft == null) ? firstNonBlank(extractByKeys(r.getAiSuggestion(),"证型","辨证","证候"), r.getTcmDiagnosis(), "-") : nvl(draft.getSyndrome());
//        String prescription = (draft == null) ? buildDefaultPrescription(r) : nvl(draft.getPrescription());
//        String advice = (draft == null) ? firstNonBlank(extractBlock(r.getAiSuggestion(),"用药注意","注意事项","医嘱","建议"), "") : nvl(draft.getAdvice());
//
//        String printable = buildPrintableHtml(r, symptoms, syndrome, prescription, advice);
//
//        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
//            PdfRendererBuilder builder = new PdfRendererBuilder();
//            builder.withHtmlContent(printable, null);
//            builder.toStream(out);
//            builder.useFastMode();
//
//            // 中文字体（Windows 优先）
//            tryUseWindowsFont(builder);
//
//            builder.run();
//            return out.toByteArray();
//        } catch (Exception e) {
//            throw new RuntimeException("PDF 生成失败: " + e.getMessage(), e);
//        }
//    }
    public byte[] buildPdf(Long consultationId, ReportDraft draft) {
        ConsultationReportVO r = build(consultationId);

        String symptoms = (draft == null) ? buildDefaultSymptoms(r) : nvl(draft.getSymptoms());
        String syndrome = (draft == null) ? firstNonBlank(extractByKeys(r.getAiSuggestion(),"证型","辨证","证候"), r.getTcmDiagnosis(), "-") : nvl(draft.getSyndrome());
        String prescription = (draft == null) ? buildDefaultPrescription(r) : nvl(draft.getPrescription());
        String advice = (draft == null) ? firstNonBlank(extractBlock(r.getAiSuggestion(),"用药注意","注意事项","医嘱","建议"), "") : nvl(draft.getAdvice());

        String printable = buildPrintableHtml(r, symptoms, syndrome, prescription, advice);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(printable, null);
            builder.toStream(out);
            builder.useFastMode();

            // 添加字体支持，确保有中文字体
            addFontSupport(builder);

            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF 生成失败: " + e.getMessage(), e);
        }
    }

    private void addFontSupport(PdfRendererBuilder builder) {
        // 尝试多种字体路径
        String[] fontPaths = {
                "C:/Windows/Fonts/simsun.ttc",      // 宋体
                "C:/Windows/Fonts/simhei.ttf",      // 黑体
                "C:/Windows/Fonts/msyh.ttc",        // 微软雅黑
                "C:/Windows/Fonts/msyhbd.ttc",      // 微软雅黑粗体
                "/System/Library/Fonts/PingFang.ttc", // MacOS 苹方
                "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc" // Linux 文泉驿
        };

        for (String fontPath : fontPaths) {
            try {
                File fontFile = new File(fontPath);
                if (fontFile.exists()) {
                    String fontName = getFontName(fontPath);
                    builder.useFont(fontFile, fontName);
                    log.info("使用字体: {} - {}", fontPath, fontName);
                }
            } catch (Exception e) {
                // 忽略字体加载失败，继续尝试下一个
            }
        }
    }

    private String getFontName(String fontPath) {
        if (fontPath.contains("simsun")) return "SimSun";
        if (fontPath.contains("simhei")) return "SimHei";
        if (fontPath.contains("msyh")) return "Microsoft YaHei";
        if (fontPath.contains("PingFang")) return "PingFang SC";
        if (fontPath.contains("wqy")) return "WenQuanYi Micro Hei";
        return "default";
    }
    /** ✅ 下载原始 DOCX（不编辑） */
    public byte[] buildDocx(Long consultationId) {
        return buildDocx(consultationId, null);
    }

    /** ✅ 下载“编辑后的 DOCX” */
    public byte[] buildDocx(Long consultationId, ReportDraft draft) {
        ConsultationReportVO r = build(consultationId);

        String symptoms = (draft == null) ? buildDefaultSymptoms(r) : nvl(draft.getSymptoms());
        String syndrome = (draft == null) ? firstNonBlank(extractByKeys(r.getAiSuggestion(),"证型","辨证","证候"), r.getTcmDiagnosis(), "-") : nvl(draft.getSyndrome());
        String prescription = (draft == null) ? buildDefaultPrescription(r) : nvl(draft.getPrescription());
        String advice = (draft == null) ? firstNonBlank(extractBlock(r.getAiSuggestion(),"用药注意","注意事项","医嘱","建议"), "") : nvl(draft.getAdvice());

        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 标题
            XWPFParagraph title = doc.createParagraph();
            XWPFRun tr = title.createRun();
            tr.setText("中医智能问诊 · 处方单");
            tr.setBold(true);
            tr.setFontFamily("宋体");
            tr.setFontSize(16);

            addLine(doc, "问诊ID：" + r.getConsultationId() + "   状态：" + nullToDash(r.getStatus()));
            addLine(doc, "医生：" + nullToDash(r.getDoctorName()) + (isBlank(r.getDoctorDept()) ? "" : "（" + r.getDoctorDept() + "）"));
            addLine(doc, "患者：" + nullToDash(r.getPatientName()) + "   性别/年龄：" + nullToDash(r.getPatientGender()) + "/" + (r.getPatientAge() == null ? "-" : r.getPatientAge() + "岁"));
            addLine(doc, "时间：" + nullToDash(String.valueOf(r.getStartTime())) + " ～ " + nullToDash(String.valueOf(r.getEndTime())));

            addH2(doc, "症状/主诉");
            addPre(doc, symptoms);

            addH2(doc, "证型");
            addPre(doc, syndrome);

            addH2(doc, "最终药方");
            addPre(doc, prescription);

            addH2(doc, "医嘱/用药注意");
            addPre(doc, advice);

            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("DOCX 生成失败: " + e.getMessage(), e);
        }
    }

    // ========================= Controller POST 需要用的 DTO =========================

    @Data
    public static class ReportDraft {
        private String symptoms;
        private String syndrome;
        private String prescription;
        private String advice;
    }

    // ========================= PDF 打印 HTML =========================
    private String buildPrintableHtml(ConsultationReportVO r,
                                      String symptoms,
                                      String syndrome,
                                      String prescription,
                                      String advice) {
        // 确保使用XML格式良好的HTML
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">" +
                "<head>" +
                "<meta charset=\"UTF-8\"/>" +
                "<title>处方单</title>" +
                "<style>" +
                "  @page { size: A4; margin: 16mm; }" +
                "  body{font-family:\"Microsoft YaHei\",\"SimHei\",\"PingFang SC\",sans-serif;color:#111;font-size:12px;}" +
                "  h1{font-size:16px;margin:0 0 6px;}" +
                "  .meta{margin:0 0 10px;color:#333;}" +
                "  .sec{margin-top:10px;}" +
                "  .sec h2{font-size:13px;margin:0 0 6px;}" +
                "  .box{border:1px solid #ddd;padding:8px;white-space:pre-wrap;line-height:1.5;}" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<h1>中医智能问诊 · 处方单</h1>" +
                "<div class=\"meta\">" +
                "问诊ID：" + r.getConsultationId() +
                "　医生：" + escapeXml(nullToDash(r.getDoctorName())) +
                "　患者：" + escapeXml(nullToDash(r.getPatientName())) +
                "　性别/年龄：" + escapeXml(nullToDash(r.getPatientGender())) +
                "/" + (r.getPatientAge() == null ? "-" : r.getPatientAge() + "岁") +
                "</div>" +

                "<div class=\"sec\">" +
                "  <h2>症状/主诉</h2>" +
                "  <div class=\"box\">" + escapeXml(symptoms) + "</div>" +
                "</div>" +

                "<div class=\"sec\">" +
                "  <h2>证型</h2>" +
                "  <div class=\"box\">" + escapeXml(syndrome) + "</div>" +
                "</div>" +

                "<div class=\"sec\">" +
                "  <h2>最终药方</h2>" +
                "  <div class=\"box\">" + escapeXml(prescription) + "</div>" +
                "</div>" +

                "<div class=\"sec\">" +
                "  <h2>医嘱/用药注意</h2>" +
                "  <div class=\"box\">" + escapeXml(advice) + "</div>" +
                "</div>" +

                "</body>" +
                "</html>";
    }

    // 添加XML转义方法
    private String escapeXml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
//    private String buildPrintableHtml(ConsultationReportVO r,
//                                      String symptoms,
//                                      String syndrome,
//                                      String prescription,
//                                      String advice) {
//        // 用最朴素的 HTML，openhtmltopdf 对复杂 CSS 支持没浏览器强
//        return ("""
//<!doctype html>
//<html lang="zh-CN">
//<head>
//<meta charset="utf-8"/>
//<style>
//  @page { size: A4; margin: 16mm; }
//  body{font-family:"Microsoft YaHei","SimHei","PingFang SC",sans-serif;color:#111;font-size:12px;}
//  h1{font-size:16px;margin:0 0 6px;}
//  .meta{margin:0 0 10px;color:#333;}
//  .sec{margin-top:10px;}
//  .sec h2{font-size:13px;margin:0 0 6px;}
//  .box{border:1px solid #ddd;padding:8px;white-space:pre-wrap;line-height:1.5;}
//</style>
//</head>
//<body>
//<h1>中医智能问诊 · 处方单</h1>
//<div class="meta">
//问诊ID：__CID__　医生：__DOCTOR__　患者：__PATIENT__　性别/年龄：__GA__
//</div>
//
//<div class="sec">
//  <h2>症状/主诉</h2>
//  <div class="box">__SYMPTOMS__</div>
//</div>
//
//<div class="sec">
//  <h2>证型</h2>
//  <div class="box">__SYNDROME__</div>
//</div>
//
//<div class="sec">
//  <h2>最终药方</h2>
//  <div class="box">__PRESCRIPTION__</div>
//</div>
//
//<div class="sec">
//  <h2>医嘱/用药注意</h2>
//  <div class="box">__ADVICE__</div>
//</div>
//
//</body>
//</html>
//""")
//                .replace("__CID__", String.valueOf(r.getConsultationId()))
//                .replace("__DOCTOR__", esc(nullToDash(r.getDoctorName())))
//                .replace("__PATIENT__", esc(nullToDash(r.getPatientName())))
//                .replace("__GA__", esc(nullToDash(r.getPatientGender())) + "/" + (r.getPatientAge() == null ? "-" : r.getPatientAge() + "岁"))
//                .replace("__SYMPTOMS__", esc(symptoms))
//                .replace("__SYNDROME__", esc(syndrome))
//                .replace("__PRESCRIPTION__", esc(prescription))
//                .replace("__ADVICE__", esc(advice));
//    }

    private static void tryUseWindowsFont(PdfRendererBuilder builder) {
        // 如果你在 Windows 上运行，推荐这两个
        String[] candidates = {
                "C:/Windows/Fonts/msyh.ttc",   // 微软雅黑
                "C:/Windows/Fonts/simhei.ttf"  // 黑体
        };
        for (String p : candidates) {
            try {
                File f = new File(p);
                if (f.exists()) {
                    builder.useFont(f, "Microsoft YaHei");
                    builder.useFont(f, "SimHei");
                }
            } catch (Exception ignored) {}
        }
    }

    // 1) 把前端 cleanedText 的逻辑后端化：保证打印/导出也干净
    private String cleanForReport(String raw) {
        if (raw == null) return "";
        String t = String.valueOf(raw);

        // 去掉 <<<...>>> 标记
        t = t.replaceAll("<<<[^>]+>>>", "");

        // **标题**：=>【标题】：
        t = t.replaceAll("\\*\\*([^*\\n]+)\\*\\*\\s*[:：]", "【$1】：");
        t = t.replaceAll("\\*\\*([^*\\n]+)\\*\\*", "$1");
        t = t.replaceAll("\\*([^*\\n]+)\\*", "$1");
        t = t.replaceAll("[＊*]+", "");

        // 去掉行首项目符号 "- " / "• "
        t = t.replaceAll("(?m)^\\s*[-•]\\s+", "");

        // 修复“【建议】）”
        t = t.replaceAll("【建议】\\s*）", "【建议】");

        // 删除空括号
        t = t.replaceAll("（\\s*）|\\(\\s*\\)", "");

        // 标题后两行空行压成一行
        t = t.replaceAll("(【[^】]+】[:：]?)\\s*\\n\\s*\\n", "$1\n");

        // 折叠空行
        t = t.replaceAll("\\n{3,}", "\n\n");

        return t.trim();
    }

    // 2) 从 AI 文本里尽量抽取“症状/主诉”默认内容
    private String buildSymptomsFromAi(ConsultationReportVO r) {
        String merged = (nvl(r.getAiThought()) + "\n" + nvl(r.getAiSuggestion())).trim();
        if (isBlank(merged)) return "";

        // 优先抓块（AI 有“主诉/现病史/四诊”等段落时很有用）
        String block = firstNonBlank(
                extractBlock(merged, "症状/主诉", "主诉", "现病史", "四诊", "舌脉", "舌象", "脉象", "体质"),
                ""
        );
        if (!isBlank(block)) return block;

        // 再退化为“按键提取”
        StringBuilder sb = new StringBuilder();
        String v;

        v = extractByKeys(merged, "主诉", "就诊原因");
        if (!isBlank(v)) sb.append("主诉：").append(v).append("\n");

        v = extractByKeys(merged, "现病史", "病程", "病史要点");
        if (!isBlank(v)) sb.append("现病史：").append(v).append("\n");

        v = extractByKeys(merged, "症状", "伴随症状");
        if (!isBlank(v)) sb.append("症状：").append(v).append("\n");

        v = extractByKeys(merged, "舌脉", "舌象", "脉象");
        if (!isBlank(v)) sb.append("舌脉：").append(v).append("\n");

        return sb.toString().trim();
    }

    // 3) 你原来的结构化默认症状（保留作为兜底）
    private String buildSymptomsFromStructured(ConsultationReportVO r) {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(r.getChiefComplaint())) sb.append("主诉：").append(r.getChiefComplaint()).append("\n");
        if (!isBlank(r.getPresentHistory())) sb.append("现病史：").append(r.getPresentHistory()).append("\n");
        if (!isBlank(r.getTonguePulse())) sb.append("舌脉：").append(r.getTonguePulse()).append("\n");
        return sb.toString().trim();
    }


    // ========================= 默认内容构造 =========================

    private String buildDefaultSymptoms(ConsultationReportVO r) {
        String s = firstNonBlank(
                buildSymptomsFromAi(r),
                buildSymptomsFromStructured(r),
                "（请补充主诉/现病史/舌脉等）"
        );
        return cleanForReport(s);
    }

    private String buildDefaultPrescription(ConsultationReportVO r) {
        List<HerbItemVORequest> herbs = (r.getHerbs() == null) ? List.of() : r.getHerbs();
        if (!herbs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (HerbItemVORequest h : herbs) {
                if (h == null || isBlank(h.getName())) continue;
                sb.append(h.getName());
                if (!isBlank(h.getDose())) sb.append(" ").append(h.getDose());
                sb.append(isBlank(h.getUnit()) ? "g" : h.getUnit());
                if (!isBlank(h.getNote())) sb.append("  ").append(h.getNote());
                sb.append("\n");
            }
            String s = sb.toString().trim();
            if (!s.isEmpty()) return s;
        }

        // 没结构化处方时：从 AI 建议里粗解析药名+剂量（或者返回提示）
        List<HerbItemVORequest> fromText = parseHerbsFromText(r.getAiSuggestion());
        if (!fromText.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (HerbItemVORequest h : fromText) {
                sb.append(h.getName()).append(" ").append(nvl(h.getDose())).append(nvl(h.getUnit())).append("\n");
            }
            return sb.toString().trim();
        }
        return "（暂无结构化处方：请在结束问诊时把处方写入 prescriptionJson，或让 AI 输出“药名+剂量”的行）";
    }

    // ========================= AI 文本解析（修复你 AI 建议为空的问题） =========================

    private static class ParsedAi {
        String thought;
        String suggestion;
        ParsedAi(String t, String s) { thought = t; suggestion = s; }
    }

    private ParsedAi splitAiMessage(String raw) {
        String txt = nvl(raw);

        // 1) 流式格式：<<<THOUGHT>>> ... <<<END_THOUGHT>>> <<<ANSWER>>> ... <<<END_ANSWER>>>
        String t = between(txt, "<<<THOUGHT>>>", "<<<END_THOUGHT>>>");
        String a = between(txt, "<<<ANSWER>>>", "<<<END_ANSWER>>>");
        if (!isBlank(t) || !isBlank(a)) return new ParsedAi(trimOrEmpty(t), trimOrEmpty(a));

        // 2) 你保存到库里的格式： 【思路概述】...【建议】...
        int i1 = txt.indexOf("【思路概述】");
        int i2 = txt.indexOf("【建议】");
        if (i1 >= 0 && i2 > i1) {
            String thought = txt.substring(i1 + "【思路概述】".length(), i2).trim();
            String sug = txt.substring(i2 + "【建议】".length()).trim();
            return new ParsedAi(thought, sug);
        }

        // 3) 兜底：整段当建议
        return new ParsedAi("", txt.trim());
    }

    private static String between(String s, String a, String b) {
        int i = s.indexOf(a);
        if (i < 0) return "";
        int j = s.indexOf(b, i + a.length());
        if (j < 0) return "";
        return s.substring(i + a.length(), j);
    }

    // ========================= 处方解析 =========================

    private List<HerbItemVORequest> parseHerbs(String prescriptionJson, String suggestionText) {
        // 1) 优先 JSON
        if (!isBlank(prescriptionJson)) {
            try {
                String trim = prescriptionJson.trim();
                if (trim.startsWith("[")) {
                    return objectMapper.readValue(trim, new TypeReference<List<HerbItemVORequest>>() {});
                } else {
                    Map<String, Object> root = objectMapper.readValue(trim, new TypeReference<Map<String, Object>>() {});
                    Object herbs = root.get("herbs");
                    if (herbs == null) herbs = root.get("items");
                    if (herbs != null) {
                        String tmp = objectMapper.writeValueAsString(herbs);
                        return objectMapper.readValue(tmp, new TypeReference<List<HerbItemVORequest>>() {});
                    }
                }
            } catch (Exception ignored) { }
        }
        // 2) 再从文本粗解析
        return parseHerbsFromText(suggestionText);
    }

    private List<HerbItemVORequest> parseHerbsFromText(String text) {
        if (isBlank(text)) return List.of();

        Pattern p = Pattern.compile("(?m)^\\s*[-•]*\\s*([\\u4e00-\\u9fa5]{1,12})\\s*(\\d+(?:\\.\\d+)?)\\s*(g|克|G|mg|毫克)?\\s*(.*)$");
        Matcher m = p.matcher(text);

        List<HerbItemVORequest> list = new ArrayList<>();
        while (m.find()) {
            String name = m.group(1);
            String dose = m.group(2);
            String unit = m.group(3);
            String note = m.group(4);

            if (isBlank(name) || isBlank(dose)) continue;

            HerbItemVORequest hi = new HerbItemVORequest();
            hi.setName(name.trim());
            hi.setDose(dose.trim());
            hi.setUnit(isBlank(unit) ? "g" : unit.trim());
            hi.setNote(isBlank(note) ? "" : note.trim());
            list.add(hi);

            if (list.size() >= 60) break;
        }
        return list;
    }

    // ========================= 从 AI 建议里抽取 “证型/注意事项” 之类 =========================

    private static String extractByKeys(String text, String... keys) {
        if (isBlank(text)) return "";
        for (String k : keys) {
            if (isBlank(k)) continue;
            // 形如：证型：xxx  或  证型：xxx（换行）
            Pattern p = Pattern.compile("(?m)^\\s*" + Pattern.quote(k) + "\\s*[:：]\\s*(.+)$");
            Matcher m = p.matcher(text);
            if (m.find()) return nvl(m.group(1)).trim();
        }
        return "";
    }

    private static String extractBlock(String text, String... titles) {
        if (isBlank(text)) return "";
        String t = text;
        for (String title : titles) {
            if (isBlank(title)) continue;
            int i = t.indexOf(title);
            if (i < 0) continue;
            // 从 title 这一行开始往后取几行
            String tail = t.substring(i);
            String[] lines = tail.split("\\R");
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < Math.min(lines.length, 10); idx++) {
                String line = lines[idx].trim();
                if (line.isEmpty()) continue;
                sb.append(line).append("\n");
            }
            String out = sb.toString().trim();
            if (!out.isEmpty()) return out;
        }
        return "";
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null) return "";
        for (String p : parts) {
            if (!isBlank(p)) return p.trim();
        }
        return "";
    }

    // ========================= Word 生成小工具 =========================

    private static void addH2(XWPFDocument doc, String title) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontFamily("宋体");
        r.setFontSize(13);
        r.setText(title);
    }

    private static void addLine(XWPFDocument doc, String line) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setFontFamily("宋体");
        r.setFontSize(11);
        r.setText(line);
    }

    private static void addPre(XWPFDocument doc, String content) {
        String[] lines = nvl(content).split("\\R");
        for (String line : lines) addLine(doc, line);
    }

    // ========================= 其它工具 =========================

    private Integer calcAge(LocalDate birthday) {
        if (birthday == null) return null;
        try {
            return Period.between(birthday, LocalDate.now()).getYears();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String nvl(String s) { return s == null ? "" : s; }
    private static String trimOrEmpty(String s) { return s == null ? "" : s.trim(); }
    private static String nullToDash(String s) { return isBlank(s) ? "-" : s; }

    /** HTML 转义 */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;")
                .replace("<","&lt;")
                .replace(">","&gt;")
                .replace("\"","&quot;")
                .replace("'","&#39;");
    }

    /** 放进 JS 反引号字符串里（把反引号、反斜杠转义一下） */
    private static String jsStr(String s) {
        String x = nvl(s);
        x = x.replace("\\", "\\\\").replace("`", "\\`");
        return x;
    }
}
