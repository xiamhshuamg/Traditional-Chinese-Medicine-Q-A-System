package com.ynu.edu.spring_ai.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Slf4j
public class LocalPdfFileRepository implements FileRepository {

    // chatId -> stored filename
    private final Map<String, String> chatFiles = new ConcurrentHashMap<>();

    @Value("${pdf.storage-dir:./pdfs}")
    private String storageDir;

    @Override
    public boolean save(String chatId, Resource resource) {
        if (chatId == null || chatId.isBlank() || resource == null || resource.getFilename() == null) {
            return false;
        }

        File dir = new File(storageDir);
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("Failed to create storage directory: {}", storageDir);
            return false;
        }

        // 防止路径穿越
        String original = resource.getFilename().replace("\\", "/");
        original = original.substring(original.lastIndexOf("/") + 1);

        // 存储名加唯一前缀，避免同名覆盖
        String storedName = chatId + "_" + UUID.randomUUID() + "_" + original;
        File target = new File(dir, storedName);

        try {
            Files.copy(resource.getInputStream(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to save PDF to disk.", e);
            return false;
        }

        chatFiles.put(chatId, storedName);
        return true;
    }

    @Override
    public Resource getFile(String chatId) {
        String storedName = chatFiles.get(chatId);
        if (storedName == null) return null;

        File f = new File(storageDir, storedName);
        return f.exists() ? new FileSystemResource(f) : null;
    }
}
