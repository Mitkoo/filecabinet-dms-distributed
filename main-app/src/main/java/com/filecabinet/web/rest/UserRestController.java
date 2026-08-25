package com.filecabinet.web.rest;

import com.filecabinet.shared.exception.ServiceExceptions.InvalidStateException;
import com.filecabinet.shared.security.AppUserDetails;
import com.filecabinet.user.model.Role;
import com.filecabinet.user.model.User;
import com.filecabinet.user.service.UserService;
import com.filecabinet.web.rest.dto.RoleUpdateRequest;
import com.filecabinet.web.rest.dto.UserSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserRestController {

    private final UserService userService;

    @GetMapping
    public List<UserSummaryResponse> list() {
        return userService.findAllSummaries().stream()
                .map(view -> new UserSummaryResponse(view.getId(), view.getUsername(),
                        view.getEmail(), view.getRole().name(), view.getFullName()))
                .toList();
    }

    @PutMapping("/{id}/role")
    public UserSummaryResponse updateRole(@AuthenticationPrincipal AppUserDetails principal,
                                          @PathVariable UUID id,
                                          @Valid @RequestBody RoleUpdateRequest request) {
        if (principal.getId().equals(id)) {
            throw new InvalidStateException("You cannot change your own role.");
        }
        Role role = parseRole(request.role());
        User user = userService.updateRole(id, role);
        log.info("Changed role of {} to {}", user.getUsername(), role);
        return toResponse(user);
    }

    private Role parseRole(String value) {
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidStateException("Unknown role: " + value);
        }
    }

    private UserSummaryResponse toResponse(User user) {
        return new UserSummaryResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole().name(), user.getFullName());
    }
}
