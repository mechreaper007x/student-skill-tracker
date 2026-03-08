package com.skilltracker.student_skill_tracker.dto;

import java.time.LocalDateTime;
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
public class HrCandidateSummaryDto {
    private Long candidateId;
    private String name;
    private String email;
    private String leetcodeUsername;
    private Double overallReadinessScore;
    private Double technicalScore;
    private Double processScore;
    private Double communicationScore;
    private Double consistencyScore;
    private Double growthScore;
    private Double confidenceScore;
    private String trend;
    private String recommendationBand;
    private String aiBriefing;
    private LocalDateTime lastActiveAt;
    private Map<String, Double> radar;
    private Map<String, Double> heatmap;
    private Map<String, Double> timeline;
    private List<String> riskFlags;
    private List<String> positiveSignals;
    private List<Map<String, Object>> recentFeedback;
}
