package com.skilltracker.student_skill_tracker;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.skilltracker.student_skill_tracker.service.LeetCodeService;

@SpringBootApplication
public class StudentSkillTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentSkillTrackerApplication.class, args);
    }

    @Bean
    CommandLineRunner testApi(LeetCodeService leetCodeService) {
        return args -> {
            var data = leetCodeService.fetchStats("username");
            System.out.println(data);
        };
    }
}
