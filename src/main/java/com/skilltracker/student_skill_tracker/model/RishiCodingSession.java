package com.skilltracker.student_skill_tracker.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@Table(name = "rishi_coding_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RishiCodingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "problem_slug", length = 255)
    private String problemSlug;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "total_duration_ms")
    @Builder.Default
    private Long totalDurationMs = 0L;

    @Column(name = "active_duration_ms")
    @Builder.Default
    private Long activeDurationMs = 0L;

    @Column(name = "typing_duration_ms")
    @Builder.Default
    private Long typingDurationMs = 0L;

    @Column(name = "cursor_idle_duration_ms")
    @Builder.Default
    private Long cursorIdleDurationMs = 0L;

    @Column(name = "editor_unfocused_duration_ms")
    @Builder.Default
    private Long editorUnfocusedDurationMs = 0L;

    @Column(name = "tab_hidden_duration_ms")
    @Builder.Default
    private Long tabHiddenDurationMs = 0L;

    @Column(name = "total_change_events")
    @Builder.Default
    private Integer totalChangeEvents = 0;

    @Column(name = "total_inserted_chars")
    @Builder.Default
    private Integer totalInsertedChars = 0;

    @Column(name = "total_deleted_chars")
    @Builder.Default
    private Integer totalDeletedChars = 0;

    @Column(name = "latest_code_length")
    @Builder.Default
    private Integer latestCodeLength = 0;

    @Column(name = "compile_attempts")
    @Builder.Default
    private Integer compileAttempts = 0;

    @Column(name = "successful_compiles")
    @Builder.Default
    private Integer successfulCompiles = 0;

    @Column(name = "failed_compiles")
    @Builder.Default
    private Integer failedCompiles = 0;

    @Column(name = "first_successful_compile_at")
    private LocalDateTime firstSuccessfulCompileAt;

    @Column(name = "first_success_duration_ms")
    private Long firstSuccessDurationMs;

    @Column(name = "session_end_reason", length = 64)
    private String sessionEndReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
