package com.skilltracker.student_skill_tracker.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.skilltracker.student_skill_tracker.model.RishiPracticeTask;
import com.skilltracker.student_skill_tracker.model.Student;

public interface RishiPracticeTaskRepository extends JpaRepository<RishiPracticeTask, Long> {

    Optional<RishiPracticeTask> findByIdAndStudent(Long id, Student student);

    Optional<RishiPracticeTask> findByStudentAndSourceTypeAndSourceExternalId(
            Student student, String sourceType, String sourceExternalId);

    List<RishiPracticeTask> findByStudentOrderByUpdatedAtDesc(Student student);

    List<RishiPracticeTask> findByStudentAndStatusInOrderByPriorityDescUpdatedAtAsc(
            Student student, List<String> statuses, Pageable pageable);

    List<RishiPracticeTask> findByStudentAndPlannedStartAtBetween(Student student, LocalDateTime start, LocalDateTime end);
}
