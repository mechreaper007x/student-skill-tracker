package com.skilltracker.student_skill_tracker.model;

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
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "language_skills")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LanguageSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "language_name", nullable = false)
    private String languageName;

    @Column(name = "problems_solved")
    private Integer problemsSolved;

    @Column(name = "rating")
    private Integer rating; // 1-5 scale

    /**
     * Calculate rating from problems solved
     * 0-20 problems → Rating 1
     * 21-50 problems → Rating 2
     * 51-100 problems → Rating 3
     * 101-200 problems → Rating 4
     * 201+ problems → Rating 5
     */
    public static int calculateRating(int problemsSolved) {
        if (problemsSolved <= 20)
            return 1;
        if (problemsSolved <= 50)
            return 2;
        if (problemsSolved <= 100)
            return 3;
        if (problemsSolved <= 200)
            return 4;
        return 5;
    }
}
