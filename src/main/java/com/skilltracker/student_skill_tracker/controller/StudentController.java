package com.skilltracker.student_skill_tracker.controller;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.SkillService;

@Controller
@RequestMapping("/")
public class StudentController {

    private final StudentRepository studentRepository;
    private final SkillDataRepository skillDataRepository;
    private final SkillService skillService;

    public StudentController(StudentRepository studentRepository, SkillDataRepository skillDataRepository, SkillService skillService) {
        this.studentRepository = studentRepository;
        this.skillDataRepository = skillDataRepository;
        this.skillService = skillService;
    }

    // Home route redirects to login
    @GetMapping
    public String home() {
        return "redirect:/login";
    }

    // Login form
    @GetMapping("/login")
    public String showLoginForm(Model model) {
        return "login";
    }

    // Handle login
    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password, Model model) {
        Optional<Student> studentOpt = studentRepository.findByEmail(email);
        
        if (studentOpt.isEmpty()) {
            model.addAttribute("error", "Email not found! Please register first.");
            return "login";
        }

        Student student = studentOpt.get();
        if (!student.getPassword().equals(password)) {
            model.addAttribute("error", "Invalid credentials.");
            return "login";
        }

        return "redirect:/dashboard/" + student.getId();
    }

    // Registration form
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("student", new Student());
        return "register";
    }

    // Handle registration
    @PostMapping("/register")
    public String register(@ModelAttribute Student student, Model model) {
        Optional<Student> existingByEmail = studentRepository.findByEmail(student.getEmail());
        if (existingByEmail.isPresent()) {
            model.addAttribute("error", "Email already exists! Please use a different email or login.");
            return "register";
        }

        Optional<Student> existingByUsername = studentRepository.findByLeetcodeUsername(student.getLeetcodeUsername());
        if (existingByUsername.isPresent()) {
            model.addAttribute("error", "LeetCode username already registered!");
            return "register";
        }

        Student saved = studentRepository.save(student);
        SkillData skillData = skillService.updateSkillData(saved);

        return "redirect:/dashboard/" + saved.getId();
    }

    // Dashboard view
    @GetMapping("/dashboard/{id}")
    public String showDashboard(@PathVariable Long id, Model model) {
        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            return "redirect:/login";
        }

        Student student = studentOpt.get();
        Optional<SkillData> skillDataOpt = skillDataRepository.findByStudent(student);
        
        SkillData skillData;
        if (skillDataOpt.isEmpty()) {
            // If no skill data exists, fetch it
            skillData = skillService.updateSkillData(student);
        } else {
            skillData = skillDataOpt.get();
        }

        model.addAttribute("student", student);
        model.addAttribute("skillData", skillData);
        return "dashboard";
    }

    // Refresh skill data manually
    @GetMapping("/refresh/{id}")
    public String refreshSkills(@PathVariable Long id, Model model) {
        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            return "redirect:/login";
        }

        Student student = studentOpt.get();
        SkillData updated = skillService.updateSkillData(student);

        model.addAttribute("student", student);
        model.addAttribute("skillData", updated);
        model.addAttribute("message", "Skills refreshed successfully!");
        return "redirect:/dashboard/" + id;
    }
}