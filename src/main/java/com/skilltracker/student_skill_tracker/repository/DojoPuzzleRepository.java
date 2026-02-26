package com.skilltracker.student_skill_tracker.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.skilltracker.student_skill_tracker.model.DojoPuzzle;

@Repository
public interface DojoPuzzleRepository extends JpaRepository<DojoPuzzle, Long> {

    // Custom query to fetch a random puzzle (useful for matchmaking when
    // pre-cached)
    @Query(value = "SELECT * FROM dojo_puzzles WHERE rounds_json IS NOT NULL AND TRIM(rounds_json) <> '' ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<DojoPuzzle> findRandomPuzzle();

    long countByUsageCount(int usageCount);

    @Query(value = "SELECT * FROM dojo_puzzles WHERE usage_count = :usageCount AND rounds_json IS NOT NULL AND TRIM(rounds_json) <> '' ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Optional<DojoPuzzle> findFirstUsableByUsageCountOrderByCreatedAtAsc(@Param("usageCount") int usageCount);
}
