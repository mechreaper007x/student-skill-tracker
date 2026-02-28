package com.skilltracker.student_skill_tracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.model.TopicMastery;

import java.util.Optional;
import java.util.List;

@Repository
public interface TopicMasteryRepository extends JpaRepository<TopicMastery, Long> {
    Optional<TopicMastery> findByStudentAndTopicSlug(Student student, String topicSlug);
    List<TopicMastery> findByStudent(Student student);
}
