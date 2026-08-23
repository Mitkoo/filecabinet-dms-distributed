package com.filecabinet.user.repository;

import com.filecabinet.user.model.Role;

import java.util.UUID;

public interface UserSummaryView {

    UUID getId();

    String getUsername();

    String getEmail();

    String getFullName();

    Role getRole();
}
