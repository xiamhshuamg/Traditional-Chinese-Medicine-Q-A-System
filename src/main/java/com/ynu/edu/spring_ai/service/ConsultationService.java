package com.ynu.edu.spring_ai.service;

import com.ynu.edu.spring_ai.domain.entity.Consultation;
import com.ynu.edu.spring_ai.domain.entity.ConsultMessage;
import com.ynu.edu.spring_ai.mapper.ConsultMessageMapper;
import com.ynu.edu.spring_ai.mapper.ConsultationMapper;
import com.ynu.edu.spring_ai.mapper.DoctorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultationService {

    private final ConsultationMapper consultationMapper;
    private final ConsultMessageMapper consultMessageMapper;
    private final DoctorMapper doctorMapper;

    /**
     * 开始新问诊（当前医生接诊新病人）
     */
    @Transactional
    public Consultation startConsultation(Long doctorId, Long patientId) {
        Consultation c = new Consultation();
        c.setDoctorId(doctorId);
        c.setPatientId(patientId);
        c.setStatus("ONGOING");
        LocalDateTime now = LocalDateTime.now();
        c.setStartTime(now);
        c.setLastActiveTime(now);
        consultationMapper.insert(c);
        return c;
    }

//     往某次问诊里追加一条消息（医生 / 病人 / AI）
//     用于保持“记忆”。
    @Transactional
    public ConsultMessage appendMessage(Long consultationId,
                                        String senderType,
                                        Long senderId,
                                        String role,
                                        String content) {
        ConsultMessage msg = new ConsultMessage();
        msg.setConsultationId(consultationId);
        msg.setSenderType(senderType);
        msg.setSenderId(senderId);
        msg.setRole(role);
        msg.setContent(content);
        consultMessageMapper.insert(msg);

        consultationMapper.updateLastActive(consultationId, LocalDateTime.now());
        return msg;
    }

    /**
     * 结束问诊：写入结构化结果 + 更新医生统计
     */
    @Transactional
    public void finishConsultation(Consultation update) {
        Consultation db = consultationMapper.findById(update.getId());
        if (db == null) {
            throw new IllegalArgumentException("Consultation not found");
        }
        if (!"ONGOING".equals(db.getStatus())) {
            // 防止重复计数
            return;
        }

        db.setStatus("FINISHED");
        db.setEndTime(LocalDateTime.now());
        db.setChiefComplaint(update.getChiefComplaint());
        db.setPresentHistory(update.getPresentHistory());
        db.setTonguePulse(update.getTonguePulse());
        db.setTcmDiagnosis(update.getTcmDiagnosis());
        db.setWesternDiagnosis(update.getWesternDiagnosis());
        db.setPrescriptionJson(update.getPrescriptionJson());
        db.setSummary(update.getSummary());
        db.setLastActiveTime(LocalDateTime.now());

        consultationMapper.updateWhenFinish(db);

        // 更新医生统计
        doctorMapper.increaseConsultCountOnFinish(db.getDoctorId());
    }

    /**
     * 获取某次问诊的全部消息，用于“回到历史问诊并恢复记忆”
     */
    public List<ConsultMessage> getMessages(Long consultationId) {
        return consultMessageMapper.findByConsultationId(consultationId);
    }

    public Consultation findById(Long id) {
        return consultationMapper.findById(id);
    }
}
