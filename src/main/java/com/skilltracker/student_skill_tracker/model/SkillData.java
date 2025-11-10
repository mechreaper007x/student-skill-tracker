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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "skill_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many SkillData rows can belong to one Student
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "problem_solving_score")
    private Double problemSolvingScore;

    @Column(name = "algorithms_score")
    private Double algorithmsScore;

    @Column(name = "data_structures_score")
    private Double dataStructuresScore;

    // languageProficiency removed due to frontend integration issues

    @Column(name = "total_problems_solved")
    private Integer totalProblemsSolved;

    @Column(name = "easy_problems")
    private Integer easyProblems;

    @Column(name = "medium_problems")
    private Integer mediumProblems;

    @Column(name = "hard_problems")
    private Integer hardProblems;

    @Column(name = "ranking")
    private Integer ranking;

    @Lob
    @Column(name = "ai_advice")
    private String aiAdvice;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
