package com.skilltracker.student_skill_tracker.controller;

import java.util.Map;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.skilltracker.student_skill_tracker.service.DuelService;

@Controller
public class DuelStompController {

    private final DuelService duelService;
    private final SimpMessagingTemplate messagingTemplate;

    public DuelStompController(DuelService duelService, SimpMessagingTemplate messagingTemplate) {
        this.duelService = duelService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/duel/{sessionId}/typing")
    public void handleTyping(@DestinationVariable String sessionId, @Payload Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/duel/" + sessionId + "/typing", payload);
    }

    @MessageMapping("/duel/{sessionId}/executeStatus")
    public void handleExecuteStatus(@DestinationVariable String sessionId, @Payload Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/duel/" + sessionId + "/executeStatus", payload);
    }

    @MessageMapping("/duel/{sessionId}/submitAnswer")
    public void handleSubmitAnswer(@DestinationVariable String sessionId, @Payload Map<String, Object> payload) {
        String username = (String) payload.getOrDefault("username", "");
        String answer = (String) payload.getOrDefault("answer", "");
        duelService.submitRoundAnswer(sessionId, username, answer);
    }

    @MessageMapping("/duel/{sessionId}/advanceRound")
    public void handleAdvanceRound(@DestinationVariable String sessionId, @Payload Map<String, Object> payload) {
        duelService.advanceRound(sessionId);
    }
}
