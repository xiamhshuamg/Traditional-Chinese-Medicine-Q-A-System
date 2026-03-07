package com.ynu.edu.spring_ai.domain.model;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Data
@Node("方剂")
public class PrescriptionFormula {
    @Id
    private String id;
    @Property("名称")
    private String name;
}