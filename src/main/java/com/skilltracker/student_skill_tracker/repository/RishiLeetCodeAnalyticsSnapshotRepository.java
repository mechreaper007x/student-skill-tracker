package com.skilltracker.student_skill_tracker.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skilltracker.student_skill_tracker.model.RishiLeetCodeAnalyticsSnapshot;
import com.skilltracker.student_skill_tracker.model.Student;

public interface RishiLeetCodeAnalyticsSnapshotRepository extends JpaRepository<RishiLeetCodeAnalyticsSnapshot, Long> {
    Optional<RishiLeetCodeAnalyticsSnapshot> findTopByStudentOrderByCreatedAtDesc(Student student);
}

