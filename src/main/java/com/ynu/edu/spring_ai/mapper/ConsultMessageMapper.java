package com.ynu.edu.spring_ai.mapper;

import com.ynu.edu.spring_ai.domain.entity.ConsultMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ConsultMessageMapper {

    @Insert("""
        INSERT INTO consult_message
        (consultation_id, sender_type, sender_id, role, content)
        VALUES
        (#{consultationId}, #{senderType}, #{senderId}, #{role}, #{content})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(ConsultMessage msg);

    @Select("""
        SELECT * FROM consult_message
        WHERE consultation_id = #{consultationId}
        ORDER BY created_at ASC
        """)
    List<ConsultMessage> findByConsultationId(Long consultationId);
}
