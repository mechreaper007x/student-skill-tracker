package com.skilltracker.student_skill_tracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skilltracker.student_skill_tracker.model.SkillData;

public interface SkillDataRepository extends JpaRepository<SkillData, Long> {
    List<SkillData> findByStudentId(Long studentId);
}
