package com.skilltracker.student_skill_tracker.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.skilltracker.student_skill_tracker.model.PasswordResetToken;
import com.skilltracker.student_skill_tracker.model.Student;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    long deleteByStudent_Id(Long studentId); // optional cleanup helper
    
    @Transactional
    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.student = ?1")
    void deleteByStudent(Student student);
}
