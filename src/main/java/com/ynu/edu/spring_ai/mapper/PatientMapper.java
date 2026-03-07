package com.ynu.edu.spring_ai.mapper;

import com.ynu.edu.spring_ai.domain.entity.Patient;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PatientMapper {

    @Select("SELECT * FROM patient WHERE id = #{id}")
    Patient findById(Long id);

    @Insert("""
        INSERT INTO patient(name, gender, birthday, phone, id_number)
        VALUES(#{name}, #{gender}, #{birthday}, #{phone}, #{idNumber})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Patient patient);

    @Update("UPDATE patient SET name=#{name}, gender=#{gender}, birthday=#{birthday}, phone=#{phone}, id_number=#{idNumber} WHERE id=#{id}")
    void update(Patient patient);
}
