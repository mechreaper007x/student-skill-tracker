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
public class RishiScheduleItemDto {
    private Long taskId;
    private String taskTitle;
    private String startAt;
    private String endAt;
    private String eventId;
    private String eventLink;
}

