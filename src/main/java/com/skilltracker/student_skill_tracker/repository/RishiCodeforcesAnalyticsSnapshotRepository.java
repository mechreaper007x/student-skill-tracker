package com.skilltracker.student_skill_tracker.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skilltracker.student_skill_tracker.model.RishiCodeforcesAnalyticsSnapshot;
import com.skilltracker.student_skill_tracker.model.Student;

public interface RishiCodeforcesAnalyticsSnapshotRepository extends JpaRepository<RishiCodeforcesAnalyticsSnapshot, Long> {
    Optional<RishiCodeforcesAnalyticsSnapshot> findTopByStudentOrderByCreatedAtDesc(Student student);
}
