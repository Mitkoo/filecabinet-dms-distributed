package com.filecabinet.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForgotPasswordForm {

    @NotBlank(message = "Username is required.")
    private String username;

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    private String email;

    @NotBlank(message = "New password is required.")
    @Size(min = 8, message = "Password must be at least 8 characters.")
    private String newPassword;

    @NotBlank(message = "Please confirm your new password.")
    private String confirmPassword;
}
