package com.skilltracker.student_skill_tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanguageSkillDTO {
    private Long id;
    private String languageName;
    private Integer problemsSolved;
    private Integer rating;
    private String category; // Always "Technical" for programming languages
}
