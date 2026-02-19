package com.skilltracker.student_skill_tracker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.skilltracker.student_skill_tracker.model.LanguageSkill;
import com.skilltracker.student_skill_tracker.model.Student;

@Repository
public interface LanguageSkillRepository extends JpaRepository<LanguageSkill, Long> {

    List<LanguageSkill> findByStudent(Student student);

    Optional<LanguageSkill> findByStudentAndLanguageName(Student student, String languageName);

    @Modifying
    @Transactional
    void deleteByStudent(Student student);
}
