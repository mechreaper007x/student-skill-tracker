package com.skilltracker.student_skill_tracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dojo_puzzles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DojoPuzzle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 5000)
    private String descriptionHtml;

    private String difficulty;

    @Column(length = 2000)
    private String starterCodeJava;

    @Column(length = 2000)
    private String starterCodePython;

    @Column(length = 2000)
    private String starterCodeCpp;

    @Column(length = 2000)
    private String starterCodeJavascript;

    // JSON representation of hidden test cases: [{"input": "...", "expectedOutput":
    // "..."}]
    @Column(length = 5000)
    private String hiddenTestCasesJson;

    // JSON array of all 5 rounds for multi-round duels
    @Column(length = 15000)
    private String roundsJson;

    @Column(nullable = false)
    private boolean isGeneratedByAi;

    @Column(nullable = false)
    @Builder.Default
    private int usageCount = 0;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private java.time.LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = java.time.LocalDateTime.now();
        }
    }
}
