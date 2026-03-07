package com.ynu.edu.spring_ai.controller;

import com.ynu.edu.spring_ai.domain.vo.request.LoginRequest;
import com.ynu.edu.spring_ai.domain.vo.request.AddMessageRequest;
import com.ynu.edu.spring_ai.domain.vo.request.UpdatePatientRequest;
import com.ynu.edu.spring_ai.domain.entity.Consultation;
import com.ynu.edu.spring_ai.domain.entity.ConsultMessage;
import com.ynu.edu.spring_ai.domain.entity.Doctor;
import com.ynu.edu.spring_ai.domain.entity.Patient;
import com.ynu.edu.spring_ai.domain.vo.Result;
import com.ynu.edu.spring_ai.mapper.ConsultationMapper;
import com.ynu.edu.spring_ai.mapper.DoctorMapper;
import com.ynu.edu.spring_ai.mapper.PatientMapper;
import com.ynu.edu.spring_ai.repository.graph.KnowledgeGraphService;
import com.ynu.edu.spring_ai.service.FileService;
import com.ynu.edu.spring_ai.service.ConsultationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping({"/api/consultations", "/consultations"})
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;
    private final DoctorMapper doctorMapper;
    private final PatientMapper patientMapper;
    private final ConsultationMapper consultationMapper;
    private final FileService fileService;
    private final KnowledgeGraphService knowledgeGraphService;

    // 1. 医生登录接口
    @PostMapping("/doctors/login")
    public Doctor doctorLogin(@RequestBody LoginRequest request) {
        Doctor doctor = doctorMapper.findById(request.getDoctorId());
        if (doctor != null && doctor.getName().equals(request.getName())) {
            return doctor;
        }
        throw new RuntimeException("医生信息验证失败");
    }

    // 2. 开始新问诊
    @PostMapping("/start")
    public Consultation start(@RequestParam Long doctorId,
                              @RequestParam(required = false) Long patientId) {
        return consultationService.startConsultation(doctorId, patientId);
    }

    // 3. 在问诊中追加一条消息（医生 / 病人 / AI）
    @PostMapping("/{id}/messages")
    public ConsultMessage addMessage(@PathVariable("id") Long consultationId,
                                     @RequestBody AddMessageRequest req) {
        return consultationService.appendMessage(
                consultationId,
                req.getSenderType(),
                req.getSenderId(),
                req.getRole(),
                req.getContent()
        );
    }

    // 4. 获取某次问诊的全部消息
    @GetMapping("/{id}/messages")
    public List<ConsultMessage> getMessages(@PathVariable("id") Long consultationId) {
        return consultationService.getMessages(consultationId);
    }

    // 5. 结束问诊
    @PostMapping("/{id}/finish")
    public Result finish(@PathVariable("id") Long consultationId,
                         @RequestBody Consultation payload) {
        payload.setId(consultationId);
        consultationService.finishConsultation(payload);
        return Result.ok();
    }

    // 6. 更新患者信息（兼容 PUT/POST + 兼容末尾 /）
    @RequestMapping(value = {"/{id}/patient", "/{id}/patient/"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public Result updatePatientInfo(@PathVariable("id") Long consultationId,
                                    @RequestBody UpdatePatientRequest request) {
        try {
            Consultation consultation = consultationService.findById(consultationId);
            if (consultation == null) return Result.fail("问诊不存在");

            // 1) 找到或创建 Patient
            Patient patient = null;
            if (consultation.getPatientId() != null) {
                patient = patientMapper.findById(consultation.getPatientId());
            }
            boolean isNew = (patient == null);
            if (isNew) patient = new Patient();

            // 2) 写入患者字段
            patient.setName(request.getName());
            patient.setGender(request.getGender());
            patient.setPhone(request.getPhone());
            patient.setIdNumber(request.getIdNumber());
            if (request.getAge() != null && request.getAge() > 0) {
                patient.setBirthday(LocalDate.now().minusYears(request.getAge()));
            }

            // 3) 保存 patient
            if (isNew) {
                patientMapper.insert(patient);
            } else {
                patientMapper.update(patient);
            }

            // 4) 绑定到 consultation（确保刷新后能 JOIN 到 patient）
            if (consultation.getPatientId() == null || !consultation.getPatientId().equals(patient.getId())) {
                consultationMapper.updatePatientId(consultationId, patient.getId());
            }

            return Result.ok(Map.of(
                    "patientId", patient.getId(),
                    "patientName", patient.getName(),
                    "patientGender", patient.getGender(),
                    "patientAge", request.getAge()
            ));
        } catch (Exception e) {
            return Result.fail("更新患者信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取医生统计数据
     */
    @GetMapping("/doctors/{id}/stats")
    public Map<String, Object> getDoctorStats(@PathVariable Long id) {
        Doctor doctor = doctorMapper.findById(id);
        if (doctor == null) {
            throw new RuntimeException("医生不存在");
        }

        return Map.of(
                "totalConsultCount", doctor.getTotalConsultCount() != null ? doctor.getTotalConsultCount() : 0,
                "todayConsultCount", doctor.getTodayConsultCount() != null ? doctor.getTodayConsultCount() : 0
        );
    }

    /**
     * 根据医生ID获取问诊列表
     */
    @GetMapping("/byDoctor")
    public List<Consultation> listByDoctor(@RequestParam Long doctorId) {
        return consultationMapper.findByDoctor(doctorId);
    }

    // 添加新的统计接口
    @GetMapping("/doctors/{id}/today-stats")
    public Map<String, Object> getTodayStats(@PathVariable Long id) {
        Integer todayCount = consultationMapper.countTodayConsultationsByDoctor(id);
        return Map.of("todayConsultCount", todayCount != null ? todayCount : 0);
    }

    @PostMapping(value = "/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result uploadFile(@RequestParam("file") MultipartFile file,
                             @RequestParam("consultationId") Long consultationId) {
        try {
            String filePath = fileService.saveFile(file, consultationId);

            // ✅ 只提取一次
            String extracted = fileService.extractTextContent(file);
            String extractedShort = extracted;
            if (extractedShort != null && extractedShort.length() > 8000) {
                extractedShort = extractedShort.substring(0, 8000) + "\n.(原文过长已截断)";
            }

            // ✅ 知识图谱直接用文本，不要再传 file 进去重复解析
            String analysis = knowledgeGraphService.buildHintFromText(extracted == null ? "" : extracted);
            String safeAnalysis = (analysis == null || analysis.isBlank())
                    ? "（知识图谱未命中或内容不足）"
                    : analysis;

            String systemContent =
                    "【病历解析结果】\n" +
                            "文件：" + (file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename()) + "\n\n" +
                            "【病历原文(提取)】\n" + (extractedShort == null ? "" : extractedShort) + "\n\n" +
                            "【知识图谱匹配】\n" + safeAnalysis;

            consultationService.appendMessage(consultationId, "SYSTEM", null, "system", systemContent);

            return Result.ok(Map.of("filePath", filePath, "analysis", safeAnalysis));

        } catch (Throwable e) { // ✅ 兜底 Error，避免 500
            return Result.fail("文件上传失败: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

}
