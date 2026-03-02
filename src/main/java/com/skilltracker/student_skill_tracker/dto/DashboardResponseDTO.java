package com.skilltracker.student_skill_tracker.dto;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class DashboardResponseDTO {
    private StudentDTO student;
    private SkillDataDTO skillData;
}
