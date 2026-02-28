package com.skilltracker.student_skill_tracker.dto;

import java.util.ArrayList;
import java.util.List;

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
public class RishiAgentExecuteResponse {
    private String reply;
    private String provider;
    private String model;
    private String mode;
    private String threadId;
    @Builder.Default
    private List<String> actions = new ArrayList<>();
}

