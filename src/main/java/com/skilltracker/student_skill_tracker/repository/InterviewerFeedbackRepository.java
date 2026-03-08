package com.skilltracker.student_skill_tracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skilltracker.student_skill_tracker.model.InterviewerFeedback;
import com.skilltracker.student_skill_tracker.model.Student;

public interface InterviewerFeedbackRepository extends JpaRepository<InterviewerFeedback, Long> {

    List<InterviewerFeedback> findByCandidateOrderByCreatedAtDesc(Student candidate);
}
