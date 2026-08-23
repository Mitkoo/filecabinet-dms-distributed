package com.filecabinet.user.repository;

import com.filecabinet.user.model.Role;
import com.filecabinet.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    @Query("SELECT u.id AS id, u.username AS username, u.email AS email, u.fullName AS fullName, u.role AS role FROM User u ORDER BY u.username")
    List<UserSummaryView> findAllSummaries();

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByRoleOrderByUsernameAsc(Role role);
}
