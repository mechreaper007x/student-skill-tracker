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
@Table(name = "rishi_codeforces_analytics_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RishiCodeforcesAnalyticsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "codeforces_handle", nullable = false, length = 120)
    private String codeforcesHandle;

    @Column(name = "window_days", nullable = false)
    private Integer windowDays;

    @Column(name = "current_rating", nullable = false)
    @Builder.Default
    private Integer currentRating = 0;

    @Column(name = "max_rating", nullable = false)
    @Builder.Default
    private Integer maxRating = 0;

    @Column(name = "rank", length = 120)
    private String rank;

    @Column(name = "max_rank", length = 120)
    private String maxRank;

    @Column(name = "contest_count", nullable = false)
    @Builder.Default
    private Integer contestCount = 0;

    @Column(name = "solved_total", nullable = false)
    @Builder.Default
    private Integer solvedTotal = 0;

    @Column(name = "solved_current_window", nullable = false)
    @Builder.Default
    private Integer solvedCurrentWindow = 0;

    @Column(name = "solved_previous_window", nullable = false)
    @Builder.Default
    private Integer solvedPreviousWindow = 0;

    @Column(name = "solve_trend_pct")
    @Builder.Default
    private Double solveTrendPct = 0.0;

    @Column(name = "strong_tags", length = 2048)
    private String strongTags;

    @Column(name = "weak_tags", length = 2048)
    private String weakTags;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
