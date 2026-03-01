package com.skilltracker.student_skill_tracker.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RishiAgentExecuteRequest {
    private String message;
    private String model;
    private String threadId;
    private Boolean autoSchedule;
}

