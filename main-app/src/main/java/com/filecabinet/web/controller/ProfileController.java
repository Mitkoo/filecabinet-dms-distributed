package com.filecabinet.web.controller;

import com.filecabinet.user.model.Role;
import com.filecabinet.user.model.User;
import com.filecabinet.user.service.UserService;
import com.filecabinet.web.dto.ProfileForm;
import com.filecabinet.web.interceptor.SessionAuthInterceptor;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        User currentUser = currentUser(session);
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", toForm(currentUser));
        }
        model.addAttribute("currentUser", currentUser);
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("profileForm") ProfileForm form, BindingResult bindingResult,
                                 HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        if (bindingResult.hasErrors()) {
            model.addAttribute("currentUser", currentUser);
            return "profile";
        }

        userService.updateProfile(currentUser.getId(), form.getFullName(), form.getPhone(), form.getJobTitle(),
                form.getCompanyName(), form.getCompanyAddress());
        redirectAttributes.addFlashAttribute("successMessage", "Profile updated.");
        return "redirect:/profile";
    }

    @GetMapping("/users")
    public String users(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        if (currentUser.getRole() != Role.ADMIN) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only admins can manage users.");
            return "redirect:/documents";
        }

        model.addAttribute("users", userService.findAll());
        model.addAttribute("roles", Role.values());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activePage", "users");
        return "users";
    }

    @PostMapping("/users/{id}/role")
    public String updateRole(@PathVariable UUID id, @RequestParam Role role,
                              HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        if (currentUser.getRole() != Role.ADMIN) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only admins can manage users.");
            return "redirect:/documents";
        }
        if (currentUser.getId().equals(id)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can't change your own role.");
            return "redirect:/users";
        }

        User updated = userService.updateRole(id, role);
        redirectAttributes.addFlashAttribute("successMessage", updated.getUsername() + " is now " + role.name() + ".");
        return "redirect:/users";
    }

    private ProfileForm toForm(User user) {
        ProfileForm form = new ProfileForm();
        form.setFullName(user.getFullName());
        form.setPhone(user.getPhone());
        form.setJobTitle(user.getJobTitle());
        form.setCompanyName(user.getCompanyName());
        form.setCompanyAddress(user.getCompanyAddress());
        return form;
    }

    private User currentUser(HttpSession session) {
        UUID userId = (UUID) session.getAttribute(SessionAuthInterceptor.SESSION_USER_ID);
        return userService.findById(userId);
    }
}
