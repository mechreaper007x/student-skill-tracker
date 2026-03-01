package com.skilltracker.student_skill_tracker.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rishi_practice_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RishiPracticeTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "source_external_id", nullable = false, length = 512)
    private String sourceExternalId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Lob
    @Column(name = "details")
    private String details;

    @Column(name = "topic", length = 160)
    private String topic;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 1;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "TODO";

    @Column(name = "suggested_minutes")
    @Builder.Default
    private Integer suggestedMinutes = 45;

    @Column(name = "planned_start_at")
    private LocalDateTime plannedStartAt;

    @Column(name = "planned_end_at")
    private LocalDateTime plannedEndAt;

    @Column(name = "calendar_event_id", length = 512)
    private String calendarEventId;

    @Column(name = "calendar_event_link", length = 1024)
    private String calendarEventLink;

    @Lob
    @Column(name = "metadata_json")
    private String metadataJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

