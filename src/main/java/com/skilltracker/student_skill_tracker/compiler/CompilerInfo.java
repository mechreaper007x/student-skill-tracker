package com.skilltracker.student_skill_tracker.compiler;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CompilerInfo {
    private String languageName;
    private String version;
    private String command;
}