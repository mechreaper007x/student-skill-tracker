package com.skilltracker.student_skill_tracker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skilltracker.student_skill_tracker.model.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByEmail(String email);

    Optional<Student> findByEmailIgnoreCase(String email);

    Optional<Student> findByLeetcodeUsername(String username);

    List<Student> findByNameContainingIgnoreCase(String name);
}
