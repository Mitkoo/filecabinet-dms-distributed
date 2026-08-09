package com.filecabinet.web.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileForm {

    @Size(max = 100, message = "Full name must be at most 100 characters.")
    private String fullName;

    @Size(max = 30, message = "Phone must be at most 30 characters.")
    private String phone;

    @Size(max = 100, message = "Job title must be at most 100 characters.")
    private String jobTitle;

    @Size(max = 100, message = "Company name must be at most 100 characters.")
    private String companyName;

    @Size(max = 250, message = "Company address must be at most 250 characters.")
    private String companyAddress;
}
