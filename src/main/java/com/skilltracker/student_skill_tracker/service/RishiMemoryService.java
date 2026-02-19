package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skilltracker.student_skill_tracker.model.Student;

@Service
public class RishiMemoryService {

    private static final int MAX_MEMORY_MESSAGES = 30;
    private final ObjectMapper objectMapper;

    public RishiMemoryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record MemoryMessage(String role, String content, String type, String timestamp) {}

    public List<MemoryMessage> getMemory(Student student) {
        if (student == null || student.getRishiMemoryJson() == null || student.getRishiMemoryJson().isBlank()) {
            return new ArrayList<>();
        }

        try {
            List<MemoryMessage> parsed = objectMapper.readValue(student.getRishiMemoryJson(),
                    new TypeReference<List<MemoryMessage>>() {});
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    public void appendExchange(Student student, String userMessage, String userType, String assistantMessage,
            String assistantType) {
        List<MemoryMessage> memory = getMemory(student);
        String now = LocalDateTime.now().toString();
        memory.add(new MemoryMessage("user", safe(userMessage), safeType(userType), now));
        memory.add(new MemoryMessage("assistant", safe(assistantMessage), safeType(assistantType), now));
        trim(memory);
        persist(student, memory);
    }

    public void clearMemory(Student student) {
        student.setRishiMemoryJson("[]");
    }

    public List<Map<String, String>> toModelContext(List<MemoryMessage> memory, int maxMessages) {
        if (memory == null || memory.isEmpty()) {
            return List.of();
        }

        int safeMax = Math.max(0, maxMessages);
        int start = Math.max(0, memory.size() - safeMax);
        List<Map<String, String>> context = new ArrayList<>();
        for (int i = start; i < memory.size(); i++) {
            MemoryMessage m = memory.get(i);
            if (m == null || isBlank(m.role()) || isBlank(m.content())) {
                continue;
            }
            String role = m.role().trim().toLowerCase();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            context.add(Map.of("role", role, "content", m.content().trim()));
        }
        return context;
    }

    private void persist(Student student, List<MemoryMessage> memory) {
        try {
            student.setRishiMemoryJson(objectMapper.writeValueAsString(memory));
        } catch (Exception ex) {
            student.setRishiMemoryJson("[]");
        }
    }

    private void trim(List<MemoryMessage> memory) {
        int overflow = memory.size() - MAX_MEMORY_MESSAGES;
        if (overflow > 0) {
            memory.subList(0, overflow).clear();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeType(String value) {
        if (value == null || value.isBlank()) {
            return "text";
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
