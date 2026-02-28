package com.skilltracker.student_skill_tracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "duel_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DuelSession {

    @Id
    @Column(length = 36)
    private String id; // UUID

    private String player1Username;
    private String player2Username;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "puzzle_id", nullable = true)
    private DojoPuzzle puzzle;

    private String status; // WAITING, LIVE, FINISHED

    @Builder.Default
    @Column(name = "current_round")
    private int currentRound = 1;

    @Builder.Default
    @Column(name = "player1score")
    private int player1Score = 0;

    @Builder.Default
    @Column(name = "player2score")
    private int player2Score = 0;

    @Builder.Default
    @Column(name = "player1_highest_bloom_level")
    private int player1HighestBloomLevel = 1;

    @Builder.Default
    @Column(name = "player2_highest_bloom_level")
    private int player2HighestBloomLevel = 1;

    @Column(name = "start_time")
    private java.time.LocalDateTime startTime;

    private String winnerUsername;
}
