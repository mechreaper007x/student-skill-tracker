package com.skilltracker.student_skill_tracker.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardResponseDTO {
    private StudentDTO student;
    private SkillDataDTO skillData;
}
