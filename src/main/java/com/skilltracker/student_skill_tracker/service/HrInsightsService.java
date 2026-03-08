package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skilltracker.student_skill_tracker.dto.HrCandidateCardDto;
import com.skilltracker.student_skill_tracker.dto.HrCandidateSummaryDto;
import com.skilltracker.student_skill_tracker.dto.HrInterviewInsightsDto;
import com.skilltracker.student_skill_tracker.dto.InterviewerFeedbackRequestDto;
import com.skilltracker.student_skill_tracker.dto.InterviewerFeedbackResponseDto;
import com.skilltracker.student_skill_tracker.model.InterviewerFeedback;
import com.skilltracker.student_skill_tracker.model.RishiCodingSession;
import com.skilltracker.student_skill_tracker.model.RishiCompileAttemptLog;
import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.InterviewerFeedbackRepository;
import com.skilltracker.student_skill_tracker.repository.RishiCodingSessionRepository;
import com.skilltracker.student_skill_tracker.repository.RishiCompileAttemptLogRepository;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;

@Service
public class HrInsightsService {

    private final StudentRepository studentRepository;
    private final SkillDataRepository skillDataRepository;
    private final RishiCodingSessionRepository sessionRepository;
    private final RishiCompileAttemptLogRepository compileAttemptLogRepository;
    private final InterviewerFeedbackRepository feedbackRepository;

    public HrInsightsService(
            StudentRepository studentRepository,
            SkillDataRepository skillDataRepository,
            RishiCodingSessionRepository sessionRepository,
            RishiCompileAttemptLogRepository compileAttemptLogRepository,
            InterviewerFeedbackRepository feedbackRepository) {
        this.studentRepository = studentRepository;
        this.skillDataRepository = skillDataRepository;
        this.sessionRepository = sessionRepository;
        this.compileAttemptLogRepository = compileAttemptLogRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public List<HrCandidateCardDto> getCandidates(String name) {
        String search = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        List<Student> students = studentRepository.findAll().stream()
                .filter(student -> search.isBlank() || containsIgnoreCase(student.getName(), search)
                        || containsIgnoreCase(student.getEmail(), search)
                        || containsIgnoreCase(student.getLeetcodeUsername(), search))
                .toList();

        List<HrCandidateCardDto> cards = new ArrayList<>(students.size());
        for (Student student : students) {
            CandidateScores scores = calculateScores(student);
            cards.add(HrCandidateCardDto.builder()
                    .candidateId(student.getId())
                    .name(student.getName())
                    .email(student.getEmail())
                    .leetcodeUsername(student.getLeetcodeUsername())
                    .overallReadinessScore(scores.overallReadinessScore())
                    .technicalScore(scores.technicalScore())
                    .communicationScore(scores.communicationScore())
                    .consistencyScore(scores.consistencyScore())
                    .confidenceScore(scores.confidenceScore())
                    .trend(scores.trend())
                    .recommendationBand(toRecommendationBand(scores.overallReadinessScore()))
                    .lastActiveAt(scores.lastActiveAt())
                    .riskFlags(scores.riskFlags())
                    .positiveSignals(scores.positiveSignals())
                    .build());
        }

        cards.sort(Comparator.comparing(HrCandidateCardDto::getOverallReadinessScore).reversed());
        return cards;
    }

    @Transactional(readOnly = true)
    public Optional<HrCandidateSummaryDto> getCandidateSummary(Long candidateId) {
        return studentRepository.findById(candidateId).map(student -> {
            CandidateScores scores = calculateScores(student);
            Optional<SkillData> latestSkillData = skillDataRepository.findTopByStudentOrderByCreatedAtDesc(student);
            List<InterviewerFeedback> feedback = feedbackRepository.findByCandidateOrderByCreatedAtDesc(student);

            Map<String, Double> radar = new LinkedHashMap<>();
            radar.put("DSA", round2(scores.technicalScore()));
            radar.put("Problem Solving", round2(scores.processScore()));
            radar.put("Communication", round2(scores.communicationScore()));
            radar.put("Consistency", round2(scores.consistencyScore()));
            radar.put("Growth", round2(scores.growthScore()));

            Map<String, Double> heatmap = new LinkedHashMap<>();
            heatmap.put("Algorithms", normalizeSkill(latestSkillData.map(SkillData::getAlgorithmsScore).orElse(0.0)));
            heatmap.put("Data Structures",
                    normalizeSkill(latestSkillData.map(SkillData::getDataStructuresScore).orElse(0.0)));
            heatmap.put("Problem Solving",
                    normalizeSkill(latestSkillData.map(SkillData::getProblemSolvingScore).orElse(0.0)));
            heatmap.put("Debugging", round2(scores.processScore()));
            heatmap.put("Interview Simulation", round2(scores.consistencyScore()));

            List<RishiCodingSession> sessions30 = sessionRepository.findByStudentAndStartedAtAfter(
                    student, LocalDateTime.now().minusDays(30));
            double focusHours = sessions30.stream()
                    .mapToLong(s -> safeLong(s.getActiveDurationMs()))
                    .sum() / 3_600_000.0;

            Map<String, Double> timeline = new LinkedHashMap<>();
            timeline.put("sessions_30d", (double) sessions30.size());
            timeline.put("success_rate_30d", scores.successRate30Days());
            timeline.put("focus_hours_30d", round2(focusHours));
            timeline.put("mock_interviews", (double) feedback.size());
            timeline.put("hard_problems",
                    (double) safeInt(latestSkillData.map(SkillData::getHardProblems).orElse(0)));

            List<Map<String, Object>> feedbackView = feedback.stream().limit(5).map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("interviewerEmail", item.getInterviewerEmail());
                row.put("recommendation", item.getRecommendation());
                row.put("weightedTotalScore", round2(item.getWeightedTotalScore()));
                row.put("recommendationReason", item.getRecommendationReason());
                row.put("createdAt", item.getCreatedAt());
                return row;
            }).toList();

            return HrCandidateSummaryDto.builder()
                    .candidateId(student.getId())
                    .name(student.getName())
                    .email(student.getEmail())
                    .leetcodeUsername(student.getLeetcodeUsername())
                    .overallReadinessScore(scores.overallReadinessScore())
                    .technicalScore(scores.technicalScore())
                    .processScore(scores.processScore())
                    .communicationScore(scores.communicationScore())
                    .consistencyScore(scores.consistencyScore())
                    .growthScore(scores.growthScore())
                    .confidenceScore(scores.confidenceScore())
                    .trend(scores.trend())
                    .recommendationBand(toRecommendationBand(scores.overallReadinessScore()))
                    .aiBriefing(buildBriefing(student, scores))
                    .lastActiveAt(scores.lastActiveAt())
                    .radar(radar)
                    .heatmap(heatmap)
                    .timeline(timeline)
                    .riskFlags(scores.riskFlags())
                    .positiveSignals(scores.positiveSignals())
                    .recentFeedback(feedbackView)
                    .build();
        });
    }

    @Transactional(readOnly = true)
    public Optional<HrInterviewInsightsDto> getInterviewInsights(Long candidateId) {
        return studentRepository.findById(candidateId).map(student -> {
            CandidateScores scores = calculateScores(student);
            Map<String, Double> behavioral = new LinkedHashMap<>();
            behavioral.put("responseClarity", round2(scores.communicationScore()));
            behavioral.put("collaborationSignal", round2((scores.communicationScore() + scores.consistencyScore()) / 2.0));
            behavioral.put("persistence", round2(scores.processScore()));
            behavioral.put("frustrationRecovery", round2(recoveryScore(student)));
            behavioral.put("toneIndicator", round2(tonesScore(student)));

            List<String> focusAreas = recommendedFocusAreas(scores);
            List<String> questions = focusAreas.stream().map(this::toTargetedQuestion).toList();

            return HrInterviewInsightsDto.builder()
                    .candidateId(student.getId())
                    .briefing(buildBriefing(student, scores))
                    .behavioralScores(behavioral)
                    .recommendedFocusAreas(focusAreas)
                    .suggestedQuestions(questions)
                    .build();
        });
    }

    @Transactional
    public Optional<InterviewerFeedbackResponseDto> submitFeedback(
            Long candidateId,
            String interviewerEmail,
            InterviewerFeedbackRequestDto request) {
        return studentRepository.findById(candidateId).map(candidate -> {
            double technicalDepth = clampScore(request.getTechnicalDepthScore());
            double problemSolving = clampScore(request.getProblemSolvingScore());
            double communication = clampScore(request.getCommunicationScore());
            double consistency = clampScore(request.getConsistencyScore());
            double growth = clampScore(request.getGrowthScore());

            double weightedTotal = round2(
                    technicalDepth * 0.40
                            + problemSolving * 0.20
                            + communication * 0.20
                            + consistency * 0.10
                            + growth * 0.10);

            InterviewerFeedback saved = feedbackRepository.save(InterviewerFeedback.builder()
                    .candidate(candidate)
                    .interviewerEmail(interviewerEmail)
                    .technicalDepthScore(technicalDepth)
                    .problemSolvingScore(problemSolving)
                    .communicationScore(communication)
                    .consistencyScore(consistency)
                    .growthScore(growth)
                    .weightedTotalScore(weightedTotal)
                    .recommendation(safeText(request.getRecommendation(), 32))
                    .recommendationReason(safeText(request.getRecommendationReason(), 1500))
                    .notes(safeText(request.getNotes(), 3000))
                    .build());

            return InterviewerFeedbackResponseDto.builder()
                    .id(saved.getId())
                    .candidateId(candidateId)
                    .interviewerEmail(saved.getInterviewerEmail())
                    .weightedTotalScore(saved.getWeightedTotalScore())
                    .recommendation(saved.getRecommendation())
                    .recommendationReason(saved.getRecommendationReason())
                    .notes(saved.getNotes())
                    .createdAt(saved.getCreatedAt())
                    .build();
        });
    }

    private CandidateScores calculateScores(Student student) {
        LocalDateTime now = LocalDateTime.now();
        Optional<SkillData> latestSkillData = skillDataRepository.findTopByStudentOrderByCreatedAtDesc(student);

        List<RishiCompileAttemptLog> attempts30 = compileAttemptLogRepository
                .findBySessionStudentAndAttemptedAtAfterOrderByAttemptedAtDesc(student, now.minusDays(30));
        List<RishiCompileAttemptLog> attempts14 = compileAttemptLogRepository
                .findBySessionStudentAndAttemptedAtAfterOrderByAttemptedAtDesc(student, now.minusDays(14));
        List<RishiCompileAttemptLog> attempts28 = compileAttemptLogRepository
                .findBySessionStudentAndAttemptedAtAfterOrderByAttemptedAtDesc(student, now.minusDays(28));

        List<RishiCompileAttemptLog> previous14 = attempts28.stream()
                .filter(log -> log.getAttemptedAt() != null && log.getAttemptedAt().isBefore(now.minusDays(14)))
                .toList();

        List<RishiCodingSession> sessions30 = sessionRepository.findByStudentAndStartedAtAfter(student, now.minusDays(30));
        Optional<RishiCodingSession> latestSession = sessionRepository.findTopByStudentOrderByLastActivityAtDesc(student);
        Optional<RishiCompileAttemptLog> latestAttempt = compileAttemptLogRepository.findTopBySessionStudentOrderByAttemptedAtDesc(student);

        double technicalScore = computeTechnicalScore(student, latestSkillData, attempts30);
        double processScore = computeProcessScore(student, attempts30);
        double communicationScore = computeCommunicationScore(student, latestSkillData);
        double consistencyScore = computeConsistencyScore(attempts30, sessions30);
        double growthScore = computeGrowthScore(attempts14, previous14, sessions30);
        double confidenceScore = computeConfidenceScore(latestSkillData.isPresent(), attempts30.size(), sessions30.size());

        double overall = round2(
                technicalScore * 0.40
                        + processScore * 0.20
                        + communicationScore * 0.20
                        + consistencyScore * 0.10
                        + growthScore * 0.10);

        LocalDateTime lastActiveAt = latestAttempt.map(RishiCompileAttemptLog::getAttemptedAt)
                .or(() -> latestSession.map(RishiCodingSession::getLastActivityAt))
                .orElse(student.getUpdatedAt());

        List<String> riskFlags = buildRiskFlags(student, technicalScore, communicationScore, consistencyScore, attempts30);
        List<String> positiveSignals = buildPositiveSignals(growthScore, consistencyScore, processScore);

        return new CandidateScores(
                round2(overall),
                round2(technicalScore),
                round2(processScore),
                round2(communicationScore),
                round2(consistencyScore),
                round2(growthScore),
                round2(confidenceScore),
                toTrend(growthScore),
                lastActiveAt,
                round2(successRate(attempts30)),
                riskFlags,
                positiveSignals);
    }

    private double computeTechnicalScore(Student student, Optional<SkillData> skillData, List<RishiCompileAttemptLog> attempts30) {
        double skillBase = normalizeAverage(List.of(
                skillData.map(SkillData::getProblemSolvingScore).orElse(0.0),
                skillData.map(SkillData::getAlgorithmsScore).orElse(0.0),
                skillData.map(SkillData::getDataStructuresScore).orElse(0.0)));
        double successRate = successRate(attempts30);
        double hardProblems = clampScore((safeInt(skillData.map(SkillData::getHardProblems).orElse(0)) / 40.0) * 100.0);
        return clampScore(skillBase * 0.55 + successRate * 0.30 + hardProblems * 0.15);
    }

    private double computeProcessScore(Student student, List<RishiCompileAttemptLog> attempts30) {
        double successRate = successRate(attempts30);
        double firstAttemptRatio = 0.0;
        if (safeInt(student.getTotalSubmissions()) > 0) {
            firstAttemptRatio = (safeInt(student.getFirstAttemptSuccessCount()) * 100.0) / safeInt(student.getTotalSubmissions());
        }
        return clampScore(successRate * 0.65 + clampScore(firstAttemptRatio) * 0.35);
    }

    private double computeCommunicationScore(Student student, Optional<SkillData> skillData) {
        double eq = normalizeSkill(skillData.map(SkillData::getEqScore).orElse(0.0));
        double criticalThinking = normalizeSkill(skillData.map(SkillData::getCriticalThinkingScore).orElse(0.0));
        double styleBoost = student.getThinkingStyle() == null || student.getThinkingStyle().isBlank() ? 0.0 : 12.0;
        return clampScore(eq * 0.45 + criticalThinking * 0.45 + styleBoost);
    }

    private double computeConsistencyScore(List<RishiCompileAttemptLog> attempts30, List<RishiCodingSession> sessions30) {
        if (sessions30.isEmpty()) {
            return 0.0;
        }
        long activeDays = sessions30.stream()
                .map(session -> session.getStartedAt() != null ? session.getStartedAt().toLocalDate() : null)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        double cadence = Math.min(100.0, (activeDays / 30.0) * 100.0 * 1.8);
        double variancePenalty = Math.min(35.0, accuracyStdDev(attempts30) * 0.8);
        return clampScore(cadence - variancePenalty + 20.0);
    }

    private double computeGrowthScore(
            List<RishiCompileAttemptLog> current14,
            List<RishiCompileAttemptLog> previous14,
            List<RishiCodingSession> sessions30) {
        double currentSuccess = successRate(current14);
        double previousSuccess = successRate(previous14);
        double delta = currentSuccess - previousSuccess;
        double momentum = 50.0 + delta;
        double sessionBonus = Math.min(20.0, sessions30.size() * 1.5);
        return clampScore(momentum + sessionBonus - 10.0);
    }

    private double computeConfidenceScore(boolean hasSkillData, int attempts30, int sessions30) {
        double skillDataFactor = hasSkillData ? 30.0 : 0.0;
        double attemptsFactor = Math.min(45.0, attempts30 * 1.5);
        double sessionsFactor = Math.min(25.0, sessions30 * 1.2);
        return clampScore(skillDataFactor + attemptsFactor + sessionsFactor);
    }

    private String buildBriefing(Student student, CandidateScores scores) {
        return String.format(
                Locale.ROOT,
                "%s shows %s readiness with %.1f/100 overall. Technical depth is %.1f, communication is %.1f, and consistency is %.1f. Trend is %s; focus interview time on the weakest dimension and validate real-time problem-solving clarity.",
                student.getName(),
                toRecommendationBand(scores.overallReadinessScore()),
                scores.overallReadinessScore(),
                scores.technicalScore(),
                scores.communicationScore(),
                scores.consistencyScore(),
                scores.trend());
    }

    private List<String> buildRiskFlags(
            Student student,
            double technicalScore,
            double communicationScore,
            double consistencyScore,
            List<RishiCompileAttemptLog> attempts30) {
        List<String> flags = new ArrayList<>();
        if (attempts30.size() < 5) {
            flags.add("Low confidence: insufficient recent assessment data.");
        }
        if (accuracyStdDev(attempts30) > 30.0) {
            flags.add("Inconsistent performance variance detected.");
        }
        if (technicalScore >= 70.0 && communicationScore < 45.0) {
            flags.add("Strong fundamentals but low communication confidence.");
        }
        if (safeInt(student.getTotalSubmissions()) > 100 && technicalScore < 45.0) {
            flags.add("Weak fundamentals despite high solved count.");
        }
        if (consistencyScore < 35.0) {
            flags.add("Low weekly consistency and discipline.");
        }
        return flags;
    }

    private List<String> buildPositiveSignals(double growthScore, double consistencyScore, double processScore) {
        List<String> signals = new ArrayList<>();
        if (growthScore >= 60.0) {
            signals.add("Strong growth trajectory.");
        }
        if (consistencyScore >= 60.0) {
            signals.add("Consistent weekly practice habit.");
        }
        if (processScore >= 65.0) {
            signals.add("Reliable debugging and problem-solving process.");
        }
        if (signals.isEmpty()) {
            signals.add("Baseline signals available; needs deeper live interview validation.");
        }
        return signals;
    }

    private List<String> recommendedFocusAreas(CandidateScores scores) {
        Map<String, Double> dimensions = new LinkedHashMap<>();
        dimensions.put("Technical fundamentals", scores.technicalScore());
        dimensions.put("Problem-solving process", scores.processScore());
        dimensions.put("Communication clarity", scores.communicationScore());
        dimensions.put("Consistency under pressure", scores.consistencyScore());
        dimensions.put("Growth mindset", scores.growthScore());

        return dimensions.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String toTargetedQuestion(String focusArea) {
        return switch (focusArea) {
            case "Technical fundamentals" -> "Give one medium-hard DSA problem and ask for tradeoff analysis before coding.";
            case "Problem-solving process" -> "Ask candidate to explain test strategy and edge cases before first run.";
            case "Communication clarity" -> "Use a pair-programming style prompt and evaluate step-by-step narration.";
            case "Consistency under pressure" -> "Introduce one requirement change mid-solution and observe adaptation.";
            default -> "Ask for one reflection on a failed attempt and what changed in the next iteration.";
        };
    }

    private double tonesScore(Student student) {
        if (student.getThinkingStyle() == null || student.getThinkingStyle().isBlank()) {
            return 50.0;
        }
        return 70.0;
    }

    private double recoveryScore(Student student) {
        long recoveryMs = safeLong(student.getAvgRecoveryVelocityMs());
        if (recoveryMs <= 0L) {
            return 55.0;
        }
        double score = 100.0 - (recoveryMs / 1000.0) / 2.0;
        return clampScore(score);
    }

    private String toRecommendationBand(double score) {
        if (score >= 80.0) {
            return "Strong Hire";
        }
        if (score >= 65.0) {
            return "Hire / Next Round";
        }
        if (score >= 50.0) {
            return "Hold";
        }
        return "Not Ready";
    }

    private String toTrend(double growthScore) {
        if (growthScore >= 58.0) {
            return "improving";
        }
        if (growthScore >= 45.0) {
            return "stable";
        }
        return "declining";
    }

    private static double accuracyStdDev(List<RishiCompileAttemptLog> attempts) {
        List<Double> values = attempts.stream()
                .map(RishiCompileAttemptLog::getAccuracyPct)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (values.size() < 2) {
            return 0.0;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = values.stream()
                .mapToDouble(value -> {
                    double delta = value - mean;
                    return delta * delta;
                })
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    private static double successRate(List<RishiCompileAttemptLog> attempts) {
        if (attempts.isEmpty()) {
            return 0.0;
        }
        long success = attempts.stream().filter(RishiCompileAttemptLog::isSuccess).count();
        return round2((success * 100.0) / attempts.size());
    }

    private static double normalizeAverage(List<Double> values) {
        return round2(values.stream().mapToDouble(HrInsightsService::normalizeSkill).average().orElse(0.0));
    }

    private static double normalizeSkill(Double raw) {
        if (raw == null) {
            return 0.0;
        }
        if (raw <= 5.0) {
            return clampScore(raw * 20.0);
        }
        return clampScore(raw);
    }

    private static double clampScore(Double value) {
        if (value == null) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static long safeLong(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static String safeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private static boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private record CandidateScores(
            double overallReadinessScore,
            double technicalScore,
            double processScore,
            double communicationScore,
            double consistencyScore,
            double growthScore,
            double confidenceScore,
            String trend,
            LocalDateTime lastActiveAt,
            double successRate30Days,
            List<String> riskFlags,
            List<String> positiveSignals) {
    }
}
