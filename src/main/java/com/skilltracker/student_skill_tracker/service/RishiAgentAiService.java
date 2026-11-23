package com.skilltracker.student_skill_tracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.skilltracker.student_skill_tracker.model.Student;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * OpenAI-compatible tool-calling adapter. It deliberately accepts a caller
 * supplied gateway base URL so agent tool calls do not silently bypass the
 * configured AI gateway.
 */
@Service
public class RishiAgentAiService {

    private static final Logger logger = LoggerFactory.getLogger(RishiAgentAiService.class);

    private final RishiToolRegistry toolRegistry;

    public RishiAgentAiService(RishiToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public interface RishiAgent {
        String chat(String userMessage);
    }

    public AgentResponse executeWithTools(String apiKey, String model, String baseUrl, Student student,
            String userMessage, String systemPrompt) {
        toolRegistry.bindStudent(student);
        try {
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(AiGatewayUrlValidator.normalizeBaseUrl(baseUrl))
                    .modelName(model)
                    .temperature(0.7)
                    .maxTokens(900)
                    .build();

            RishiAgent agent = AiServices.builder(RishiAgent.class)
                    .chatModel(chatModel)
                    .tools(toolRegistry)
                    .systemMessage(systemPrompt)
                    .build();

            logger.info("Executing Rishi agent through configured OpenAI-compatible gateway for student: {}",
                    student.getEmail());
            String reply = agent.chat(userMessage);
            return new AgentResponse(reply, true);
        } finally {
            toolRegistry.clearStudent();
        }
    }

    public record AgentResponse(String reply, boolean toolsEnabled) {
    }
}
