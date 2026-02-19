package com.skilltracker.student_skill_tracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    @Autowired
    private StudentRepository studentRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        System.out.println("DEBUG: UserDetailsServiceImpl loading user: " + email);
        log.debug("Attempting to load user by email: {}", email);
        Student s = studentRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    System.out.println("DEBUG: User not found in DB: " + email);
                    return new UsernameNotFoundException("User not found: " + email);
                });

        System.out.println("DEBUG: User found! Roles: " + s.getRoles());

        String roles = (s.getRoles() == null || s.getRoles().isBlank()) ? "ROLE_USER" : s.getRoles();

        return new org.springframework.security.core.userdetails.User(
                s.getEmail(),
                s.getPassword(), // <-- do NOT encode here
                AuthorityUtils.commaSeparatedStringToAuthorityList(roles));
    }
}
