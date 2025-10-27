package com.skilltracker.student_skill_tracker.controller;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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

    // Home route redirects to register
    @GetMapping
    public String home() {
        return "redirect:/register";
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
        Optional<Student> existing = studentRepository.findByEmail(student.getEmail());
        if (existing.isPresent()) {
            model.addAttribute("error", "Email already exists!");
            return "register";
        }

        Student saved = studentRepository.save(student);
        SkillData skillData = skillService.updateSkillData(saved);

        model.addAttribute("student", saved);
        model.addAttribute("skillData", skillData);
        return "redirect:/dashboard/" + saved.getId();
    }

    // Dashboard view
    @GetMapping("/dashboard/{id}")
    public String showDashboard(@PathVariable Long id, Model model) {
        Student student = studentRepository.findById(id).orElseThrow();
        SkillData skillData = skillDataRepository.findByStudent(student).orElse(new SkillData());

        model.addAttribute("student", student);
        model.addAttribute("skillData", skillData);
        return "dashboard";
    }

    // Refresh skill data manually
    @GetMapping("/refresh/{id}")
    public String refreshSkills(@PathVariable Long id, Model model) {
        Student student = studentRepository.findById(id).orElseThrow();
        SkillData updated = skillService.updateSkillData(student);

        model.addAttribute("student", student);
        model.addAttribute("skillData", updated);
        model.addAttribute("message", "Skills refreshed successfully!");
        return "redirect:/dashboard/" + id;
    }
}
