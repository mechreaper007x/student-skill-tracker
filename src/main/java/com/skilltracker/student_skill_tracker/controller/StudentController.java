package com.skilltracker.student_skill_tracker.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.CommonQuestionsService;
import com.skilltracker.student_skill_tracker.service.SkillService;


@RestController
@RequestMapping("/api/students")
public class StudentController {

    private static final Logger logger = LoggerFactory.getLogger(StudentController.class);

    private final StudentRepository studentRepository;
    private final SkillDataRepository skillDataRepository;
    private final SkillService skillService;
    private final CommonQuestionsService commonQuestionsService;
    private final PasswordEncoder passwordEncoder;

    public StudentController(StudentRepository studentRepository, SkillDataRepository skillDataRepository, SkillService skillService, CommonQuestionsService commonQuestionsService, PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.skillDataRepository = skillDataRepository;
        this.skillService = skillService;
        this.commonQuestionsService = commonQuestionsService;
        this.passwordEncoder = passwordEncoder;
    }


    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Student student) {
        if (!student.getPassword().equals(student.getConfirmPassword())) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("error", "Passwords do not match!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }

        if (studentRepository.findByEmail(student.getEmail()).isPresent()) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("error", "Email already exists!");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(resp);
        }

        if (studentRepository.findByLeetcodeUsername(student.getLeetcodeUsername()).isPresent()) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("error", "LeetCode username already registered!");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(resp);
        }

        student.setPassword(passwordEncoder.encode(student.getPassword()));
        student.setRoles("ROLE_USER"); // default role so AuthorityUtils won’t choke on null
        Student savedStudent = studentRepository.save(student);
        skillService.updateSkillData(savedStudent); // This can run in the background

        return ResponseEntity.status(HttpStatus.CREATED).body(savedStudent);
    }

    private boolean isOwner(Long studentId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<Student> studentOpt = studentRepository.findByEmail(email);
        return studentOpt.isPresent() && studentOpt.get().getId().equals(studentId);
    }

    private ResponseEntity<?> buildDashboardResponse(Student student) {
        Optional<SkillData> skillDataOpt = skillDataRepository.findByStudent(student);

        try {
            SkillData skillData = skillDataOpt.orElseGet(() -> skillService.updateSkillData(student));

            // Debug logs to help trace why frontend may receive empty data
            logger.debug("student={}", student);
            logger.debug("skillData={}", skillData);

            // Build simple DTO maps to avoid Jackson/Hibernate lazy-loading or proxy
            // Use mutable HashMap to allow null values (Map.of throws NPE on nulls)
            Map<String, Object> studentMap = new HashMap<>();
            studentMap.put("id", student.getId());
            studentMap.put("name", student.getName());
            studentMap.put("email", student.getEmail());
            studentMap.put("leetcodeUsername", student.getLeetcodeUsername());

            Map<String, Object> skillMap = new HashMap<>();
            skillMap.put("id", skillData.getId());
            skillMap.put("problemSolvingScore", skillData.getProblemSolvingScore());
            skillMap.put("algorithmsScore", skillData.getAlgorithmsScore());
            skillMap.put("dataStructuresScore", skillData.getDataStructuresScore());
            skillMap.put("totalProblemsSolved", skillData.getTotalProblemsSolved());
            skillMap.put("easyProblems", skillData.getEasyProblems());
            skillMap.put("mediumProblems", skillData.getMediumProblems());
            skillMap.put("hardProblems", skillData.getHardProblems());
            skillMap.put("ranking", skillData.getRanking());
            skillMap.put("aiAdvice", skillData.getAiAdvice());

            Map<String, Object> resp = new HashMap<>();
            resp.put("student", studentMap);
            resp.put("skillData", skillMap);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("Failed to build dashboard data for student id={}", student.getId(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Failed to build dashboard data");
            err.put("details", e.getMessage() == null ? "" : e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @GetMapping("/dashboard/{id}")
    public ResponseEntity<?> showDashboard(@PathVariable Long id) {
        if (!isOwner(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You are not authorized to view this dashboard."));
        }
        System.out.println("Received dashboard request for id: " + id);
        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("error", "Student not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }

        return buildDashboardResponse(studentOpt.get());
    }

    // New endpoint: return dashboard for currently authenticated user
    @GetMapping("/me/dashboard")
    public ResponseEntity<?> showMyDashboard() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("error", "Not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
        }

        Optional<Student> studentOpt = studentRepository.findByEmail(email);
        if (studentOpt.isEmpty()) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("error", "Student not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }

        return buildDashboardResponse(studentOpt.get());
    }

    @GetMapping("/{id}/common-questions")
    public ResponseEntity<?> getCommonQuestions(@PathVariable Long id) {
        if (!isOwner(id)) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("error", "You are not authorized to view these questions.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
        }

        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("error", "Student not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }

        List<Map<String, Object>> questions = commonQuestionsService.getCommonQuestionsForStudent(id);

        Optional<SkillData> skillDataOpt = skillDataRepository.findByStudent(studentOpt.get());
        SkillData skillData = skillDataOpt.orElseGet(() -> skillService.updateSkillData(studentOpt.get()));

        List<Map<String, Object>> personalized = commonQuestionsService.personalizeQuestions(questions, skillData, getTopPriority(skillData));
        return ResponseEntity.ok(personalized);
    }

    private String getTopPriority(SkillData sd) {
        if (sd == null) return "Problem Solving"; // Default if no skill data

        double ps = sd.getProblemSolvingScore() != null ? sd.getProblemSolvingScore() : 0.0;
        double alg = sd.getAlgorithmsScore() != null ? sd.getAlgorithmsScore() : 0.0;
        double ds = sd.getDataStructuresScore() != null ? sd.getDataStructuresScore() : 0.0;

        if (alg <= ds && alg <= ps) return "Algorithms";
        if (ds <= alg && ds <= ps) return "Data Structures";
        return "Problem Solving";
    }

    @GetMapping("/{id}/trending-questions")
    public ResponseEntity<?> getTrendingQuestions(@PathVariable Long id) {
        if (!isOwner(id)) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("error", "You are not authorized to view these questions.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
        }

        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("error", "Student not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }

        List<Map<String, Object>> tquestions = commonQuestionsService.getTrendingQuestionsForStudent(id);

        Optional<SkillData> skillDataOpt2 = skillDataRepository.findByStudent(studentOpt.get());
        SkillData skillData2 = skillDataOpt2.orElseGet(() -> skillService.updateSkillData(studentOpt.get()));

        List<Map<String, Object>> personalizedTrending = commonQuestionsService.personalizeQuestions(tquestions, skillData2, getTopPriority(skillData2));
        return ResponseEntity.ok(personalizedTrending);
    }

    @GetMapping("/refresh/{id}")
    public ResponseEntity<?> refreshSkills(@PathVariable Long id) {
        if (!isOwner(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You are not authorized to refresh these skills."));
        }
        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("error", "Student not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }

        Student student = studentOpt.get();
        SkillData updated = skillService.updateSkillData(student);

    Map<String, Object> resp = new HashMap<>();
    resp.put("message", "Skills refreshed successfully!");
    resp.put("skillData", updated);
    return ResponseEntity.ok(resp);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Student>> searchStudents(@RequestParam String name) {
        List<Student> students = studentRepository.findByNameContainingIgnoreCase(name);
        return ResponseEntity.ok(students);
    }

    @GetMapping
    public ResponseEntity<List<Student>> getAllStudents() {
        List<Student> students = studentRepository.findAll();
        return ResponseEntity.ok(students);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateStudent(@PathVariable Long id, @RequestBody Student studentDetails) {
        if (!isOwner(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You are not authorized to update this student."));
        }
        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        Student student = studentOpt.get();
        student.setName(studentDetails.getName());
        student.setEmail(studentDetails.getEmail());
        student.setLeetcodeUsername(studentDetails.getLeetcodeUsername());
        Student updatedStudent = studentRepository.save(student);

        return ResponseEntity.ok(updatedStudent);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStudent(@PathVariable Long id) {
        if (!isOwner(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You are not authorized to delete this student."));
        }
        if (!studentRepository.existsById(id)) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("error", "Student not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }

        studentRepository.deleteById(id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Student deleted successfully");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<Map<String, Object>>> getLeaderboard() {
        List<Student> students = studentRepository.findAll();

        List<Map<String, Object>> leaderboard = students.stream()
            .map(student -> {
                SkillData latest = skillDataRepository.findTopByStudentOrderByCreatedAtDesc(student)
                        .orElse(null);
                if (latest == null) return null;

                double totalScore = latest.getProblemSolvingScore()
                        + latest.getAlgorithmsScore()
                        + latest.getDataStructuresScore();

                Map<String, Object> entry = new HashMap<>();
                entry.put("name", student.getName());
                entry.put("leetcodeUsername", student.getLeetcodeUsername());
                entry.put("totalScore", totalScore);
                entry.put("ranking", latest.getRanking());
                return entry;
            })
            .filter(Objects::nonNull)
            .sorted((a, b) -> Double.compare((double) b.get("totalScore"), (double) a.get("totalScore")))
            .limit(10)
            .collect(Collectors.toList());

        return ResponseEntity.ok(leaderboard);
    }
}