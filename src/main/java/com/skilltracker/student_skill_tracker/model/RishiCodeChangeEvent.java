package com.skilltracker.student_skill_tracker.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "rishi_code_change_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RishiCodeChangeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private RishiCodingSession session;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "editor_version")
    private Integer editorVersion;

    @Column(name = "range_offset")
    private Integer rangeOffset;

    @Column(name = "range_length")
    private Integer rangeLength;

    @Column(name = "inserted_chars")
    private Integer insertedChars;

    @Column(name = "deleted_chars")
    private Integer deletedChars;

    @Column(name = "resulting_code_length")
    private Integer resultingCodeLength;

    @Lob
    @Column(name = "meta_json")
    private String metaJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

