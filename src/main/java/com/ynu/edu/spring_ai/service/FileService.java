package com.ynu.edu.spring_ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /** 保存上传的文件 */
    public String saveFile(MultipartFile file, Long consultationId) throws IOException {
        Path consultationDir = Paths.get(uploadDir, "consultation_" + consultationId);
        if (!Files.exists(consultationDir)) {
            Files.createDirectories(consultationDir);
        }

        String originalFileName = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        String fileName = UUID.randomUUID() + fileExtension;

        Path filePath = consultationDir.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        log.info("文件保存成功: {}", filePath.toAbsolutePath());
        return filePath.toAbsolutePath().toString();
    }

    /** 提取文件内容 */
    public String extractTextContent(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();

        if (contentType == null) {
            return String.format("无法识别文件类型，文件名: %s, 大小: %d bytes", fileName, file.getSize());
        }

        // 文本文件
        if (contentType.startsWith("text/")) {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }

        // PDF：contentType 或扩展名兜底
        boolean isPdf = "application/pdf".equalsIgnoreCase(contentType)
                || (fileName != null && fileName.toLowerCase().endsWith(".pdf"));
        if (isPdf) {
            return extractTextFromPdfSafely(file);
        }

        // 图片
        if (contentType.startsWith("image/")) {
            return String.format("图片文件: %s, 大小: %d bytes (需要OCR处理)", fileName, file.getSize());
        }

        // Word
        if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType)) {
            return String.format("Word文档: %s, 大小: %d bytes", fileName, file.getSize());
        }

        return String.format("文件类型: %s, 文件名: %s, 大小: %d bytes", contentType, fileName, file.getSize());
    }

    /**
     * 关键：这里一定要 catch(Throwable，避免 pdfbox 依赖缺失时抛 NoClassDefFoundError 直接 500
     */
    private String extractTextFromPdfSafely(MultipartFile file) {
        try {
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPagesPerDocument(1)
                    .build();

            PagePdfDocumentReader reader = new PagePdfDocumentReader(file.getResource(), config);
            var documents = reader.get();

            String text = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));


            return text == null ? "" : text;

        } catch (Throwable t) { // ✅ 兜住 NoClassDefFoundError / LinkageError 等
            log.error("PDF解析失败（可能缺少 PDFBox 依赖，或PDF损坏）: {}", t.toString(), t);
            String msg = (t.getMessage() == null || t.getMessage().isBlank()) ? t.toString() : t.getMessage();
            return "PDF解析失败：" + msg + "\n（提示：后端需要加入 Apache PDFBox 依赖，见我下面的 pom/gradle 配置）";
        }
    }
}
