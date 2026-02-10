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
import org.springframework.transaction.annotation.Transactional;
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
import com.skilltracker.student_skill_tracker.repository.LanguageSkillRepository;
import com.skilltracker.student_skill_tracker.repository.PasswordResetTokenRepository;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.CommonQuestionsService;
import com.skilltracker.student_skill_tracker.service.DashboardService;
import com.skilltracker.student_skill_tracker.service.GitHubService;
import com.skilltracker.student_skill_tracker.service.LeetCodeService;
import com.skilltracker.student_skill_tracker.service.SkillService;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private static final Logger logger = LoggerFactory.getLogger(StudentController.class);

    private final StudentRepository studentRepository;
    private final SkillDataRepository skillDataRepository;
    private final LanguageSkillRepository languageSkillRepository;
    private final SkillService skillService;
    private final DashboardService dashboardService;
    private final CommonQuestionsService commonQuestionsService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final GitHubService gitHubService;
    private final LeetCodeService leetCodeService;

    public StudentController(StudentRepository studentRepository,
            SkillDataRepository skillDataRepository,
            LanguageSkillRepository languageSkillRepository,
            SkillService skillService,
            DashboardService dashboardService,
            CommonQuestionsService commonQuestionsService,
            PasswordEncoder passwordEncoder,
            PasswordResetTokenRepository passwordResetTokenRepository,
            GitHubService gitHubService,
            LeetCodeService leetCodeService) {
        this.studentRepository = studentRepository;
        this.skillDataRepository = skillDataRepository;
        this.languageSkillRepository = languageSkillRepository;
        this.skillService = skillService;
        this.dashboardService = dashboardService;
        this.commonQuestionsService = commonQuestionsService;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.gitHubService = gitHubService;
        this.leetCodeService = leetCodeService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Student student) {
        if (!student.getPassword().equals(student.getConfirmPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Passwords do not match!"));
        }

        if (studentRepository.findByEmail(student.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email already exists!"));
        }

        if (studentRepository.findByLeetcodeUsername(student.getLeetcodeUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "LeetCode username already registered!"));
        }

        student.setPassword(passwordEncoder.encode(student.getPassword()));
        student.setRoles("ROLE_USER");
        Student savedStudent = studentRepository.save(student);
        // Call both methods separately to avoid self-invocation issues
        skillService.updateSkillData(savedStudent);
        skillService.updateLanguageSkills(savedStudent);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedStudent);
    }

    private boolean isOwner(Long studentId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return studentRepository.findByEmailIgnoreCase(email)
                .map(student -> student.getId().equals(studentId))
                .orElse(false);
    }

    @GetMapping("/dashboard/{id}")
    public ResponseEntity<?> showDashboard(@PathVariable Long id) {
        if (!isOwner(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You are not authorized to view this dashboard."));
        }

        logger.debug("Received dashboard request for id: {}", id);

        return dashboardService.getDashboardData(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found")));
    }

    @GetMapping("/me/dashboard")
    public ResponseEntity<?> showMyDashboard() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        return dashboardService.getDashboardDataByEmail(email)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found")));
    }

    @GetMapping("/me/language-skills")
    public ResponseEntity<List<Map<String, Object>>> getMyLanguageSkills() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(List.of());
        }

        Optional<Student> studentOpt = studentRepository.findByEmail(email);
        if (studentOpt.isEmpty()) {
            logger.warn("Student not found for email: {}", email);
            return ResponseEntity.ok(List.of());
        }

        Student student = studentOpt.get();
        List<Map<String, Object>> languageSkills = languageSkillRepository.findByStudent(student)
                .stream()
                .map(skill -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", String.valueOf(skill.getId()));
                    map.put("name", skill.getLanguageName());
                    map.put("category", "Technical");
                    map.put("rating", skill.getRating());
                    map.put("maxRating", 5);
                    map.put("problemsSolved", skill.getProblemsSolved());
                    return map;
                })
                .collect(Collectors.toList());

        logger.info("Returning {} language skills for user: {}", languageSkills.size(), email);
        return ResponseEntity.ok(languageSkills);
    }

    @GetMapping("/me/github-repos")
    public ResponseEntity<List<Map<String, Object>>> getMyGitHubRepos() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication != null ? authentication.getName() : null;
        logger.debug("GetMyGitHubRepos - authenticated user: {}", email);
        if (email == null || "anonymousUser".equals(email)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(List.of());
        }

        return studentRepository.findByEmailIgnoreCase(email)
                .map(student -> {
                    String githubUsername = student.getGithubUsername();
                    if (githubUsername == null || githubUsername.isEmpty()) {
                        logger.debug("GitHub username is not set for {}", email);
                        return ResponseEntity.ok(List.<Map<String, Object>>of());
                    }

                    try {
                        List<Map<String, Object>> repos = gitHubService.fetchRepos(githubUsername,
                                student.getGithubAccessToken());
                        return ResponseEntity.ok(repos);
                    } catch (Exception ex) {
                        logger.error("Failed to fetch GitHub repos for {}: {}", email, ex.getMessage(), ex);
                        return ResponseEntity.ok(List.<Map<String, Object>>of());
                    }
                })
                .orElseGet(() -> {
                    logger.warn("Student lookup failed for email: {}", email);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(List.of());
                });
    }

    @GetMapping("/me/leetcode-stats")
    public ResponseEntity<Map<String, Object>> getMyLeetCodeStats() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return studentRepository.findByEmail(email)
                .map(student -> {
                    String leetcodeUsername = student.getLeetcodeUsername();
                    if (leetcodeUsername == null || leetcodeUsername.isEmpty()) {
                        return new java.util.HashMap<String, Object>();
                    }
                    return leetCodeService.fetchFullStats(leetcodeUsername);
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/me/github-repos/{repoName}/languages")
    public ResponseEntity<?> getRepoLanguages(@PathVariable String repoName) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return studentRepository.findByEmail(email)
                .map(student -> {
                    String username = student.getGithubUsername();
                    String token = student.getGithubAccessToken();
                    if (username == null)
                        return ResponseEntity.badRequest().body("GitHub username not set");

                    return ResponseEntity.ok(gitHubService.fetchRepoLanguages(username, repoName, token));
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/me/github-repos/{repoName}/skeleton")
    public ResponseEntity<?> getRepoSkeleton(@PathVariable String repoName) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return studentRepository.findByEmail(email)
                .map(student -> {
                    String username = student.getGithubUsername();
                    String token = student.getGithubAccessToken();
                    if (username == null)
                        return ResponseEntity.badRequest().body("GitHub username not set");

                    return ResponseEntity.ok(gitHubService.fetchRepoSkeleton(username, repoName, token));
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/me/github-token")
    public ResponseEntity<?> updateGithubToken(@RequestBody Map<String, String> payload) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = payload.get("token");
        String username = payload.get("username");

        return studentRepository.findByEmailIgnoreCase(email)
                .map(student -> {
                    if (token != null)
                        student.setGithubAccessToken(token);
                    if (username != null && !username.isEmpty())
                        student.setGithubUsername(username);

                    studentRepository.save(student);
                    return ResponseEntity.ok((Object) Map.of("message", "GitHub profile linked successfully"));
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
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
        SkillData skillData;
        if (skillDataOpt.isEmpty()) {
            Student student = studentOpt.get();
            skillService.updateSkillData(student);
            skillService.updateLanguageSkills(student);
            skillData = new SkillData(); // Temporary empty data while update happens
            skillData.setStudent(student);
        } else {
            skillData = skillDataOpt.get();
        }

        List<Map<String, Object>> personalized = commonQuestionsService.personalizeQuestions(questions, skillData,
                getTopPriority(skillData));
        return ResponseEntity.ok(personalized);
    }

    private String getTopPriority(SkillData sd) {
        if (sd == null)
            return "Problem Solving"; // Default if no skill data

        double ps = sd.getProblemSolvingScore() != null ? sd.getProblemSolvingScore() : 0.0;
        double alg = sd.getAlgorithmsScore() != null ? sd.getAlgorithmsScore() : 0.0;
        double ds = sd.getDataStructuresScore() != null ? sd.getDataStructuresScore() : 0.0;

        if (alg <= ds && alg <= ps)
            return "Algorithms";
        if (ds <= alg && ds <= ps)
            return "Data Structures";
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
        SkillData skillData2;
        if (skillDataOpt2.isEmpty()) {
            Student student = studentOpt.get();
            skillService.updateSkillData(student);
            skillService.updateLanguageSkills(student);
            skillData2 = new SkillData(); // Temporary empty data while update happens
            skillData2.setStudent(student);
        } else {
            skillData2 = skillDataOpt2.get();
        }

        List<Map<String, Object>> personalizedTrending = commonQuestionsService.personalizeQuestions(tquestions,
                skillData2, getTopPriority(skillData2));
        return ResponseEntity.ok(personalizedTrending);
    }

    @GetMapping("/refresh/{id}")
    public ResponseEntity<?> refreshSkills(@PathVariable Long id) {
        if (!isOwner(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You are not authorized to refresh these skills."));
        }
        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("error", "Student not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }

        Student student = studentOpt.get();
        // Call both methods separately to avoid self-invocation issues
        skillService.updateSkillData(student);
        skillService.updateLanguageSkills(student);

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Skills refresh initiated! It might take a few moments for data to appear.");
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
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You are not authorized to update this student."));
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
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You are not authorized to delete this student."));
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

    @Transactional
    @DeleteMapping("/me")
    public ResponseEntity<?> deleteStudent(org.springframework.security.core.Authentication authentication) {
        String username = authentication.getName();
        Student student = studentRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // Delete associated skill data
        skillDataRepository.findByStudent(student).ifPresent(skillDataRepository::delete);

        // Delete associated password reset tokens
        passwordResetTokenRepository.deleteByStudent(student);

        // Delete the student
        studentRepository.delete(student);

        // Clear the security context
        SecurityContextHolder.clearContext();

        return ResponseEntity.ok().build();
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<Map<String, Object>>> getLeaderboard() {
        List<Student> students = studentRepository.findAll();

        List<Map<String, Object>> leaderboard = students.stream()
                .map(student -> {
                    SkillData latest = skillDataRepository.findTopByStudentOrderByCreatedAtDesc(student)
                            .orElse(null);
                    if (latest == null)
                        return null;

                    double totalScore = latest.getProblemSolvingScore()
                            + latest.getAlgorithmsScore()
                            + latest.getDataStructuresScore();

                    Map<String, Object> entry = new HashMap<>();
                    entry.put("name", student.getName());
                    entry.put("leetcodeUsername", student.getLeetcodeUsername());
                    entry.put("totalScore", totalScore);
                    entry.put("ranking", latest.getRanking());
                    entry.put("title", getTitleForScore(totalScore));
                    return entry;
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Double.compare((double) b.get("totalScore"), (double) a.get("totalScore")))
                .limit(10)
                .collect(Collectors.toList());

        return ResponseEntity.ok(leaderboard);
    }

    private String getTitleForScore(double score) {
        if (score >= 12000)
            return "The Architect";
        if (score >= 10000)
            return "Shadow Walker";
        if (score >= 8000)
            return "Algo Hunter";
        if (score >= 5000)
            return "Logic Master";
        if (score >= 2500)
            return "Code Warrior";
        return "Novice Seeker";
    }
}
