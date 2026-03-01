package com.skilltracker.student_skill_tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RishiTaskDto {
    private Long id;
    private String sourceType;
    private String title;
    private String details;
    private String topic;
    private Integer priority;
    private String status;
    private Integer suggestedMinutes;
    private String plannedStartAt;
    private String plannedEndAt;
    private String calendarEventLink;
    private String updatedAt;
}

