package com.skilltracker.student_skill_tracker.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrCandidateCardDto {
    private Long candidateId;
    private String name;
    private String email;
    private String leetcodeUsername;
    private Double overallReadinessScore;
    private Double technicalScore;
    private Double communicationScore;
    private Double consistencyScore;
    private Double confidenceScore;
    private String trend;
    private String recommendationBand;
    private LocalDateTime lastActiveAt;
    private List<String> riskFlags;
    private List<String> positiveSignals;
}
