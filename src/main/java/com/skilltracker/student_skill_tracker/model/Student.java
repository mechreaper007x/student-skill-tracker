package com.skilltracker.student_skill_tracker.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "students")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    @ToString.Exclude
    private String password;

    @jakarta.persistence.Transient
    @ToString.Exclude
    private String confirmPassword;

    @Column(name = "leetcode_username", nullable = false, unique = true)
    private String leetcodeUsername;

    @Column(name = "github_username")
    private String githubUsername;

    @Column(name = "github_access_token")
    private String githubAccessToken;

    @Column(name = "ai_provider")
    private String aiProvider;

    @Column(name = "ai_model")
    private String aiModel;

    @Column(name = "ai_api_key", length = 2048)
    @ToString.Exclude
    private String aiApiKey;

    @Lob
    @Column(name = "rishi_memory_json")
    @ToString.Exclude
    private String rishiMemoryJson;

    @Lob
    @Column(name = "rishi_study_plan")
    @ToString.Exclude
    private String rishiStudyPlan;

    @Column(name = "rishi_study_topic")
    private String rishiStudyTopic;

    @Column(name = "rishi_study_days")
    private Integer rishiStudyDays;

    @Column(name = "rishi_study_generated_at")
    private LocalDateTime rishiStudyGeneratedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder.Default
    private String roles = "ROLE_USER";

    @Builder.Default
    private Integer level = 1;

    @Builder.Default
    private Integer xp = 0;

    public void levelUp() {
        this.level++;
        this.xp = 0;
    }
}
