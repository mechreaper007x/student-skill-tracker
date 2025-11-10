package com.skilltracker.student_skill_tracker.service;

public interface EmailService {
    void send(String toEmail, String subject, String body);
}
