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
@Table(name = "rishi_leetcode_analytics_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RishiLeetCodeAnalyticsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "leetcode_username", nullable = false, length = 120)
    private String leetcodeUsername;

    @Column(name = "window_days", nullable = false)
    private Integer windowDays;

    @Column(name = "total_solved", nullable = false)
    @Builder.Default
    private Integer totalSolved = 0;

    @Column(name = "easy_solved", nullable = false)
    @Builder.Default
    private Integer easySolved = 0;

    @Column(name = "medium_solved", nullable = false)
    @Builder.Default
    private Integer mediumSolved = 0;

    @Column(name = "hard_solved", nullable = false)
    @Builder.Default
    private Integer hardSolved = 0;

    @Column(name = "ranking", nullable = false)
    @Builder.Default
    private Integer ranking = 0;

    @Column(name = "reputation", nullable = false)
    @Builder.Default
    private Integer reputation = 0;

    @Column(name = "contest_rating")
    @Builder.Default
    private Double contestRating = 0.0;

    @Column(name = "contest_attended_count", nullable = false)
    @Builder.Default
    private Integer contestAttendedCount = 0;

    @Column(name = "solved_last_7d", nullable = false)
    @Builder.Default
    private Integer solvedLast7d = 0;

    @Column(name = "solved_prev_7d", nullable = false)
    @Builder.Default
    private Integer solvedPrev7d = 0;

    @Column(name = "solve_trend_pct")
    @Builder.Default
    private Double solveTrendPct = 0.0;

    @Column(name = "weak_topics", length = 1024)
    private String weakTopics;

    @Column(name = "strong_topics", length = 1024)
    private String strongTopics;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

