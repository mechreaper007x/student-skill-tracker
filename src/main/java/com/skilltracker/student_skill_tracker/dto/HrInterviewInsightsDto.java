package com.skilltracker.student_skill_tracker.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrInterviewInsightsDto {
    private Long candidateId;
    private String briefing;
    private Map<String, Double> behavioralScores;
    private List<String> recommendedFocusAreas;
    private List<String> suggestedQuestions;
}
