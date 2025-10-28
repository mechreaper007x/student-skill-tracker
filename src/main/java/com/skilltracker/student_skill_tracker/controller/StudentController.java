package com.skilltracker.student_skill_tracker.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.SkillService;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentRepository studentRepository;
    private final SkillDataRepository skillDataRepository;
    private final SkillService skillService;

    public StudentController(StudentRepository studentRepository, SkillDataRepository skillDataRepository, SkillService skillService) {
        this.studentRepository = studentRepository;
        this.skillDataRepository = skillDataRepository;
        this.skillService = skillService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String email = credentials.get("email");
        String password = credentials.get("password");

        Optional<Student> studentOpt = studentRepository.findByEmail(email);

        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Email not found!"));
        }

        Student student = studentOpt.get();
        if (!student.getPassword().equals(password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials."));
        }

        return ResponseEntity.ok(Map.of("id", student.getId(), "message", "Login successful"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Student student) {
        if (studentRepository.findByEmail(student.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email already exists!"));
        }

        if (studentRepository.findByLeetcodeUsername(student.getLeetcodeUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "LeetCode username already registered!"));
        }

        Student savedStudent = studentRepository.save(student);
        skillService.updateSkillData(savedStudent); // This can run in the background

        return ResponseEntity.status(HttpStatus.CREATED).body(savedStudent);
    }

    @GetMapping("/dashboard/{id}")
    public ResponseEntity<?> showDashboard(@PathVariable Long id) {
        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        Student student = studentOpt.get();
        Optional<SkillData> skillDataOpt = skillDataRepository.findByStudent(student);

        SkillData skillData = skillDataOpt.orElseGet(() -> skillService.updateSkillData(student));

        return ResponseEntity.ok(Map.of("student", student, "skillData", skillData));
    }

    @GetMapping("/refresh/{id}")
    public ResponseEntity<?> refreshSkills(@PathVariable Long id) {
        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        Student student = studentOpt.get();
        SkillData updated = skillService.updateSkillData(student);

        return ResponseEntity.ok(Map.of("message", "Skills refreshed successfully!", "skillData", updated));
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
        if (!studentRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        studentRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Student deleted successfully"));
    }
}
