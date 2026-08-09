package com.filecabinet.web.controller;

import com.filecabinet.shared.exception.ServiceExceptions;
import com.filecabinet.user.model.User;
import com.filecabinet.user.service.UserService;
import com.filecabinet.web.dto.ForgotPasswordForm;
import com.filecabinet.web.dto.LoginForm;
import com.filecabinet.web.dto.RegisterForm;
import com.filecabinet.web.interceptor.SessionAuthInterceptor;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form, BindingResult bindingResult, Model model) {
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Passwords must match.");
        }
        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            userService.register(form.getUsername(), form.getEmail(), form.getPassword());
        } catch (ServiceExceptions.DuplicateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "register";
        }

        return "redirect:/login?registered";
    }

    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String registered, @RequestParam(required = false) String reset,
                             Model model) {
        if (!model.containsAttribute("loginForm")) {
            model.addAttribute("loginForm", new LoginForm());
        }
        if (registered != null) {
            model.addAttribute("registered", "Account created. You can now sign in.");
        }
        if (reset != null) {
            model.addAttribute("registered", "Password updated. You can now sign in.");
        }
        return "login";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm(Model model) {
        if (!model.containsAttribute("forgotPasswordForm")) {
            model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        }
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form, BindingResult bindingResult,
                                  Model model) {
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Passwords must match.");
        }
        if (bindingResult.hasErrors()) {
            return "forgot-password";
        }

        try {
            userService.resetPassword(form.getUsername(), form.getEmail(), form.getNewPassword());
        } catch (ServiceExceptions.InvalidStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "forgot-password";
        }

        return "redirect:/login?reset";
    }

    @PostMapping("/login")
    public String login(@ModelAttribute("loginForm") LoginForm form, HttpSession session, Model model) {
        Optional<User> user = userService.login(form.getUsername(), form.getPassword());
        if (user.isEmpty()) {
            model.addAttribute("loginError", "Invalid username or password.");
            return "login";
        }
        session.setAttribute(SessionAuthInterceptor.SESSION_USER_ID, user.get().getId());
        return "redirect:/documents";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
