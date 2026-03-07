package com.ynu.edu.spring_ai.config;

import com.ynu.edu.spring_ai.constans.SystemConstants;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CommonConfiguration {

    /** 主中医问诊LLM（无内置持久记忆，由我们自己喂历史消息） */
    @Bean("chatClient")
    @Primary
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是一位中医界的泰斗，熟悉《黄帝内经》《伤寒论》《金匮要略》，
                        精通辨证论治、方剂加减和中药配伍，请始终以中医专家的身份回答问题。
                        回答时说明辨证思路，再给出方剂或建议。
                        """)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    /** PDF 问答专用 */
    @Bean("pdfClient")
    public ChatClient pdfClient(ChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem("""
                        你是一个中医文献问答助手，只能根据提供的文献内容作答，
                        没有相关内容就回答"资料中没有相关内容"。
                        """)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    /** 中医教学 / 演练模式 */
    @Bean("gameChatClient")
    public ChatClient gameChatClient(ChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(SystemConstants.GAME_SYSTEM_PROMPT)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    /** RAG专用客户端 */
    @Bean("ragChatClient")
    public ChatClient ragChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                    你是一位中医专家，专门基于提供的相似病例进行分析和推荐。
                    请仔细分析相似病例，结合中医理论给出合理的诊断和治疗方案。
                    """)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}