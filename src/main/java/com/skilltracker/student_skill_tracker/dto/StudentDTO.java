package com.skilltracker.student_skill_tracker.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentDTO {
    private Long id;
    private String name;
    private String email;
    private String leetcodeUsername;
    private Integer level;
    private Integer xp;
}
