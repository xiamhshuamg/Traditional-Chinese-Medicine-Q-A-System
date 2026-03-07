package com.ynu.edu.spring_ai.service;

import com.ynu.edu.spring_ai.repository.graph.KnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    
    private final ChatClient chatClient;
    private final KnowledgeGraphService knowledgeGraphService;
    
    /**
     * 处理聊天请求，包含知识图谱增强
     */
    public String processChat(String prompt, Long consultationId) {
        // 这里可以封装ChatController中的复杂逻辑
        // 比如构建历史消息、知识图谱分析等
        
        // 示例实现：
        KnowledgeGraphService.KnowledgeGraphResult kgResult = 
            knowledgeGraphService.analyzeWithKnowledgeGraph(prompt);
            
        String enhancedPrompt = buildEnhancedPrompt(prompt, kgResult);
        
        return chatClient.prompt(enhancedPrompt)
                .call()
                .content();
    }
    
    private String buildEnhancedPrompt(String prompt, KnowledgeGraphService.KnowledgeGraphResult kgResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题：").append(prompt).append("\n\n");
        
        if (kgResult.isHasValidAnalysis()) {
            sb.append("知识图谱分析结果：\n")
              .append(kgResult.getAnalysisText())
              .append("\n\n");
        }
        
        sb.append("请基于以上信息回答：");
        return sb.toString();
    }
    
    /**
     * 清理回复中的多余符号
     */
    public String cleanResponse(String answer) {
        if (answer == null) return "";
        
        String cleaned = answer
                .replaceAll("-{3,}", "")
                .replaceAll("\\*{3,}", "")
                .replaceAll("#{3,}", "")
                .replaceAll("_{3,}", "")
                .trim();
                
        return cleaned.isEmpty() ? answer : cleaned;
    }
}