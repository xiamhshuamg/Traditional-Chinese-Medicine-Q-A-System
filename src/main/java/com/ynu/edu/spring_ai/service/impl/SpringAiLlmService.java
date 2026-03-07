package com.ynu.edu.spring_ai.service.impl;
import com.ynu.edu.spring_ai.repository.graph.LlmService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;


@Service
public class SpringAiLlmService implements LlmService {

    private final ChatClient chatClient;

    public SpringAiLlmService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String generate(String prompt) {
        try {
            // 单次同步调用，保持温度较低可在 ChatModel 默认配置里设置
            return chatClient
                    .prompt(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            return "【调用异常】" + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}