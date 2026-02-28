package com.skilltracker.student_skill_tracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skilltracker.student_skill_tracker.model.RishiCodeChangeEvent;

public interface RishiCodeChangeEventRepository extends JpaRepository<RishiCodeChangeEvent, Long> {
}

