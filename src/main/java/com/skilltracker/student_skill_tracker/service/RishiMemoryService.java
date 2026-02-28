package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skilltracker.student_skill_tracker.model.Student;

@Service
public class RishiMemoryService {

    private static final Logger log = LoggerFactory.getLogger(RishiMemoryService.class);
    private static final int MAX_MEMORY_MESSAGES_PER_THREAD = 30;
    private final ObjectMapper objectMapper;

    public RishiMemoryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record MemoryMessage(String role, String content, String type, String timestamp) {}
    
    public static class ChatThread {
        public String id;
        public String title;
        public String preview;
        public String updatedAt;
        public List<MemoryMessage> messages = new ArrayList<>();
        
        public ChatThread() {}
        public ChatThread(String id, String title, String preview, String updatedAt) {
            this.id = id;
            this.title = title;
            this.preview = preview;
            this.updatedAt = updatedAt;
        }
    }

    public static class MemoryStore {
        public List<ChatThread> threads = new ArrayList<>();
    }

    private MemoryStore getStore(Student student) {
        if (student == null || isBlank(student.getRishiMemoryJson())) {
            return new MemoryStore();
        }

        String json = student.getRishiMemoryJson().trim();
        try {
            // First try parsing as the new structure (MemoryStore with threads)
            if (json.startsWith("{")) {
                MemoryStore store = objectMapper.readValue(json, MemoryStore.class);
                return store == null ? new MemoryStore() : store;
            } 
            // Fallback for legacy data (just a list of messages)
            else if (json.startsWith("[")) {
                List<MemoryMessage> legacyMessages = objectMapper.readValue(json, new TypeReference<List<MemoryMessage>>() {});
                MemoryStore store = new MemoryStore();
                if (legacyMessages != null && !legacyMessages.isEmpty()) {
                    ChatThread legacyThread = new ChatThread(UUID.randomUUID().toString(), "Legacy Chat", extractPreview(legacyMessages.get(0).content()), LocalDateTime.now().toString());
                    legacyThread.messages.addAll(legacyMessages);
                    store.threads.add(legacyThread);
                }
                return store;
            }
        } catch (Exception ex) {
            log.warn("Failed to parse Rishi memory, resetting. json: {}", json, ex);
        }
        return new MemoryStore();
    }

    public List<ChatThread> getAllThreads(Student student) {
        MemoryStore store = getStore(student);
        // Sort by updatedAt descending
        store.threads.sort((a, b) -> b.updatedAt.compareTo(a.updatedAt));
        return store.threads;
    }

    public ChatThread getThread(Student student, String threadId) {
        MemoryStore store = getStore(student);
        return store.threads.stream()
                .filter(t -> t.id.equals(threadId))
                .findFirst()
                .orElse(null);
    }

    public List<MemoryMessage> getMemory(Student student) {
        // Legacy accessor, returns messages from the most recent thread or empty
        List<ChatThread> threads = getAllThreads(student);
        if (threads.isEmpty()) return new ArrayList<>();
        return threads.get(0).messages;
    }

    public String appendExchange(Student student, String threadId, String userMessage, String userType, String assistantMessage, String assistantType) {
        MemoryStore store = getStore(student);
        String now = LocalDateTime.now().toString();
        
        ChatThread targetThread = null;
        if (threadId != null && !threadId.isBlank()) {
            targetThread = store.threads.stream()
                .filter(t -> t.id.equals(threadId))
                .findFirst()
                .orElse(null);
        }

        if (targetThread == null) {
            // Create a new thread
            String newId = UUID.randomUUID().toString();
            String title = generateTitle(userMessage);
            String preview = extractPreview(userMessage);
            targetThread = new ChatThread(newId, title, preview, now);
            store.threads.add(targetThread);
        }

        targetThread.updatedAt = now;
        targetThread.preview = extractPreview(userMessage);
        
        targetThread.messages.add(new MemoryMessage("user", safe(userMessage), safeType(userType), now));
        targetThread.messages.add(new MemoryMessage("assistant", safe(assistantMessage), safeType(assistantType), now));
        
        trim(targetThread.messages);
        persist(student, store);
        
        return targetThread.id;
    }

    public void clearMemory(Student student) {
        // Clears EVERYTHING (if needed)
        student.setRishiMemoryJson("{}");
    }
    
    public void deleteThread(Student student, String threadId) {
        MemoryStore store = getStore(student);
        store.threads.removeIf(t -> t.id.equals(threadId));
        persist(student, store);
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

    private void persist(Student student, MemoryStore store) {
        try {
            student.setRishiMemoryJson(objectMapper.writeValueAsString(store));
        } catch (Exception ex) {
            log.error("Failed to serialize memory store", ex);
            student.setRishiMemoryJson("{}");
        }
    }

    private void trim(List<MemoryMessage> memory) {
        int overflow = memory.size() - MAX_MEMORY_MESSAGES_PER_THREAD;
        if (overflow > 0) {
            memory.subList(0, overflow).clear();
        }
    }

    private String generateTitle(String firstMessage) {
        if (firstMessage == null) return "New Conversation";
        String clean = firstMessage.trim();
        if (clean.length() <= 25) return clean;
        return clean.substring(0, 25) + "...";
    }

    private String extractPreview(String message) {
        if (message == null) return "";
        String clean = message.replaceAll("(?s)```.*?```", "[Code]");
        clean = clean.replaceAll("<[^>]*>", "");
        if (clean.length() <= 40) return clean;
        return clean.substring(0, 40) + "...";
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
