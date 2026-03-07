package com.ynu.edu.spring_ai.repository;

import com.ynu.edu.spring_ai.domain.model.Herb;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface HerbRepository extends Neo4jRepository<Herb, String> {
    Herb findByName(String name);
}