package com.skilltracker.student_skill_tracker.dto;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class StudentDTO {
    private Long id;
    private String name;
    private String email;
    private String leetcodeUsername;
    private Integer level;
    private Integer xp;
    private String thinkingStyle;
    private Integer highestBloomLevel;
    private Integer duelWins;
    private String lastEmotionAfterFailure;
    private java.util.Map<String, Integer> emotionDistribution;
}
