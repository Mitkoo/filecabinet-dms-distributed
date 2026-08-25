package com.filecabinet.user.service;

import com.filecabinet.shared.exception.ServiceExceptions.DuplicateException;
import com.filecabinet.shared.exception.ServiceExceptions.NotFoundException;
import com.filecabinet.user.model.Role;
import com.filecabinet.user.model.User;
import com.filecabinet.user.repository.UserRepository;
import com.filecabinet.user.repository.UserSummaryView;
import com.filecabinet.web.rest.dto.ReviewerOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService service;

    private User user(String username, Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email(username + "@filecabinet.local")
                .passwordHash("hashed")
                .role(role)
                .build();
    }

    @Test
    void registerStoresHashedPasswordAsClerk() {
        when(userRepository.existsByUsername("jane")).thenReturn(false);
        when(userRepository.existsByEmail("jane@x.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = service.register("jane", "jane@x.com", "secret");

        assertThat(created.getRole()).isEqualTo(Role.CLERK);
        assertThat(created.getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("jane")).thenReturn(true);
        assertThatThrownBy(() -> service.register("jane", "jane@x.com", "secret"))
                .isInstanceOf(DuplicateException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByUsername("jane")).thenReturn(false);
        when(userRepository.existsByEmail("jane@x.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register("jane", "jane@x.com", "secret"))
                .isInstanceOf(DuplicateException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginReturnsUserWhenPasswordMatches() {
        User jane = user("jane", Role.ADMIN);
        when(userRepository.findByUsername("jane")).thenReturn(Optional.of(jane));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);

        assertThat(service.login("jane", "secret")).contains(jane);
    }

    @Test
    void loginReturnsEmptyWhenPasswordWrong() {
        User jane = user("jane", Role.ADMIN);
        when(userRepository.findByUsername("jane")).thenReturn(Optional.of(jane));
        when(passwordEncoder.matches("bad", "hashed")).thenReturn(false);

        assertThat(service.login("jane", "bad")).isEmpty();
    }

    @Test
    void loginReturnsEmptyWhenUserUnknown() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThat(service.login("ghost", "secret")).isEmpty();
    }

    @Test
    void getOrCreateDemoReturnsExistingDemo() {
        User demo = user("demo", Role.DEMO);
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(demo));

        assertThat(service.getOrCreateDemo()).isSameAs(demo);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getOrCreateDemoCreatesWhenMissing() {
        when(userRepository.findByUsername("demo")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User demo = service.getOrCreateDemo();

        assertThat(demo.getRole()).isEqualTo(Role.DEMO);
        assertThat(demo.getUsername()).isEqualTo("demo");
    }

    @Test
    void findByIdReturnsUser() {
        User jane = user("jane", Role.CLERK);
        when(userRepository.findById(jane.getId())).thenReturn(Optional.of(jane));
        assertThat(service.findById(jane.getId())).isSameAs(jane);
    }

    @Test
    void findByIdUnknownThrows() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void findByRoleDelegatesToRepository() {
        User manager = user("mgr", Role.MANAGER);
        when(userRepository.findByRoleOrderByUsernameAsc(Role.MANAGER)).thenReturn(List.of(manager));
        assertThat(service.findByRole(Role.MANAGER)).containsExactly(manager);
    }

    @Test
    void findAllDelegatesToRepository() {
        User jane = user("jane", Role.CLERK);
        when(userRepository.findAll()).thenReturn(List.of(jane));
        assertThat(service.findAll()).containsExactly(jane);
    }

    @Test
    void updateProfileSetsFields() {
        User jane = user("jane", Role.CLERK);
        when(userRepository.findById(jane.getId())).thenReturn(Optional.of(jane));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = service.updateProfile(jane.getId(), "Jane Doe", "555", "Clerk", "Acme", "1 Road");

        assertThat(updated.getFullName()).isEqualTo("Jane Doe");
        assertThat(updated.getCompanyName()).isEqualTo("Acme");
    }

    @Test
    void updateRoleChangesRole() {
        User jane = user("jane", Role.CLERK);
        when(userRepository.findById(jane.getId())).thenReturn(Optional.of(jane));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = service.updateRole(jane.getId(), Role.ADMIN);

        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void getReviewerOptionsMapsSummaries() {
        UserSummaryView view = summaryView(UUID.randomUUID(), "jane", Role.ADMIN);
        when(userRepository.findAllSummaries()).thenReturn(List.of(view));

        List<ReviewerOption> options = service.getReviewerOptions();

        assertThat(options).hasSize(1);
        assertThat(options.get(0).username()).isEqualTo("jane");
        assertThat(options.get(0).role()).isEqualTo("ADMIN");
    }

    @Test
    void findAllSummariesDelegatesToRepository() {
        UserSummaryView view = summaryView(UUID.randomUUID(), "jane", Role.CLERK);
        when(userRepository.findAllSummaries()).thenReturn(List.of(view));
        assertThat(service.findAllSummaries()).containsExactly(view);
    }

    private static UserSummaryView summaryView(UUID id, String username, Role role) {
        return new UserSummaryView() {
            public UUID getId() {
                return id;
            }

            public String getUsername() {
                return username;
            }

            public String getEmail() {
                return username + "@filecabinet.local";
            }

            public String getFullName() {
                return username;
            }

            public Role getRole() {
                return role;
            }
        };
    }
}
