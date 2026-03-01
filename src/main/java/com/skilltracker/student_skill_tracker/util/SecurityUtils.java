package com.skilltracker.student_skill_tracker.util;

import java.util.regex.Pattern;

public class SecurityUtils {

    private static final Pattern PASSWORD_PATTERN =
        Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$");

    public static boolean isValidPassword(String password) {
        if (password == null) return false;
        return PASSWORD_PATTERN.matcher(password).matches();
    }
}
