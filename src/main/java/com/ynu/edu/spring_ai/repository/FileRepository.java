package com.ynu.edu.spring_ai.repository;

import org.springframework.core.io.Resource;

public interface FileRepository {
    boolean save(String chatId, Resource resource);

    Resource getFile(String chatId);
}
