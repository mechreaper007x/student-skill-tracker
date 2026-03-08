package com.skilltracker.student_skill_tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewerFeedbackRequestDto {
    private Double technicalDepthScore;
    private Double problemSolvingScore;
    private Double communicationScore;
    private Double consistencyScore;
    private Double growthScore;
    private String recommendation;
    private String recommendationReason;
    private String notes;
}
