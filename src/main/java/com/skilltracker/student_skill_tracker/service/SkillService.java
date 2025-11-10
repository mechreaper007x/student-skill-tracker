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
        System.out.println("Fetching data for user: " + student.getLeetcodeUsername());
        Map<String, Object> data = leetCodeService.fetchStats(student.getLeetcodeUsername());
        System.out.println("Stats data: " + data);
    // language proficiency removed to simplify frontend integration

        if (data.isEmpty()) {
            SkillData emptySkillData = SkillData.builder()
                .student(student)
                .problemSolvingScore(0.0)
                .algorithmsScore(0.0)
                .dataStructuresScore(0.0)
                .totalProblemsSolved(0)
                .easyProblems(0)
                .mediumProblems(0)
                .hardProblems(0)
                .ranking(0)
                .aiAdvice("LeetCode data not available. Please check the username and try again.")
                .build();
            return skillDataRepository.save(emptySkillData);
        }

        int total = (int) data.getOrDefault("totalSolved", 0);
        int easy  = (int) data.getOrDefault("easySolved", 0);
        int medium= (int) data.getOrDefault("mediumSolved", 0);
        int hard  = (int) data.getOrDefault("hardSolved", 0);
        int rank  = (int) data.getOrDefault("ranking", 0);

        double ps = SkillCalculator.problemSolvingScore(total);
        double algo = SkillCalculator.algorithmsScore(medium, hard);
        double ds = SkillCalculator.dataStructuresScore(easy, medium);

        String advice = AIAdvisor.generateAdvice(ps, algo, ds);

        // language proficiency computation removed

        SkillData skillData = SkillData.builder()
                .student(student)
                .problemSolvingScore(ps)
                .algorithmsScore(algo)
                .dataStructuresScore(ds)
                
                .totalProblemsSolved(total)
                .easyProblems(easy)
                .mediumProblems(medium)
                .hardProblems(hard)
                .ranking(rank)
                .aiAdvice(advice)
                .build();

        return skillDataRepository.save(skillData);
    }
}
