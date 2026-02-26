package com.skilltracker.student_skill_tracker.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;

@Service
public class CognitiveMetricService {

    private static final Logger logger = LoggerFactory.getLogger(CognitiveMetricService.class);
    private final StudentRepository studentRepository;

    public CognitiveMetricService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Transactional
    public void recordCompilation(Student student, boolean success) {
        student.setTotalCompilations(student.getTotalCompilations() + 1);
        if (success) {
            student.setSuccessfulCompilations(student.getSuccessfulCompilations() + 1);
        }
        studentRepository.save(student);
        logger.debug("Recorded compilation for {}: success={}", student.getEmail(), success);
    }

    @Transactional
    public void recordSubmission(Student student, String problemSlug, boolean accepted) {
        student.setTotalSubmissions(student.getTotalSubmissions() + 1);
        
        // Track "First Attempt Success" (System 2 Synthesis)
        if (accepted) {
            student.setAcceptedSubmissions(student.getAcceptedSubmissions() + 1);
        } else {
            student.setLastFailureTimestamp(LocalDateTime.now());
        }
        
        studentRepository.save(student);
        logger.debug("Recorded submission for {}: problem={}, accepted={}", student.getEmail(), problemSlug, accepted);
    }

    @Transactional
    public void recordQuestionSelection(Student student, String slug) {
        student.setLastSelectedQuestionSlug(slug);
        student.setQuestionSelectionTimestamp(LocalDateTime.now());
        studentRepository.save(student);
    }

    @Transactional
    public void trackPlanningTime(Student student, String slug) {
        if (slug == null || !slug.equals(student.getLastSelectedQuestionSlug()) || student.getQuestionSelectionTimestamp() == null) {
            return;
        }

        long planningTime = Duration.between(student.getQuestionSelectionTimestamp(), LocalDateTime.now()).toMillis();
        
        // Rolling average for planning time
        long currentAvg = student.getAvgPlanningTimeMs();
        int totalEvents = student.getTotalCompilations() + student.getTotalSubmissions();
        long newAvg = (currentAvg * totalEvents + planningTime) / (totalEvents + 1);
        
        student.setAvgPlanningTimeMs(newAvg);
        // Reset timestamp so we only track the FIRST meaningful action as planning end
        student.setQuestionSelectionTimestamp(null); 
        studentRepository.save(student);
        
        logger.info("Tracked planning time for {}: {}ms", student.getEmail(), planningTime);
    }

    @Transactional
    public void trackRecoveryVelocity(Student student) {
        if (student.getLastFailureTimestamp() == null) {
            return;
        }

        long recoveryTime = Duration.between(student.getLastFailureTimestamp(), LocalDateTime.now()).toMillis();
        
        // Rolling average for recovery velocity (Affective Regulation)
        long currentAvg = student.getAvgRecoveryVelocityMs();
        int totalSubmissions = student.getTotalSubmissions();
        long newAvg = (currentAvg * totalSubmissions + recoveryTime) / (totalSubmissions + 1);
        
        student.setAvgRecoveryVelocityMs(newAvg);
        student.setLastFailureTimestamp(null); // Reset after recovery action
        studentRepository.save(student);
        
        logger.info("Tracked recovery velocity for {}: {}ms", student.getEmail(), recoveryTime);
    }
}
