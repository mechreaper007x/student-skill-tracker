package com.skilltracker.student_skill_tracker.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RishiOAuthCompleteRequest {
    private String code;
    private String state;
    private String redirectUri;
}

