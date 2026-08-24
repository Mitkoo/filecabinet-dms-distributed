package com.filecabinet.user.service;

import com.filecabinet.shared.exception.ServiceExceptions;
import com.filecabinet.user.model.Role;
import com.filecabinet.user.model.User;
import com.filecabinet.user.repository.UserRepository;
import com.filecabinet.user.repository.UserSummaryView;
import com.filecabinet.web.rest.dto.ReviewerOption;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserSummaryView> findAllSummaries() {
        return userRepository.findAllSummaries();
    }

    @Cacheable("reviewers")
    @Transactional(readOnly = true)
    public List<ReviewerOption> getReviewerOptions() {
        return userRepository.findAllSummaries().stream()
                .map(view -> new ReviewerOption(view.getId(), view.getUsername(), view.getRole().name()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    @CacheEvict(value = "reviewers", allEntries = true)
    public User register(String username, String email, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new ServiceExceptions.DuplicateException("Username already taken: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new ServiceExceptions.DuplicateException("Email already registered: " + email);
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(Role.CLERK)
                .createdOn(LocalDateTime.now())
                .build();
        return userRepository.save(user);
    }

    public Optional<User> login(String username, String rawPassword) {
        return userRepository.findByUsername(username)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()));
    }

    @CacheEvict(value = "reviewers", allEntries = true)
    public User getOrCreateDemo() {
        return userRepository.findByUsername("demo").orElseGet(() -> userRepository.save(User.builder()
                .username("demo")
                .email("demo@filecabinet.local")
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.DEMO)
                .createdOn(LocalDateTime.now())
                .build()));
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public List<User> findByRole(Role role) {
        return userRepository.findByRoleOrderByUsernameAsc(role);
    }

    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ServiceExceptions.NotFoundException("User not found: " + id));
    }

    public User updateProfile(UUID id, String fullName, String phone, String jobTitle, String companyName, String companyAddress) {
        User user = findById(id);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setJobTitle(jobTitle);
        user.setCompanyName(companyName);
        user.setCompanyAddress(companyAddress);
        return userRepository.save(user);
    }

    @CacheEvict(value = "reviewers", allEntries = true)
    @PreAuthorize("hasRole('ADMIN')")
    public User updateRole(UUID id, Role role) {
        User user = findById(id);
        user.setRole(role);
        return userRepository.save(user);
    }

    public void resetPassword(String username, String email, String newRawPassword) {
        User user = userRepository.findByUsername(username)
                .filter(u -> u.getEmail().equalsIgnoreCase(email))
                .orElseThrow(() -> new ServiceExceptions.InvalidStateException("No account matches that username and email."));
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
    }
}
