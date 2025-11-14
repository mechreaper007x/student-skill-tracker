package com.skilltracker.student_skill_tracker.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.util.AIAdvisor;
import com.skilltracker.student_skill_tracker.util.SkillCalculator;

@Service
public class SkillService {

    private final LeetCodeService leetCodeService;
    private final SkillDataRepository skillDataRepository;
    private final StudentRepository studentRepository;

    public SkillService(LeetCodeService leetCodeService, SkillDataRepository skillDataRepository, StudentRepository studentRepository) {
        this.leetCodeService = leetCodeService;
        this.skillDataRepository = skillDataRepository;
        this.studentRepository = studentRepository;
    }

    public SkillData updateSkillData(Student student) {
        System.out.println("Fetching data for user: " + student.getLeetcodeUsername());
        Map<String, Object> data = leetCodeService.fetchStats(student.getLeetcodeUsername());
        System.out.println("Stats data: " + data);
    // language proficiency removed to simplify frontend integration

        SkillData skillData = skillDataRepository.findByStudent(student).orElse(new SkillData());
        skillData.setStudent(student);

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
            return skillDataRepository.save(skillData);
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

        skillData.setProblemSolvingScore(ps);
        skillData.setAlgorithmsScore(algo);
        skillData.setDataStructuresScore(ds);
        skillData.setTotalProblemsSolved(total);
        skillData.setEasyProblems(easy);
        skillData.setMediumProblems(medium);
        skillData.setHardProblems(hard);
        skillData.setRanking(rank);
        skillData.setAiAdvice(advice);

        SkillData savedSkillData = skillDataRepository.save(skillData);

        Student studentToUpdate = savedSkillData.getStudent();
        int newXp = savedSkillData.calculateXp();
        studentToUpdate.setXp(newXp);
        if (studentToUpdate.getXp() >= 100) {
            studentToUpdate.levelUp();
        }
        studentRepository.save(studentToUpdate);

        return savedSkillData;
    }
}
