package com.skilltracker.student_skill_tracker.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewerFeedbackResponseDto {
    private Long id;
    private Long candidateId;
    private String interviewerEmail;
    private Double weightedTotalScore;
    private String recommendation;
    private String recommendationReason;
    private String notes;
    private LocalDateTime createdAt;
}
