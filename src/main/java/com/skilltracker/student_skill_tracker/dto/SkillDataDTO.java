package com.skilltracker.student_skill_tracker.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SkillDataDTO {
    private Long id;
    private Double problemSolvingScore;
    private Double algorithmsScore;
    private Double dataStructuresScore;
    private Integer totalProblemsSolved;
    private Integer easyProblems;
    private Integer mediumProblems;
    private Integer hardProblems;
    private Integer ranking;
    private String aiAdvice;
}
