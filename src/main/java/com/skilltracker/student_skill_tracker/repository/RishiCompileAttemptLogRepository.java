package com.skilltracker.student_skill_tracker.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skilltracker.student_skill_tracker.model.RishiCodingSession;
import com.skilltracker.student_skill_tracker.model.RishiCompileAttemptLog;
import com.skilltracker.student_skill_tracker.model.Student;

public interface RishiCompileAttemptLogRepository extends JpaRepository<RishiCompileAttemptLog, Long> {

    Optional<RishiCompileAttemptLog> findTopBySessionOrderByAttemptedAtDesc(RishiCodingSession session);

    List<RishiCompileAttemptLog> findBySessionStudentAndAttemptedAtBetweenOrderByAttemptedAtDesc(
            Student student,
            LocalDateTime start,
            LocalDateTime end);

    List<RishiCompileAttemptLog> findTop5BySessionStudentOrderByAttemptedAtDesc(Student student);

    List<RishiCompileAttemptLog> findBySessionStudentAndAttemptedAtAfterAndSuccessFalse(
            Student student, LocalDateTime since);

    Optional<RishiCompileAttemptLog> findTopBySessionStudentOrderByAttemptedAtDesc(Student student);

    List<RishiCompileAttemptLog> findBySessionStudentAndAttemptedAtAfterOrderByAttemptedAtDesc(
            Student student, LocalDateTime since);

    List<RishiCompileAttemptLog> findTop100BySessionStudentOrderByAttemptedAtDesc(Student student);
}
