package com.ynu.edu.spring_ai.mapper;

import com.ynu.edu.spring_ai.domain.entity.Doctor;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DoctorMapper {

    @Select("SELECT * FROM doctor WHERE id = #{id}")
    Doctor findById(Long id);

    @Select("SELECT * FROM doctor WHERE username = #{username}")
    Doctor findByUsername(String username);

    @Insert("""
        INSERT INTO doctor(username, password, name, dept)
        VALUES(#{username}, #{password}, #{name}, #{dept})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Doctor doctor);

    /**
     * 在问诊从 ONGOING -> FINISHED 时调用
     */
    @Update("""
    UPDATE doctor
    SET
      total_consult_count = total_consult_count + 1,
      today_consult_count = CASE
          WHEN last_consult_date = CURDATE() THEN today_consult_count + 1
          ELSE 1
      END,
      last_consult_date = CURDATE()
    WHERE id = #{doctorId}
    """)
    void increaseConsultCountOnFinish(@Param("doctorId") Long doctorId);
}
