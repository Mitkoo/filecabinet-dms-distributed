package com.filecabinet.web.rest;

import com.filecabinet.shared.security.AppUserDetails;
import com.filecabinet.shared.security.JwtService;
import com.filecabinet.user.service.UserService;
import com.filecabinet.web.rest.dto.AuthResponse;
import com.filecabinet.web.rest.dto.LoginRequest;
import com.filecabinet.web.rest.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRestController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        AppUserDetails user = (AppUserDetails) authentication.getPrincipal();
        log.info("User {} logged in", user.getUsername());
        return new AuthResponse(jwtService.generateToken(user), user.getUsername(), user.getRole().name());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        var user = userService.register(request.username(), request.email(), request.password());
        log.info("Registered new user {}", user.getUsername());
        AppUserDetails principal = new AppUserDetails(user.getId(), user.getUsername(), user.getPasswordHash(), user.getRole());
        return new AuthResponse(jwtService.generateToken(principal), principal.getUsername(), principal.getRole().name());
    }
}
