package com.skilltracker.student_skill_tracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.skilltracker.student_skill_tracker.model.DuelSession;

@Repository
public interface DuelSessionRepository extends JpaRepository<DuelSession, String> {
}
