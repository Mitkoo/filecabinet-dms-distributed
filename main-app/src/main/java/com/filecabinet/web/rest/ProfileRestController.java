package com.filecabinet.web.rest;

import com.filecabinet.shared.security.AppUserDetails;
import com.filecabinet.user.model.User;
import com.filecabinet.user.service.UserService;
import com.filecabinet.web.rest.dto.ProfileResponse;
import com.filecabinet.web.rest.dto.ProfileUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileRestController {

    private final UserService userService;

    @GetMapping("/me")
    public ProfileResponse me(@AuthenticationPrincipal AppUserDetails principal) {
        return toResponse(userService.findById(principal.getId()));
    }

    @PutMapping
    public ProfileResponse update(@AuthenticationPrincipal AppUserDetails principal,
                                  @Valid @RequestBody ProfileUpdateRequest request) {
        User user = userService.updateProfile(principal.getId(), request.fullName(), request.phone(),
                request.jobTitle(), request.companyName(), request.companyAddress());
        return toResponse(user);
    }

    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name(),
                user.getFullName(), user.getPhone(), user.getJobTitle(), user.getCompanyName(), user.getCompanyAddress());
    }
}
