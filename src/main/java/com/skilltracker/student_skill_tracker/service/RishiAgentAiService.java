package com.skilltracker.student_skill_tracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.skilltracker.student_skill_tracker.model.Student;

import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Bridges the existing RishiGenAiService with LangChain4j's AiServices for tool
 * calling.
 *
 * Architecture decision: The Mistral API key is per-student (encrypted in DB),
 * so we CANNOT
 * use a singleton ChatModel bean. Instead, we construct the ChatModel
 * per-request with
 * the student's decrypted key, then wire it through AiServices with the tool
 * registry.
 *
 * This service provides two modes:
 * 1. AGENT MODE (with tools): AI reasons about telemetry and can invoke tools.
 * 2. CHAT MODE (no tools): Standard conversational AI, preserving existing
 * behavior.
 */
@Service
public class RishiAgentAiService {

    private static final Logger logger = LoggerFactory.getLogger(RishiAgentAiService.class);

    private final RishiToolRegistry toolRegistry;

    public RishiAgentAiService(RishiToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * The declarative AI Service interface. LangChain4j will proxy this
     * and automatically handle tool calling negotiation with the LLM.
     */
    public interface RishiAgent {
        String chat(String userMessage);
    }

    /**
     * Executes an agent request with full tool-calling capabilities.
     * The AI can reason on telemetry and invoke tools like lockCompiler(),
     * triggerAmbushDuel(), etc.
     *
     * @param apiKey       Decrypted Mistral API key
     * @param model        Model name (e.g., "open-mixtral-8x7b")
     * @param student      Target student for tool binding
     * @param userMessage  The user's message or agent observation
     * @param systemPrompt The system prompt with context
     * @return The AI's textual response (after any tool executions)
     */
    public AgentResponse executeWithTools(String apiKey, String model, Student student,
            String userMessage, String systemPrompt) {
        toolRegistry.bindStudent(student);
        try {
            MistralAiChatModel chatModel = MistralAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(model)
                    .temperature(0.7)
                    .maxTokens(900)
                    .build();

            RishiAgent agent = AiServices.builder(RishiAgent.class)
                    .chatModel(chatModel)
                    .tools(toolRegistry)
                    .systemMessage(systemPrompt)
                    .build();

            logger.info("Executing agent with tools for student: {}", student.getEmail());
            String reply = agent.chat(userMessage);
            return new AgentResponse(reply, true);
        } finally {
            toolRegistry.clearStudent();
        }
    }

    /**
     * Executes a simple chat without tools (preserving existing chat mode
     * behavior).
     */
    public AgentResponse executeChat(String apiKey, String model,
            String userMessage, String systemPrompt) {

        MistralAiChatModel chatModel = MistralAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.7)
                .maxTokens(900)
                .build();

        RishiAgent agent = AiServices.builder(RishiAgent.class)
                .chatModel(chatModel)
                .systemMessage(systemPrompt)
                .build();

        String reply = agent.chat(userMessage);
        return new AgentResponse(reply, false);
    }

    /**
     * Encapsulates the agent's response along with metadata about tool usage.
     */
    public record AgentResponse(String reply, boolean toolsEnabled) {
    }
}
