package com.skilltracker.student_skill_tracker.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RishiCodeChangeBatchRequest {
    private List<RishiCodeChangeEventDto> events = new ArrayList<>();
}

