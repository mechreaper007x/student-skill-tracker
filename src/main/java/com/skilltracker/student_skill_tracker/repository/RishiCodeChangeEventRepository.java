package com.skilltracker.student_skill_tracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skilltracker.student_skill_tracker.model.RishiCodeChangeEvent;
import com.skilltracker.student_skill_tracker.model.RishiCodingSession;

public interface RishiCodeChangeEventRepository extends JpaRepository<RishiCodeChangeEvent, Long> {

    List<RishiCodeChangeEvent> findBySessionOrderByOccurredAtAsc(RishiCodingSession session);
}
