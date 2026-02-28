package com.skilltracker.student_skill_tracker.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RishiScheduleRequest {
    private Integer count;
    private Integer blockMinutes;
    private String startAt;
    private String timeZone;
}

