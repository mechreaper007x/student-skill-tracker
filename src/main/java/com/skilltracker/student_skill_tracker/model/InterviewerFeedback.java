package com.skilltracker.student_skill_tracker.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "interviewer_feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewerFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Student candidate;

    @Column(name = "interviewer_email", nullable = false, length = 320)
    private String interviewerEmail;

    @Column(name = "technical_depth_score")
    private Double technicalDepthScore;

    @Column(name = "problem_solving_score")
    private Double problemSolvingScore;

    @Column(name = "communication_score")
    private Double communicationScore;

    @Column(name = "consistency_score")
    private Double consistencyScore;

    @Column(name = "growth_score")
    private Double growthScore;

    @Column(name = "weighted_total_score")
    private Double weightedTotalScore;

    @Column(name = "recommendation", length = 32)
    private String recommendation;

    @Column(name = "recommendation_reason", length = 1500)
    private String recommendationReason;

    @Column(name = "notes", length = 3000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
