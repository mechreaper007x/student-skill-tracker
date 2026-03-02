package com.skilltracker.student_skill_tracker.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.skilltracker.student_skill_tracker.model.RishiCompileAttemptLog;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.RishiCompileAttemptLogRepository;

/**
 * Diffs source code snapshots between consecutive compile attempts.
 * Produces a compact summary for Rishi's agent context.
 */
@Service
public class CodeDiffService {

    private final RishiCompileAttemptLogRepository attemptLogRepository;

    public CodeDiffService(RishiCompileAttemptLogRepository attemptLogRepository) {
        this.attemptLogRepository = attemptLogRepository;
    }

    /**
     * Generates a compact diff summary of the student's last N compile attempts.
     * Returns empty string if no snapshots are available.
     */
    public String generateDiffSummary(Student student) {
        List<RishiCompileAttemptLog> recent = attemptLogRepository
                .findTop5BySessionStudentOrderByAttemptedAtDesc(student);

        if (recent.size() < 2)
            return "";

        // Reverse to chronological order (oldest first)
        List<RishiCompileAttemptLog> chronological = new ArrayList<>(recent);
        java.util.Collections.reverse(chronological);

        StringBuilder sb = new StringBuilder("CodeDiffs:\n");
        int diffCount = 0;

        for (int i = 0; i < chronological.size() - 1 && diffCount < 3; i++) {
            String prev = chronological.get(i).getSourceCodeSnapshot();
            String curr = chronological.get(i + 1).getSourceCodeSnapshot();
            boolean prevSuccess = chronological.get(i).isSuccess();
            boolean currSuccess = chronological.get(i + 1).isSuccess();

            if (prev == null || curr == null)
                continue;

            String diff = computeCompactDiff(prev, curr, i + 1, i + 2, prevSuccess, currSuccess);
            if (!diff.isBlank()) {
                sb.append(diff).append("\n");
                diffCount++;
            }
        }

        return diffCount > 0 ? sb.toString() : "";
    }

    private String computeCompactDiff(String before, String after,
            int runBefore, int runAfter,
            boolean prevSuccess, boolean currSuccess) {
        String[] oldLines = before.split("\n", -1);
        String[] newLines = after.split("\n", -1);

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();

        int maxLen = Math.max(oldLines.length, newLines.length);

        for (int i = 0; i < maxLen; i++) {
            String oldLine = i < oldLines.length ? oldLines[i].trim() : null;
            String newLine = i < newLines.length ? newLines[i].trim() : null;

            if (oldLine == null && newLine != null && !newLine.isBlank()) {
                added.add("L" + (i + 1) + ": +" + truncate(newLine, 60));
            } else if (newLine == null && oldLine != null && !oldLine.isBlank()) {
                removed.add("L" + (i + 1) + ": -" + truncate(oldLine, 60));
            } else if (oldLine != null && newLine != null && !oldLine.equals(newLine)) {
                changed.add("L" + (i + 1) + ": " + truncate(oldLine, 30) + " → " + truncate(newLine, 30));
            }
        }

        if (added.isEmpty() && removed.isEmpty() && changed.isEmpty())
            return "";

        String statusChange = (prevSuccess ? "✓" : "✗") + "→" + (currSuccess ? "✓" : "✗");
        StringBuilder result = new StringBuilder("Run" + runBefore + "→" + runAfter + " [" + statusChange + "]: ");

        int total = added.size() + removed.size() + changed.size();
        if (total > 5) {
            result.append("+").append(added.size()).append(" -").append(removed.size())
                    .append(" ~").append(changed.size()).append(" lines");
            // Show top 2 changes only
            changed.stream().limit(2).forEach(c -> result.append("; ").append(c));
        } else {
            List<String> all = new ArrayList<>();
            all.addAll(added);
            all.addAll(removed);
            all.addAll(changed);
            result.append(String.join("; ", all));
        }

        return result.toString();
    }

    private String truncate(String s, int max) {
        if (s == null)
            return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
