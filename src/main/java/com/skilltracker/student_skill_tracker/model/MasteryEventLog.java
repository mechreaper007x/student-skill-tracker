package com.skilltracker.student_skill_tracker.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mastery_event_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasteryEventLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    private String topicSlug;
    private String eventType; // "COMPILER_RUN", "DUEL_ROUND", "LEETCODE_SUBMIT"
    
    // --- Behavioral Telemetry ---
    private long timeTakenMs;
    private int errorCount;
    private boolean success;
    private int qualityScore; // 0-5 (The 'q' for SM-2)
    
    // --- Contextual Data ---
    private boolean highPressure; // true if in a Duel
    private String metadataJson; // Store specific errors or performance tags

    private LocalDateTime createdAt;
}
