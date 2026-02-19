package com.skilltracker.student_skill_tracker.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.skilltracker.student_skill_tracker.dto.DashboardResponseDTO;
import com.skilltracker.student_skill_tracker.dto.SkillDataDTO;
import com.skilltracker.student_skill_tracker.dto.StudentDTO;
import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;

@Service
public class DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

    private final StudentRepository studentRepository;
    private final SkillDataRepository skillDataRepository;
    private final SkillService skillService;

    public DashboardService(StudentRepository studentRepository, SkillDataRepository skillDataRepository,
            SkillService skillService) {
        this.studentRepository = studentRepository;
        this.skillDataRepository = skillDataRepository;
        this.skillService = skillService;
    }

    public Optional<DashboardResponseDTO> getDashboardData(Long studentId) {
        return studentRepository.findById(studentId)
                .map(this::buildDashboardResponse);
    }

    public Optional<DashboardResponseDTO> getDashboardDataByEmail(String email) {
        return studentRepository.findByEmail(email)
                .map(this::buildDashboardResponse);
    }

    private DashboardResponseDTO buildDashboardResponse(Student student) {
        SkillData skillData = skillDataRepository.findByStudent(student)
                .orElseGet(() -> {
                    skillService.updateSkillData(student);
                    SkillData sd = new SkillData();
                    sd.setStudent(student);
                    return sd;
                });

        return DashboardResponseDTO.builder()
                .student(mapToStudentDTO(student))
                .skillData(mapToSkillDataDTO(skillData))
                .build();
    }

    private StudentDTO mapToStudentDTO(Student student) {
        return StudentDTO.builder()
                .id(student.getId())
                .name(student.getName())
                .email(student.getEmail())
                .leetcodeUsername(student.getLeetcodeUsername())
                .level(student.getLevel())
                .xp(student.getXp())
                .build();
    }

    private SkillDataDTO mapToSkillDataDTO(SkillData skillData) {
        return SkillDataDTO.builder()
                .id(skillData.getId())
                .problemSolvingScore(skillData.getProblemSolvingScore())
                .algorithmsScore(skillData.getAlgorithmsScore())
                .dataStructuresScore(skillData.getDataStructuresScore())
                .totalProblemsSolved(skillData.getTotalProblemsSolved())
                .easyProblems(skillData.getEasyProblems())
                .mediumProblems(skillData.getMediumProblems())
                .hardProblems(skillData.getHardProblems())
                .ranking(skillData.getRanking())
                .aiAdvice(skillData.getAiAdvice())
                .build();
    }
}
