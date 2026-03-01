package com.skilltracker.student_skill_tracker.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skilltracker.student_skill_tracker.model.RishiTogglFocusSnapshot;
import com.skilltracker.student_skill_tracker.model.Student;

public interface RishiTogglFocusSnapshotRepository extends JpaRepository<RishiTogglFocusSnapshot, Long> {
    Optional<RishiTogglFocusSnapshot> findTopByStudentOrderByCreatedAtDesc(Student student);
}
