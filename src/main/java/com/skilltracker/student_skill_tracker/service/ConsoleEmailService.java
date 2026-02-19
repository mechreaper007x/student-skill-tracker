package com.skilltracker.student_skill_tracker.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(name = "smtpEmailService")
public class ConsoleEmailService implements EmailService {
    @Override
    public void send(String toEmail, String subject, String body) {
        System.out.println("\n=== DEV EMAIL ===");
        System.out.println("TO: " + toEmail);
        System.out.println("SUBJECT: " + subject);
        System.out.println("BODY:\n" + body);
        System.out.println("=================\n");
    }
}
