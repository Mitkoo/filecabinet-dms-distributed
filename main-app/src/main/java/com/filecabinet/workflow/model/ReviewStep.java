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
public class ReviewStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private ReviewWorkflow workflow;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private User reviewer;

    @Column(nullable = false)
    private int stepOrder;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StepStatus status;

    private LocalDateTime decidedOn;

    @Column(length = 500)
    private String comment;

    private LocalDateTime lastReminderSentOn;
}
