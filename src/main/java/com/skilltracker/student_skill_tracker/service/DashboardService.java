package com.skilltracker.student_skill_tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ObjectMapper objectMapper;

    public DashboardService(StudentRepository studentRepository, SkillDataRepository skillDataRepository,
            SkillService skillService) {
        this.studentRepository = studentRepository;
        this.skillDataRepository = skillDataRepository;
        this.skillService = skillService;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public Optional<DashboardResponseDTO> getDashboardData(Long studentId) {
        return studentRepository.findById(studentId).map(this::buildDashboardResponse);
    }

    @Transactional(readOnly = true)
    public Optional<DashboardResponseDTO> getDashboardDataByEmail(String email) {
        return studentRepository.findByEmail(email).map(this::buildDashboardResponse);
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
        Map<String, Integer> emotionDist = calculateEmotionDistribution(student.getEmotionLogJson());

        return StudentDTO.builder()
                .id(student.getId())
                .name(student.getName())
                .email(student.getEmail())
                .leetcodeUsername(student.getLeetcodeUsername())
                .level(student.getLevel())
                .xp(student.getXp())
                .thinkingStyle(student.getThinkingStyle())
                .highestBloomLevel(student.getHighestBloomLevel())
                .duelWins(student.getDuelWins())
                .lastEmotionAfterFailure(student.getLastEmotionAfterFailure())
                .emotionDistribution(emotionDist)
                .build();
    }

    private Map<String, Integer> calculateEmotionDistribution(String emotionLogJson) {
        Map<String, Integer> dist = new HashMap<>();
        dist.put("frustrated", 0);
        dist.put("neutral", 0);
        dist.put("motivated", 0);

        if (emotionLogJson == null || emotionLogJson.isBlank()) {
            return dist;
        }

        try {
            JsonNode root = objectMapper.readTree(emotionLogJson);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    String emotion = node.path("emotion").asText();
                    if (!emotion.isBlank()) {
                        dist.put(emotion, dist.getOrDefault(emotion, 0) + 1);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse emotion log for distribution: {}", e.getMessage());
        }

        return dist;
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
                .reasoningScore(skillData.getReasoningScore())
                .criticalThinkingScore(skillData.getCriticalThinkingScore())
                .problemSolvingScoreHumanistic(skillData.getProblemSolvingScoreHumanistic())
                .eqScore(skillData.getEqScore())
                .build();
    }
}
