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
@Table(name = "rishi_github_analytics_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RishiGithubAnalyticsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "github_username", nullable = false, length = 120)
    private String githubUsername;

    @Column(name = "window_days", nullable = false)
    private Integer windowDays;

    @Column(name = "commit_count", nullable = false)
    @Builder.Default
    private Integer commitCount = 0;

    @Column(name = "pull_request_count", nullable = false)
    @Builder.Default
    private Integer pullRequestCount = 0;

    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    @Column(name = "issue_count", nullable = false)
    @Builder.Default
    private Integer issueCount = 0;

    @Column(name = "active_repo_count", nullable = false)
    @Builder.Default
    private Integer activeRepoCount = 0;

    @Column(name = "total_stars", nullable = false)
    @Builder.Default
    private Integer totalStars = 0;

    @Column(name = "top_languages", length = 1024)
    private String topLanguages;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

