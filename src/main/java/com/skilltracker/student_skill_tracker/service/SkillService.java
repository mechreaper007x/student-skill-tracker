package com.skilltracker.student_skill_tracker.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skilltracker.student_skill_tracker.dto.AdvisorResult;
import com.skilltracker.student_skill_tracker.model.LanguageSkill;
import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.LanguageSkillRepository;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.util.SkillCalculator;

@Service
public class SkillService {

    private static final Logger logger = LoggerFactory.getLogger(SkillService.class);

    private final LeetCodeService leetCodeService;
    private final SkillDataRepository skillDataRepository;
    private final StudentRepository studentRepository;
    private final LanguageSkillRepository languageSkillRepository;
    private final SkillCalculator skillCalculator;
    private final AiAdvisorService aiAdvisorService;

    public SkillService(LeetCodeService leetCodeService,
            SkillDataRepository skillDataRepository,
            StudentRepository studentRepository,
            LanguageSkillRepository languageSkillRepository,
            SkillCalculator skillCalculator,
            AiAdvisorService aiAdvisorService) {
        this.leetCodeService = leetCodeService;
        this.skillDataRepository = skillDataRepository;
        this.studentRepository = studentRepository;
        this.languageSkillRepository = languageSkillRepository;
        this.skillCalculator = skillCalculator;
        this.aiAdvisorService = aiAdvisorService;
    }

    @Async
    @Transactional
    public void updateSkillData(Student student) {
        logger.info("Asynchronously fetching LeetCode data for user: {}", student.getLeetcodeUsername());
        Map<String, Object> data = leetCodeService.fetchStats(student.getLeetcodeUsername());

        SkillData skillData = skillDataRepository.findByStudent(student).orElse(new SkillData());
        skillData.setStudent(student);

        if (data.isEmpty()) {
            logger.warn("No data returned from LeetCode for user: {}", student.getLeetcodeUsername());
            skillData.setProblemSolvingScore(0.0);
            skillData.setAlgorithmsScore(0.0);
            skillData.setDataStructuresScore(0.0);
            skillData.setTotalProblemsSolved(0);
            skillData.setEasyProblems(0);
            skillData.setMediumProblems(0);
            skillData.setHardProblems(0);
            skillData.setRanking(0);
            skillData.setAiAdvice("LeetCode data not available. Please check the username and try again.");
            skillDataRepository.save(skillData);
            return;
        }

        int total = (int) data.getOrDefault("totalSolved", 0);
        int easy = (int) data.getOrDefault("easySolved", 0);
        int medium = (int) data.getOrDefault("mediumSolved", 0);
        int hard = (int) data.getOrDefault("hardSolved", 0);
        int rank = (int) data.getOrDefault("ranking", 0);

        double ps = skillCalculator.calculateProblemSolvingScore(total);
        double algo = skillCalculator.calculateAlgorithmsScore(medium, hard);
        double ds = skillCalculator.calculateDataStructuresScore(easy, medium);

        skillData.setProblemSolvingScore(ps);
        skillData.setAlgorithmsScore(algo);
        skillData.setDataStructuresScore(ds);
        skillData.setTotalProblemsSolved(total);
        skillData.setEasyProblems(easy);
        skillData.setMediumProblems(medium);
        skillData.setHardProblems(hard);
        skillData.setRanking(rank);

        // Use AiAdvisorService to generate a consolidated advice summary
        AdvisorResult advisorResult = aiAdvisorService.advise(skillData);
        skillData.setAiAdvice(advisorResult.getSummary());

        SkillData savedSkillData = skillDataRepository.save(skillData);

        Student studentToUpdate = savedSkillData.getStudent();
        int newXp = savedSkillData.calculateXp();
        studentToUpdate.setXp(newXp);
        if (studentToUpdate.getXp() >= 100) {
            studentToUpdate.levelUp();
        }
        studentRepository.save(studentToUpdate);

        // NOTE: Language skills should be updated externally to avoid self-invocation
        // The controller should call updateLanguageSkills() separately after this
        // method completes
        logger.info("Skill data updated. Language skills should be fetched separately.");
    }

    @Async
    @Transactional
    public void updateLanguageSkills(Student student) {
        logger.info("Fetching language skills for user: {}", student.getLeetcodeUsername());
        Map<String, Object> response = leetCodeService.fetchLanguageStats(student.getLeetcodeUsername());

        if (response.isEmpty() || !response.containsKey("data")) {
            logger.warn("No language data returned from LeetCode for user: {}", student.getLeetcodeUsername());
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> matchedUser = (Map<String, Object>) data.get("matchedUser");

            if (matchedUser == null) {
                logger.warn("No matched user found for: {}", student.getLeetcodeUsername());
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> languageCounts = (List<Map<String, Object>>) matchedUser
                    .get("languageProblemCount");

            if (languageCounts == null || languageCounts.isEmpty()) {
                logger.warn("No language problem counts found for user: {}", student.getLeetcodeUsername());
                return;
            }

            // Clear existing language skills for this student
            languageSkillRepository.deleteByStudent(student);

            // Process and save new language skills
            for (Map<String, Object> langData : languageCounts) {
                String languageName = (String) langData.get("languageName");
                Integer problemsSolved = (Integer) langData.get("problemsSolved");

                if (languageName != null && problemsSolved != null && problemsSolved > 0) {
                    int rating = LanguageSkill.calculateRating(problemsSolved);

                    LanguageSkill skill = LanguageSkill.builder()
                            .student(student)
                            .languageName(languageName)
                            .problemsSolved(problemsSolved)
                            .rating(rating)
                            .build();

                    languageSkillRepository.save(skill);
                    logger.info("Saved language skill: {} with {} problems (rating: {})",
                            languageName, problemsSolved, rating);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing language skills: " + e.getMessage(), e);
        }
    }
}
