package com.skilltracker.student_skill_tracker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.model.Student;

public interface SkillDataRepository extends JpaRepository<SkillData, Long> {
    List<SkillData> findByStudentId(Long studentId);
    Optional<SkillData> findByStudent(Student student);
    Optional<SkillData> findTopByStudentOrderByCreatedAtDesc(Student student);
    List<SkillData> findTop2ByStudentOrderByCreatedAtDesc(Student student);
}