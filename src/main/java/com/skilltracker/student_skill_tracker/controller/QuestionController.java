package com.skilltracker.student_skill_tracker.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.service.QuestionService;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /**
     * The Crucible — foundational common questions.
     */
    @GetMapping("/common")
    public ResponseEntity<List<Map<String, Object>>> getCommonQuestions() {
        return ResponseEntity.ok(questionService.getCommonQuestions());
    }

    /**
     * The Apex — elite top-tier questions.
     */
    @GetMapping("/top-tier")
    public ResponseEntity<List<Map<String, Object>>> getTopTierQuestions() {
        return ResponseEntity.ok(questionService.getTopTierQuestions());
    }

    /**
     * The Vanguard — currently trending questions.
     */
    @GetMapping("/trending")
    public ResponseEntity<List<Map<String, Object>>> getTrendingQuestions() {
        return ResponseEntity.ok(questionService.getTrendingQuestions());
    }

    /**
     * All questions, deduplicated across categories.
     */
    @GetMapping("/all")
    public ResponseEntity<List<Map<String, Object>>> getAllQuestions() {
        return ResponseEntity.ok(questionService.getAllQuestions());
    }

    /**
     * Filter by difficulty (Easy, Medium, Hard).
     */
    @GetMapping("/by-difficulty")
    public ResponseEntity<List<Map<String, Object>>> getByDifficulty(@RequestParam String difficulty) {
        return ResponseEntity.ok(questionService.getByDifficulty(difficulty));
    }

    /**
     * Filter by tag (e.g., "array", "dynamic-programming").
     */
    @GetMapping("/by-tag")
    public ResponseEntity<List<Map<String, Object>>> getByTag(@RequestParam String tag) {
        return ResponseEntity.ok(questionService.getByTag(tag));
    }

    /**
     * Daily Combat — a deterministic daily challenge.
     */
    @GetMapping("/daily-challenge")
    public ResponseEntity<Map<String, Object>> getDailyChallenge() {
        return ResponseEntity.ok(questionService.getDailyChallenge());
    }
}
