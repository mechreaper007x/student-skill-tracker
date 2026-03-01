package com.skilltracker.student_skill_tracker.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skilltracker.student_skill_tracker.model.RishiGithubAnalyticsSnapshot;
import com.skilltracker.student_skill_tracker.model.Student;

public interface RishiGithubAnalyticsSnapshotRepository extends JpaRepository<RishiGithubAnalyticsSnapshot, Long> {
    Optional<RishiGithubAnalyticsSnapshot> findTopByStudentOrderByCreatedAtDesc(Student student);
}

