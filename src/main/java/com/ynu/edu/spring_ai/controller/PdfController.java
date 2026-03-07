package com.ynu.edu.spring_ai.controller;

import com.ynu.edu.spring_ai.domain.vo.Result;
import com.ynu.edu.spring_ai.repository.FileRepository;
import com.ynu.edu.spring_ai.service.PdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/ai/pdf")
@RequiredArgsConstructor
public class PdfController {

    private final FileRepository fileRepository;
    private final PdfService pdfService;

    /**
     * 基于 PDF + 向量库的问答
     */
    @GetMapping(value = "/chat", produces = "text/plain;charset=utf-8")
    public String chat(@RequestParam("prompt") String prompt,
                       @RequestParam("chatId") String chatId) {
        Resource file = fileRepository.getFile(chatId);
        return pdfService.chatWithPdf(prompt, chatId, file);
    }

    /**
     * 上传 PDF（保存到本地 + 向量化）
     */
    @PostMapping(value = "/upload/{chatId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result upload(@PathVariable("chatId") String chatId,
                         @RequestPart("file") MultipartFile file) {
        try {
            // 1) 先存文件
            boolean ok = fileRepository.save(chatId, file.getResource());
            if (!ok) {
                return Result.fail("保存文件失败");
            }

            // 2) 再向量化
            pdfService.processPdfUpload(chatId, file);

            return Result.ok();
        } catch (Exception e) {
            log.error("上传PDF失败", e);
            return Result.fail("上传文件失败");
        }
    }

    /**
     * 下载已上传的 PDF
     */
    @GetMapping("/file/{chatId}")
    public ResponseEntity<Resource> download(@PathVariable("chatId") String chatId) {
        try {
            Resource resource = fileRepository.getFile(chatId);
            if (resource == null || !resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            String filename = URLEncoder.encode(
                    Objects.requireNonNull(resource.getFilename()),
                    StandardCharsets.UTF_8
            );

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("下载PDF失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
