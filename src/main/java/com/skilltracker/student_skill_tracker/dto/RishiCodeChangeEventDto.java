package com.skilltracker.student_skill_tracker.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RishiCodeChangeEventDto {
    private String timestamp;
    private Integer editorVersion;
    private Integer rangeOffset;
    private Integer rangeLength;
    private Integer insertedChars;
    private Integer deletedChars;
    private Integer resultingCodeLength;
    private String activityState;
    private Boolean editorFocused;
    private Boolean windowFocused;
    private Boolean documentVisible;
}
