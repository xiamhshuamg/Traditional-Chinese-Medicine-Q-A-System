package com.ynu.edu.spring_ai.mapper;

import com.ynu.edu.spring_ai.domain.entity.Consultation;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ConsultationMapper {

    @Insert("""
        INSERT INTO consultation
        (doctor_id, patient_id, status, start_time, last_active_time)
        VALUES
        (#{doctorId}, #{patientId}, #{status}, #{startTime}, #{lastActiveTime})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Consultation consultation);

    @Select("SELECT * FROM consultation WHERE id = #{id}")
    Consultation findById(Long id);

    @Update("""
        UPDATE consultation
        SET
          status = #{status},
          end_time = #{endTime},
          chief_complaint = #{chiefComplaint},
          present_history = #{presentHistory},
          tongue_pulse = #{tonguePulse},
          tcm_diagnosis = #{tcmDiagnosis},
          western_diagnosis = #{westernDiagnosis},
          prescription_json = #{prescriptionJson},
          summary = #{summary},
          last_active_time = #{lastActiveTime}
        WHERE id = #{id}
        """)
    void updateWhenFinish(Consultation consultation);

    @Update("""
        UPDATE consultation
        SET last_active_time = #{time}
        WHERE id = #{id}
        """)
    void updateLastActive(@Param("id") Long id, @Param("time") LocalDateTime time);

    // ✅ 关键：JOIN patient，把 patientName/patientGender/patientAge 返回给前端
    @Select("""
SELECT c.*,
       p.name   AS patientName,
       p.gender AS patientGender,
       TIMESTAMPDIFF(YEAR, p.birthday, CURDATE()) AS patientAge
FROM consultation c
LEFT JOIN patient p ON c.patient_id = p.id
WHERE c.doctor_id = #{doctorId}
ORDER BY c.last_active_time DESC
""")
    List<Consultation> findByDoctor(Long doctorId);

    @Update("UPDATE consultation SET patient_id = #{patientId} WHERE id = #{consultationId}")
    void updatePatientId(@Param("consultationId") Long consultationId, @Param("patientId") Long patientId);

    @Select("""
    SELECT COUNT(*) FROM consultation 
    WHERE doctor_id = #{doctorId} 
    AND DATE(start_time) = CURDATE()
    """)
    Integer countTodayConsultationsByDoctor(Long doctorId);

    @Update("""
    UPDATE consultation
    SET prescription_json = #{json}, last_active_time = NOW()
    WHERE id = #{id}
""")
    void updatePrescriptionJson(@Param("id") Long id, @Param("json") String json);
}
