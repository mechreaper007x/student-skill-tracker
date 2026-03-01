package com.skilltracker.student_skill_tracker.util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class SecurityUtils {

    // Blacklist of dangerous keywords for RCE prevention
    private static final List<String> MALICIOUS_KEYWORDS = Arrays.asList(
        "Runtime.getRuntime", "ProcessBuilder", "System.exit", "java.io.File",
        "java.nio.file", "os.system", "subprocess", "eval(", "exec(", "socket",
        "java.net", "java.lang.reflect", "ClassLoader"
    );

    private static final Pattern PASSWORD_PATTERN =
        Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$");

    public static boolean containsMaliciousKeywords(String code) {
        if (code == null) return false;
        for (String keyword : MALICIOUS_KEYWORDS) {
            if (code.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidPassword(String password) {
        if (password == null) return false;
        return PASSWORD_PATTERN.matcher(password).matches();
    }
}
