package com.skilltracker.student_skill_tracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.model.MasteryEventLog;

import java.util.List;

@Repository
public interface MasteryEventLogRepository extends JpaRepository<MasteryEventLog, Long> {
    List<MasteryEventLog> findTop5ByStudentAndTopicSlugOrderByCreatedAtDesc(Student student, String topicSlug);
}
