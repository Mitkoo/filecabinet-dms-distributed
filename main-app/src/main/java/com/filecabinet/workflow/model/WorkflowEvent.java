package com.filecabinet.workflow.model;

import com.filecabinet.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class WorkflowEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private ReviewWorkflow workflow;

    @ManyToOne
    private User actor;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private WorkflowEventType eventType;

    @Column(length = 500)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdOn;
}
