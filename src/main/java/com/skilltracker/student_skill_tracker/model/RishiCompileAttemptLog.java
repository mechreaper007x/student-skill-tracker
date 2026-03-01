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
@Table(name = "rishi_compile_attempt_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RishiCompileAttemptLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private RishiCodingSession session;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    @Column(name = "attempt_source", length = 64)
    private String attemptSource;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "problem_slug", length = 255)
    private String problemSlug;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "accuracy_pct")
    private Double accuracyPct;

    @Column(name = "mistake_category", length = 64)
    private String mistakeCategory;

    @Column(name = "failure_bucket", length = 64)
    private String failureBucket;

    @Column(name = "analysis_summary", length = 800)
    private String analysisSummary;

    @Column(name = "next_step_1", length = 400)
    private String nextStep1;

    @Column(name = "next_step_2", length = 400)
    private String nextStep2;

    @Column(name = "next_step_3", length = 400)
    private String nextStep3;

    @Column(name = "submission_status", length = 120)
    private String submissionStatus;

    @Column(name = "judge_message", length = 500)
    private String judgeMessage;

    @Column(name = "error_snippet", length = 2000)
    private String errorSnippet;

    @Column(name = "output_snippet", length = 2000)
    private String outputSnippet;

    @Column(name = "failed_test_input", length = 1000)
    private String failedTestInput;

    @Column(name = "expected_output_snippet", length = 1000)
    private String expectedOutputSnippet;

    @Column(name = "actual_output_snippet", length = 1000)
    private String actualOutputSnippet;

    @Column(name = "stack_trace_snippet", length = 2000)
    private String stackTraceSnippet;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
