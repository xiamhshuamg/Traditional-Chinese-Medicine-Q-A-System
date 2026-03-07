package com.ynu.edu.spring_ai.domain.model;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Data
@Node("中药")
public class Herb {
    @Id
    private String id;
    @Property("名称")
    private String name;
    @Property("来源")
    private String source;
    @Property("剂量")
    private Double dosage;
}