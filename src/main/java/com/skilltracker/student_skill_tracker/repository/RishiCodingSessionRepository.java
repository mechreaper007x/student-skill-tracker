package com.skilltracker.student_skill_tracker.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skilltracker.student_skill_tracker.model.RishiCodingSession;
import com.skilltracker.student_skill_tracker.model.Student;

public interface RishiCodingSessionRepository extends JpaRepository<RishiCodingSession, Long> {

    Optional<RishiCodingSession> findByIdAndStudent(Long id, Student student);

    List<RishiCodingSession> findByStudentAndStartedAtBetween(Student student, LocalDateTime start, LocalDateTime end);

    List<RishiCodingSession> findByStudentAndStartedAtAfter(Student student, LocalDateTime start);

    Optional<RishiCodingSession> findTopByStudentOrderByLastActivityAtDesc(Student student);
}
