package com.skilltracker.student_skill_tracker.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;
import com.skilltracker.student_skill_tracker.util.AIAdvisor;
import com.skilltracker.student_skill_tracker.util.SkillCalculator;

@Service
public class SkillService {

    private final LeetCodeService leetCodeService;
    private final SkillDataRepository skillDataRepository;

    public SkillService(LeetCodeService leetCodeService, SkillDataRepository skillDataRepository) {
        this.leetCodeService = leetCodeService;
        this.skillDataRepository = skillDataRepository;
    }

    public SkillData updateSkillData(Student student) {
        Map<String, Object> data = leetCodeService.fetchStats(student.getLeetcodeUsername());

        SkillData skillData = skillDataRepository.findByStudent(student)
                .orElse(SkillData.builder().student(student).build());

        if (data.isEmpty()) {
            skillData.setProblemSolvingScore(0.0);
            skillData.setAlgorithmsScore(0.0);
            skillData.setDataStructuresScore(0.0);
            skillData.setTotalProblemsSolved(0);
            skillData.setEasyProblems(0);
            skillData.setMediumProblems(0);
            skillData.setHardProblems(0);
            skillData.setRanking(0);
            skillData.setAiAdvice("LeetCode data not available. Please check the username and try again.");
        } else {
            int total = (int) data.getOrDefault("totalSolved", 0);
            int easy = (int) data.getOrDefault("easySolved", 0);
            int medium = (int) data.getOrDefault("mediumSolved", 0);
            int hard = (int) data.getOrDefault("hardSolved", 0);
            int rank = (int) data.getOrDefault("ranking", 0);

            double ps = SkillCalculator.problemSolvingScore(total);
            double algo = SkillCalculator.algorithmsScore(medium, hard);
            double ds = SkillCalculator.dataStructuresScore(easy, medium);

            String advice = AIAdvisor.generateAdvice(ps, algo, ds);

            skillData.setProblemSolvingScore(ps);
            skillData.setAlgorithmsScore(algo);
            skillData.setDataStructuresScore(ds);
            skillData.setTotalProblemsSolved(total);
            skillData.setEasyProblems(easy);
            skillData.setMediumProblems(medium);
            skillData.setHardProblems(hard);
            skillData.setRanking(rank);
            skillData.setAiAdvice(advice);
        }

        return skillDataRepository.save(skillData);
    }
}
