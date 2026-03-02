package com.skilltracker.student_skill_tracker.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.skilltracker.student_skill_tracker.dto.RishiAgentExecuteRequest;
import com.skilltracker.student_skill_tracker.dto.RishiAgentExecuteResponse;
import com.skilltracker.student_skill_tracker.model.Student;

@Service
public class RishiAsyncAgentService {

    private static final Logger logger = LoggerFactory.getLogger(RishiAsyncAgentService.class);

    private final RishiAgentService rishiAgentService;
    private final Map<String, TaskStatus> taskStatuses = new ConcurrentHashMap<>();

    public RishiAsyncAgentService(RishiAgentService rishiAgentService) {
        this.rishiAgentService = rishiAgentService;
    }

    public String enqueueTask(Student student, RishiAgentExecuteRequest request) {
        String taskId = UUID.randomUUID().toString();
        taskStatuses.put(taskId, new TaskStatus("PENDING", null, null));

        processTaskAsync(taskId, student, request);
        return taskId;
    }

    public TaskStatus getTaskStatus(String taskId) {
        return taskStatuses.get(taskId);
    }

    @Async("rishiAgentExecutor")
    public void processTaskAsync(String taskId, Student student, RishiAgentExecuteRequest request) {
        try {
            logger.info("Starting AI processing for task {}", taskId);
            taskStatuses.put(taskId, new TaskStatus("PROCESSING", null, null));

            RishiAgentExecuteResponse response = rishiAgentService.execute(student, request);

            taskStatuses.put(taskId, new TaskStatus("COMPLETED", response, null));
            logger.info("Completed AI processing for task {}", taskId);
        } catch (Exception e) {
            logger.error("Failed AI processing for task {}", taskId, e);
            taskStatuses.put(taskId, new TaskStatus("FAILED", null, e.getMessage()));
        }
    }

    public static class TaskStatus {
        private final String status;
        private final RishiAgentExecuteResponse response;
        private final String errorMessage;

        public TaskStatus(String status, RishiAgentExecuteResponse response, String errorMessage) {
            this.status = status;
            this.response = response;
            this.errorMessage = errorMessage;
        }

        public String getStatus() {
            return status;
        }

        public RishiAgentExecuteResponse getResponse() {
            return response;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
