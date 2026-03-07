package com.ynu.edu.spring_ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Slf4j
public class PdfService {

    private final ChatClient pdfClient;
    private final VectorStore vectorStore;

    public PdfService(@Qualifier("pdfClient") ChatClient pdfClient, VectorStore vectorStore) {
        this.pdfClient = pdfClient;
        this.vectorStore = vectorStore;
    }

    /**
     * 基于 PDF + 向量库的问答
     */
    public String chatWithPdf(String prompt, String chatId, Resource pdfFile) {
        boolean hasPdf = pdfFile != null && pdfFile.exists();

        // 没有上传文件就当普通聊天
        if (!hasPdf) {
            return pdfClient.prompt(prompt).call().content().replace("\n", "");
        }

        // 关键：先从向量库检索，再把检索片段喂给模型
        SearchRequest request = SearchRequest.builder()
                .query(prompt)
                .topK(5)
                .filterExpression("chatId=='" + chatId + "'")
                .build();

        List<Document> docs = vectorStore.similaritySearch(request);
        if (docs == null || docs.isEmpty()) {
            return "资料中没有相关内容。";
        }

        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            ctx.append("【片段").append(i + 1).append("】\n")
                    .append(docs.get(i).getText())
                    .append("\n\n");
        }

        String finalPrompt = """
                你只能根据【资料】回答问题；如果资料里没有，回答：资料中没有相关内容。

                【资料】
                %s

                【问题】
                %s
                """.formatted(ctx, prompt);

        return pdfClient.prompt(finalPrompt).call().content().replace("\n", "");
    }

    /**
     * 处理 PDF 上传并向量化
     */
    public void processPdfUpload(String chatId, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }

        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                file.getResource(),
                PdfDocumentReaderConfig.builder()
                        .withPagesPerDocument(1)
                        .build()
        );

        List<Document> pages = reader.get();

        // 分块（检索更准）
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(pages);

        for (Document doc : chunks) {
            doc.getMetadata().put("chatId", chatId);
            doc.getMetadata().put("filename", file.getOriginalFilename());
        }

        vectorStore.add(chunks);
        log.info("成功处理PDF并写入向量库：chatId={}, chunks={}", chatId, chunks.size());
    }
}
