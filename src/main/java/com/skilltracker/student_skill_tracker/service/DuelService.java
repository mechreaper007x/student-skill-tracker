package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skilltracker.student_skill_tracker.model.DojoPuzzle;
import com.skilltracker.student_skill_tracker.model.DuelSession;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.DojoPuzzleRepository;
import com.skilltracker.student_skill_tracker.repository.DuelSessionRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;

@Service
public class DuelService {

    private static final Logger log = LoggerFactory.getLogger(DuelService.class);

    private final DojoPuzzleRepository puzzleRepository;
    private final DuelSessionRepository duelRepository;
    private final StudentRepository studentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ForgettingVelocityService forgettingVelocityService;

    private final Queue<String> waitingPlayers = new ConcurrentLinkedQueue<>();

    public DuelService(DojoPuzzleRepository puzzleRepository,
            DuelSessionRepository duelRepository,
            StudentRepository studentRepository,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            ForgettingVelocityService forgettingVelocityService) {
        this.puzzleRepository = puzzleRepository;
        this.duelRepository = duelRepository;
        this.studentRepository = studentRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.forgettingVelocityService = forgettingVelocityService;
    }

    @PostConstruct
    void ensureDuelSessionColumns() {
        try {
            // Backward compatibility for previously-created local DBs.
            jdbcTemplate.execute(
                    "ALTER TABLE duel_sessions ADD COLUMN IF NOT EXISTS current_round INTEGER DEFAULT 1");
            jdbcTemplate.execute(
                    "ALTER TABLE duel_sessions ADD COLUMN IF NOT EXISTS player1score INTEGER DEFAULT 0");
            jdbcTemplate.execute(
                    "ALTER TABLE duel_sessions ADD COLUMN IF NOT EXISTS player2score INTEGER DEFAULT 0");
            jdbcTemplate.execute(
                    "ALTER TABLE duel_sessions ADD COLUMN IF NOT EXISTS player1_highest_bloom_level INTEGER DEFAULT 1");
            jdbcTemplate.execute(
                    "ALTER TABLE duel_sessions ADD COLUMN IF NOT EXISTS player2_highest_bloom_level INTEGER DEFAULT 1");
            jdbcTemplate.execute(
                    "UPDATE duel_sessions SET current_round = COALESCE(current_round, 1), "
                            + "player1score = COALESCE(player1score, 0), "
                            + "player2score = COALESCE(player2score, 0), "
                            + "player1_highest_bloom_level = COALESCE(player1_highest_bloom_level, 1), "
                            + "player2_highest_bloom_level = COALESCE(player2_highest_bloom_level, 1)");
        } catch (Exception e) {
            log.warn("Could not auto-heal duel_sessions schema columns", e);
        }
    }

    public synchronized void joinLobby(String username) {
        if (waitingPlayers.contains(username)) {
            log.info("User {} is already in the lobby", username);
            return;
        }
        waitingPlayers.add(username);
        log.info("User {} joined the lobby. Total waiting: {}", username, waitingPlayers.size());
        tryMatchmaking();
    }

    public synchronized void leaveLobby(String username) {
        waitingPlayers.remove(username);
        log.info("User {} left the lobby.", username);
    }

    private void tryMatchmaking() {
        if (waitingPlayers.size() >= 2) {
            String player1 = waitingPlayers.poll();
            String player2 = waitingPlayers.poll();
            if (player1 == null || player2 == null)
                return;
            log.info("Matchmaking successful: {} vs {}", player1, player2);
            startDuel(player1, player2);
        }
    }

    private void startDuel(String player1, String player2) {
        Optional<DojoPuzzle> puzzleOpt = puzzleRepository.findFirstUsableByUsageCountOrderByCreatedAtAsc(0);

        DojoPuzzle puzzle;
        if (puzzleOpt.isPresent()) {
            puzzle = puzzleOpt.get();
            puzzle.setUsageCount(puzzle.getUsageCount() + 1);
            puzzleRepository.save(puzzle);
        } else {
            log.warn("No fresh usable DojoPuzzles available! Using fallback...");
            puzzleOpt = puzzleRepository.findRandomPuzzle();
            puzzle = puzzleOpt.orElseGet(() -> puzzleRepository.save(createBuiltInFallbackPuzzle()));
        }

        if (!hasUsableRounds(puzzle)) {
            log.warn("Selected puzzle {} has missing/invalid roundsJson. Using built-in fallback puzzle.", puzzle.getId());
            puzzle = puzzleRepository.save(createBuiltInFallbackPuzzle());
        }

        String sessionId = UUID.randomUUID().toString();
        DuelSession session = DuelSession.builder()
                .id(sessionId)
                .player1Username(player1)
                .player2Username(player2)
                .puzzle(puzzle)
                .status("LIVE")
                .currentRound(1)
                .player1Score(0)
                .player2Score(0)
                .player1HighestBloomLevel(1)
                .player2HighestBloomLevel(1)
                .startTime(LocalDateTime.now())
                .build();

        duelRepository.save(session);

        // Broadcast match start with round 1 data
        MatchStartPayload payload = new MatchStartPayload(sessionId, player1, player2, puzzle);
        messagingTemplate.convertAndSend("/topic/" + player1 + "/duel-start", payload);
        messagingTemplate.convertAndSend("/topic/" + player2 + "/duel-start", payload);
    }

    private boolean hasUsableRounds(DojoPuzzle puzzle) {
        if (puzzle == null || puzzle.getRoundsJson() == null || puzzle.getRoundsJson().isBlank()) {
            return false;
        }

        try {
            JsonNode rounds = objectMapper.readTree(puzzle.getRoundsJson());
            if (!rounds.isArray() || rounds.isEmpty()) {
                return false;
            }

            JsonNode firstRound = findRound(rounds, 1);
            return firstRound != null && !firstRound.path("type").asText("").isBlank();
        } catch (Exception e) {
            log.warn("Invalid roundsJson for puzzle {}: {}", puzzle.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Process a player's answer for the current round.
     * Validates correctness, updates scores, and broadcasts results.
     */
    public synchronized void submitRoundAnswer(String sessionId, String username, String answer) {
        Optional<DuelSession> sessionOpt = duelRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("Session {} not found for answer submission", sessionId);
            return;
        }

        DuelSession session = sessionOpt.get();
        if (!"LIVE".equals(session.getStatus())) {
            log.warn("Session {} is not live, ignoring answer", sessionId);
            return;
        }

        DojoPuzzle puzzle = session.getPuzzle();
        if (puzzle == null || puzzle.getRoundsJson() == null) {
            log.warn("No rounds data for session {}", sessionId);
            return;
        }

        int currentRound = session.getCurrentRound();
        boolean isCorrect = false;
        int pointsAwarded = 0;
        String roundType = "";

        try {
            JsonNode rounds = objectMapper.readTree(puzzle.getRoundsJson());
            JsonNode roundData = findRound(rounds, currentRound);
            if (roundData == null) {
                log.warn("Round {} not found in puzzle data", currentRound);
                return;
            }

            roundType = roundData.path("type").asText();

            switch (roundType) {
                case "MCQ" -> {
                    // Answer is the index of chosen option (e.g. "2")
                    // For MCQ with multiple questions, answer format: "0,2,1,3" (comma-separated
                    // indices)
                    String[] playerAnswers = answer.split(",");
                    JsonNode questions = roundData.path("questions");
                    int correct = 0;
                    for (int i = 0; i < questions.size() && i < playerAnswers.length; i++) {
                        int expected = questions.get(i).path("correctIndex").asInt(-1);
                        try {
                            int given = Integer.parseInt(playerAnswers[i].trim());
                            if (given == expected)
                                correct++;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    pointsAwarded = correct;
                    isCorrect = correct == questions.size();
                }
                case "MEMORY" -> {
                    // Answer is the number of pairs matched correctly (sent by frontend)
                    try {
                        pointsAwarded = Integer.parseInt(answer.trim());
                        isCorrect = pointsAwarded >= roundData.path("pairs").size();
                    } catch (NumberFormatException e) {
                        pointsAwarded = 0;
                    }
                }
                case "PUZZLE", "PROBLEM_SOLVING" -> {
                    String expectedAnswer = roundData.path("answer").asText("").trim().toLowerCase();
                    isCorrect = answer.trim().toLowerCase().equals(expectedAnswer);
                    pointsAwarded = isCorrect ? 1 : 0;
                }
                case "CODING" -> {
                    // Coding round scoring is handled separately via compiler execution
                    // Here we just accept the passed test case count
                    try {
                        pointsAwarded = Integer.parseInt(answer.trim());
                        isCorrect = pointsAwarded > 0;
                    } catch (NumberFormatException e) {
                        pointsAwarded = 0;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to validate round answer for session {}", sessionId, e);
            return;
        }

        // Update score
        boolean isPlayer1 = username.equals(session.getPlayer1Username());
        if (isPlayer1) {
            session.setPlayer1Score(session.getPlayer1Score() + pointsAwarded);
        } else {
            session.setPlayer2Score(session.getPlayer2Score() + pointsAwarded);
        }
        updateBloomProgress(session, isPlayer1, currentRound, roundType, pointsAwarded);

        duelRepository.save(session);

        // Broadcast the answer result to both players
        Map<String, Object> result = Map.of(
                "username", username,
                "round", currentRound,
                "pointsAwarded", pointsAwarded,
                "isCorrect", isCorrect,
                "player1Score", session.getPlayer1Score(),
                "player2Score", session.getPlayer2Score());

        messagingTemplate.convertAndSend("/topic/duel/" + sessionId + "/answerResult", result);
        log.info("Round {} answer from {}: {} points (correct: {})", currentRound, username, pointsAwarded, isCorrect);

        // Record Mastery Event for Forgetting Velocity
        final String finalRoundType = roundType;
        final boolean finalIsCorrect = isCorrect;
        studentRepository.findByEmailIgnoreCase(username).ifPresent(student -> {
            forgettingVelocityService.recordEventAndUpdateMastery(
                student,
                "duel-" + finalRoundType.toLowerCase(), // e.g., duel-mcq, duel-coding
                "DUEL_ROUND",
                60000, // Appx 60s for a duel round as a fallback
                finalIsCorrect ? 0 : 1, // Error count is 0 if correct, 1 if wrong
                finalIsCorrect,
                true, // High Pressure is TRUE in Arena
                "{\"round\":" + currentRound + "}"
            );
        });
    }

    /**
     * Advance the duel to the next round. Called when both players have submitted.
     */
    public synchronized void advanceRound(String sessionId) {
        Optional<DuelSession> sessionOpt = duelRepository.findById(sessionId);
        if (sessionOpt.isEmpty())
            return;

        DuelSession session = sessionOpt.get();
        int nextRound = session.getCurrentRound() + 1;

        if (nextRound > 5) {
            // Duel is over
            session.setStatus("FINISHED");
            String winner = session.getPlayer1Score() > session.getPlayer2Score()
                    ? session.getPlayer1Username()
                    : session.getPlayer2Score() > session.getPlayer1Score()
                            ? session.getPlayer2Username()
                            : "DRAW";
            session.setWinnerUsername(winner);
            duelRepository.save(session);
            persistHighestBloomLevels(session);
            updateStudentDuelStats(session, winner);

            Map<String, Object> endPayload = Map.of(
                    "status", "FINISHED",
                    "winner", winner,
                    "player1Score", session.getPlayer1Score(),
                    "player2Score", session.getPlayer2Score(),
                    "player1HighestBloomLevel", session.getPlayer1HighestBloomLevel(),
                    "player2HighestBloomLevel", session.getPlayer2HighestBloomLevel());
            messagingTemplate.convertAndSend("/topic/duel/" + sessionId + "/roundAdvance", endPayload);
            log.info("Duel {} finished. Winner: {}", sessionId, winner);
        } else {
            session.setCurrentRound(nextRound);
            duelRepository.save(session);

            Map<String, Object> advancePayload = Map.of(
                    "status", "NEXT_ROUND",
                    "currentRound", nextRound,
                    "player1Score", session.getPlayer1Score(),
                    "player2Score", session.getPlayer2Score());
            messagingTemplate.convertAndSend("/topic/duel/" + sessionId + "/roundAdvance", advancePayload);
            log.info("Duel {} advancing to round {}", sessionId, nextRound);
        }
    }

    private void updateBloomProgress(DuelSession session, boolean isPlayer1, int roundNumber, String roundType, int pointsAwarded) {
        if (pointsAwarded <= 0) {
            return;
        }

        int bloomLevel = bloomLevelForRound(roundNumber, roundType);
        if (bloomLevel <= 0) {
            return;
        }

        if (isPlayer1) {
            session.setPlayer1HighestBloomLevel(Math.max(session.getPlayer1HighestBloomLevel(), bloomLevel));
        } else {
            session.setPlayer2HighestBloomLevel(Math.max(session.getPlayer2HighestBloomLevel(), bloomLevel));
        }
    }

    private int bloomLevelForRound(int roundNumber, String roundType) {
        if ("CODING".equalsIgnoreCase(roundType)) {
            return 6;
        }

        return switch (roundNumber) {
            case 1 -> 1; // Remember
            case 2 -> 2; // Understand
            case 3 -> 3; // Apply
            case 4 -> 4; // Analyze
            case 5 -> 5; // Evaluate
            default -> 1;
        };
    }

    private void persistHighestBloomLevels(DuelSession session) {
        persistBloomForUsername(session.getPlayer1Username(), session.getPlayer1HighestBloomLevel());
        persistBloomForUsername(session.getPlayer2Username(), session.getPlayer2HighestBloomLevel());
    }

    private void persistBloomForUsername(String username, int duelBloomLevel) {
        if (username == null || username.isBlank()) {
            return;
        }

        Optional<Student> studentOpt = studentRepository.findByEmailIgnoreCase(username);
        if (studentOpt.isEmpty()) {
            log.debug("No student profile found for duel user {}", username);
            return;
        }

        Student student = studentOpt.get();
        int currentHighest = student.getHighestBloomLevel() == null ? 1 : student.getHighestBloomLevel();
        int updatedHighest = Math.max(currentHighest, duelBloomLevel);
        if (updatedHighest != currentHighest) {
            student.setHighestBloomLevel(updatedHighest);
            studentRepository.save(student);
        }
    }

    private void updateStudentDuelStats(DuelSession session, String winner) {
        updateStatsForUser(session.getPlayer1Username(), winner.equals(session.getPlayer1Username()), "DRAW".equals(winner));
        updateStatsForUser(session.getPlayer2Username(), winner.equals(session.getPlayer2Username()), "DRAW".equals(winner));
    }

    private void updateStatsForUser(String username, boolean isWinner, boolean isDraw) {
        studentRepository.findByEmailIgnoreCase(username).ifPresent(student -> {
            int xpGained = isDraw ? 250 : (isWinner ? 500 : 100);
            student.setXp((student.getXp() == null ? 0 : student.getXp()) + xpGained);

            if (!isDraw) {
                if (isWinner) {
                    student.setDuelWins((student.getDuelWins() == null ? 0 : student.getDuelWins()) + 1);
                } else {
                    student.setDuelLosses((student.getDuelLosses() == null ? 0 : student.getDuelLosses()) + 1);
                }
            }

            student.setLastDuelAt(LocalDateTime.now());

            // Simple Level up logic (1000 XP per level)
            while (student.getXp() >= 1000) {
                student.setXp(student.getXp() - 1000);
                student.setLevel((student.getLevel() == null ? 1 : student.getLevel()) + 1);
            }

            studentRepository.save(student);
            log.info("Updated stats for {}: +{} XP, New Level: {}", username, xpGained, student.getLevel());
        });
    }

    private JsonNode findRound(JsonNode rounds, int roundNumber) {
        for (JsonNode round : rounds) {
            if (round.path("round").asInt() == roundNumber) {
                return round;
            }
        }
        return null;
    }

    private DojoPuzzle createBuiltInFallbackPuzzle() {
        String fallbackRounds = """
                [
                  {"round":1,"type":"MCQ","title":"Quick Fire Quiz","timeLimitSeconds":120,"questions":[
                    {"question":"What is the time complexity of accessing an element in an array by index?","options":["O(1)","O(n)","O(log n)","O(n²)"],"correctIndex":0},
                    {"question":"Which data structure uses FIFO ordering?","options":["Stack","Queue","Tree","Graph"],"correctIndex":1},
                    {"question":"What does HTML stand for?","options":["Hyper Text Markup Language","High Tech Modern Language","Hyper Transfer Markup Language","Home Tool Markup Language"],"correctIndex":0},
                    {"question":"Which sorting algorithm has the best average-case time complexity?","options":["Bubble Sort","Selection Sort","Merge Sort","Insertion Sort"],"correctIndex":2}
                  ]},
                  {"round":2,"type":"MEMORY","title":"Neural Match","timeLimitSeconds":120,"gridSize":4,"pairs":[
                    {"id":0,"content":"HashMap","match":"O(1) lookup"},
                    {"id":1,"content":"BFS","match":"Queue"},
                    {"id":2,"content":"DFS","match":"Stack"},
                    {"id":3,"content":"Binary Search","match":"O(log n)"},
                    {"id":4,"content":"Merge Sort","match":"O(n log n)"},
                    {"id":5,"content":"Linked List","match":"O(n) access"},
                    {"id":6,"content":"Heap","match":"Priority Queue"},
                    {"id":7,"content":"Array","match":"O(1) access"}
                  ]},
                  {"round":3,"type":"PUZZLE","title":"Pattern Decode","timeLimitSeconds":180,"puzzleHtml":"<p>Find the next number in the sequence: <b>2, 6, 12, 20, 30, ?</b></p><p>Hint: Look at the differences between consecutive numbers.</p>","answer":"42","hints":["Differences: 4, 6, 8, 10, ?","The differences increase by 2 each time"]},
                  {"round":4,"type":"PROBLEM_SOLVING","title":"Logic Gate","timeLimitSeconds":180,"problemHtml":"<p>A farmer has <b>17 sheep</b>. All but <b>9</b> run away. How many sheep does the farmer have left?</p>","answer":"9","solutionExplanation":"'All but 9' means 9 remain. The answer is 9, not 17-9=8."},
                  {"round":5,"type":"CODING","title":"Echo Sum","timeLimitSeconds":600,"descriptionHtml":"<p><b>Task:</b> Given an integer array <code>nums</code>, return the sum of all values.</p><p><b>Example:</b> nums = [1, 2, 3] → 6</p>","starterCode":{"java":"class Solution {\\n    public int solve(int[] nums) {\\n        // TODO: return sum of nums\\n        return 0;\\n    }\\n}","python":"def solve(nums):\\n    # TODO: return sum of nums\\n    return 0","cpp":"#include <vector>\\nusing namespace std;\\n\\nint solve(vector<int> nums) {\\n    // TODO: return sum of nums\\n    return 0;\\n}","javascript":"function solve(nums) {\\n  // TODO: return sum of nums\\n  return 0;\\n}"},"hiddenTestCases":[{"input":"[1,2,3]","expectedOutput":"6"},{"input":"[5]","expectedOutput":"5"},{"input":"[-1,1,0]","expectedOutput":"0"}]}
                ]
                """;

        return DojoPuzzle.builder()
                .title("Starter Gauntlet")
                .descriptionHtml("<p>A built-in 5-round duel for when no AI-generated puzzles are available.</p>")
                .difficulty("Mixed")
                .roundsJson(fallbackRounds.trim())
                .starterCodeJava("class Solution {\n    public int solve(int[] nums) {\n        return 0;\n    }\n}")
                .starterCodePython("def solve(nums):\n    return 0\n")
                .starterCodeCpp(
                        "#include <vector>\nusing namespace std;\n\nint solve(vector<int> nums) {\n    return 0;\n}\n")
                .starterCodeJavascript("function solve(nums) {\n  return 0;\n}\n")
                .hiddenTestCasesJson(
                        "[{\"input\":\"[1,2,3]\",\"expectedOutput\":\"6\"},{\"input\":\"[5]\",\"expectedOutput\":\"5\"},{\"input\":\"[-1,1,0]\",\"expectedOutput\":\"0\"}]")
                .isGeneratedByAi(false)
                .usageCount(1)
                .build();
    }

    // DTO for broadcasting match start
    public record MatchStartPayload(String sessionId, String player1, String player2, DojoPuzzle puzzle) {
    }
}
