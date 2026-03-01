package com.skilltracker.student_skill_tracker.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "topic_mastery")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicMastery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    private String topicSlug; // e.g., "binary-search", "dynamic-programming"

    // --- SM-2 Core Variables ---
    private int repetitions = 0;   // 'n'
    private double easinessFactor = 2.5; // 'EF'
    private int intervalDays = 0;  // 'I'
    
    // --- Forgetting Velocity Variables ---
    private double currentDecayRate = 0.0; // Calculated decay velocity
    private long bestTimeMs = 0; // Faster ever solve for this topic
    private int totalStruggleEvents = 0; // How many times they "struggled"

    private LocalDateTime lastReviewedAt;
    private LocalDateTime nextReviewDate;
}
