package com.skilltracker.student_skill_tracker.service;

import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private StudentRepository studentRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        System.out.println("Attempting to load user by email: " + email);
        Student s = studentRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        String roles = (s.getRoles() == null || s.getRoles().isBlank()) ? "ROLE_USER" : s.getRoles();

        return new org.springframework.security.core.userdetails.User(
            s.getEmail(),
            s.getPassword(), // <-- do NOT encode here
            AuthorityUtils.commaSeparatedStringToAuthorityList(roles)
        );
    }
}
