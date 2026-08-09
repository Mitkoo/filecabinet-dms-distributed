package com.filecabinet.workflow.service;

import com.filecabinet.document.model.Document;
import com.filecabinet.document.model.DocumentStatus;
import com.filecabinet.document.model.DocumentType;
import com.filecabinet.document.service.DocumentService;
import com.filecabinet.shared.exception.ServiceExceptions;
import com.filecabinet.user.model.Role;
import com.filecabinet.user.model.User;
import com.filecabinet.user.service.UserService;
import com.filecabinet.workflow.model.ReviewStep;
import com.filecabinet.workflow.model.ReviewWorkflow;
import com.filecabinet.workflow.model.StepStatus;
import com.filecabinet.workflow.model.WorkflowEvent;
import com.filecabinet.workflow.model.WorkflowEventType;
import com.filecabinet.workflow.model.WorkflowRead;
import com.filecabinet.workflow.model.WorkflowStatus;
import com.filecabinet.workflow.repository.ReviewStepRepository;
import com.filecabinet.workflow.repository.ReviewWorkflowRepository;
import com.filecabinet.workflow.repository.WorkflowEventRepository;
import com.filecabinet.workflow.repository.WorkflowReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private static final Duration REMINDER_COOLDOWN = Duration.ofHours(4);

    private final ReviewWorkflowRepository reviewWorkflowRepository;
    private final ReviewStepRepository reviewStepRepository;
    private final WorkflowEventRepository workflowEventRepository;
    private final WorkflowReadRepository workflowReadRepository;
    private final DocumentService documentService;
    private final UserService userService;

    public ReviewWorkflow startWorkflow(UUID documentId, UUID initiatorId, List<UUID> reviewerIds, String message) {
        Document document = documentService.findById(documentId);
        if (document.getStatus() != DocumentStatus.STRUCTURED) {
            throw new ServiceExceptions.InvalidStateException("Only structured documents can be sent for review.");
        }
        if (reviewerIds == null || reviewerIds.isEmpty()) {
            throw new ServiceExceptions.InvalidStateException("Select at least one reviewer.");
        }
        if (reviewerIds.stream().distinct().count() != reviewerIds.size()) {
            throw new ServiceExceptions.InvalidStateException("Each reviewer can only be added once.");
        }
        User initiator = userService.findById(initiatorId);
        List<User> reviewers = reviewerIds.stream().map(userService::findById).toList();

        if (document.getDocumentType() == DocumentType.INVOICE) {
            validateInvoicePipeline(reviewers);
        }

        ReviewWorkflow workflow = ReviewWorkflow.builder()
                .document(document)
                .initiator(initiator)
                .status(WorkflowStatus.IN_PROGRESS)
                .message(message)
                .createdOn(LocalDateTime.now())
                .build();
        workflow = reviewWorkflowRepository.save(workflow);

        int order = 1;
        for (User reviewer : reviewers) {
            ReviewStep step = ReviewStep.builder()
                    .workflow(workflow)
                    .reviewer(reviewer)
                    .stepOrder(order++)
                    .status(StepStatus.PENDING)
                    .build();
            reviewStepRepository.save(step);
        }

        documentService.updateStatus(documentId, DocumentStatus.IN_REVIEW);
        logEvent(workflow, initiator, WorkflowEventType.STARTED, message);
        return workflow;
    }

    private void validateInvoicePipeline(List<User> reviewers) {
        if (reviewers.size() != 3
                || reviewers.get(0).getRole() != Role.BUYER
                || reviewers.get(1).getRole() != Role.MANAGER
                || reviewers.get(2).getRole() != Role.ACCOUNTANT) {
            throw new ServiceExceptions.InvalidStateException(
                    "Invoices must be reviewed by a buyer, then a manager, then an accountant, in that order.");
        }
    }

    public void decide(UUID workflowId, UUID stepId, UUID reviewerId, boolean approve, String comment) {
        ReviewWorkflow workflow = findById(workflowId);
        if (workflow.getStatus() != WorkflowStatus.IN_PROGRESS) {
            throw new ServiceExceptions.InvalidStateException("This workflow is no longer in progress.");
        }

        ReviewStep step = reviewStepRepository.findById(stepId)
                .orElseThrow(() -> new ServiceExceptions.NotFoundException("Review step not found: " + stepId));
        if (!step.getWorkflow().getId().equals(workflowId)) {
            throw new ServiceExceptions.NotFoundException("Review step not found: " + stepId);
        }

        ReviewStep currentStep = getCurrentStep(workflow)
                .orElseThrow(() -> new ServiceExceptions.InvalidStateException("This workflow has no pending step."));
        if (!currentStep.getId().equals(stepId)) {
            throw new ServiceExceptions.InvalidStateException("It is not this reviewer's turn yet.");
        }
        if (!step.getReviewer().getId().equals(reviewerId)) {
            throw new ServiceExceptions.InvalidStateException("Only the assigned reviewer can decide this step.");
        }

        step.setStatus(approve ? StepStatus.APPROVED : StepStatus.REJECTED);
        step.setDecidedOn(LocalDateTime.now());
        step.setComment(comment);
        reviewStepRepository.save(step);

        logEvent(workflow, step.getReviewer(), approve ? WorkflowEventType.STEP_APPROVED : WorkflowEventType.STEP_REJECTED, comment);

        if (!approve) {
            workflow.setStatus(WorkflowStatus.REJECTED);
            workflow.setCompletedOn(LocalDateTime.now());
            reviewWorkflowRepository.save(workflow);
            skipRemainingSteps(workflow);
            documentService.updateStatus(workflow.getDocument().getId(), DocumentStatus.REJECTED);
            logEvent(workflow, null, WorkflowEventType.COMPLETED, "Workflow rejected.");
        } else if (getCurrentStep(workflow).isEmpty()) {
            workflow.setStatus(WorkflowStatus.APPROVED);
            workflow.setCompletedOn(LocalDateTime.now());
            reviewWorkflowRepository.save(workflow);
            documentService.updateStatus(workflow.getDocument().getId(), DocumentStatus.APPROVED);
            logEvent(workflow, null, WorkflowEventType.COMPLETED, "Workflow approved.");
        }
    }

    public void addComment(UUID workflowId, UUID userId, String message) {
        ReviewWorkflow workflow = findById(workflowId);
        User user = userService.findById(userId);
        boolean participant = workflow.getInitiator().getId().equals(userId)
                || reviewStepRepository.findByWorkflowIdOrderByStepOrderAsc(workflowId).stream()
                    .anyMatch(s -> s.getReviewer().getId().equals(userId));
        if (!participant) {
            throw new ServiceExceptions.InvalidStateException("Only the initiator or an assigned reviewer can comment.");
        }
        if (workflow.getStatus() != WorkflowStatus.IN_PROGRESS) {
            throw new ServiceExceptions.InvalidStateException("This workflow is no longer in progress.");
        }
        logEvent(workflow, user, WorkflowEventType.COMMENT, message);
    }

    public void sendReminder(UUID workflowId, UUID requesterId) {
        ReviewWorkflow workflow = findById(workflowId);
        User requester = userService.findById(requesterId);
        if (!canManage(workflow, requester)) {
            throw new ServiceExceptions.InvalidStateException("Only the initiator or an admin can send reminders.");
        }
        if (workflow.getStatus() != WorkflowStatus.IN_PROGRESS) {
            throw new ServiceExceptions.InvalidStateException("This workflow is no longer in progress.");
        }
        ReviewStep current = getCurrentStep(workflow)
                .orElseThrow(() -> new ServiceExceptions.InvalidStateException("This workflow has no pending step."));
        if (current.getLastReminderSentOn() != null
                && current.getLastReminderSentOn().isAfter(LocalDateTime.now().minus(REMINDER_COOLDOWN))) {
            throw new ServiceExceptions.InvalidStateException("A reminder was already sent recently. Please wait before sending another.");
        }
        current.setLastReminderSentOn(LocalDateTime.now());
        reviewStepRepository.save(current);
        logEvent(workflow, requester, WorkflowEventType.REMINDER_SENT, "Reminder sent to " + current.getReviewer().getUsername() + ".");
    }

    public void cancel(UUID workflowId, UUID requesterId) {
        ReviewWorkflow workflow = findById(workflowId);
        User requester = userService.findById(requesterId);
        if (!canManage(workflow, requester)) {
            throw new ServiceExceptions.InvalidStateException("Only the initiator or an admin can cancel this workflow.");
        }
        if (workflow.getStatus() != WorkflowStatus.IN_PROGRESS) {
            throw new ServiceExceptions.InvalidStateException("This workflow is no longer in progress.");
        }
        workflow.setStatus(WorkflowStatus.CANCELLED);
        workflow.setCompletedOn(LocalDateTime.now());
        reviewWorkflowRepository.save(workflow);
        skipRemainingSteps(workflow);
        documentService.updateStatus(workflow.getDocument().getId(), DocumentStatus.STRUCTURED);
        logEvent(workflow, requester, WorkflowEventType.CANCELLED, null);
    }

    public ReviewWorkflow findById(UUID id) {
        return reviewWorkflowRepository.findById(id)
                .orElseThrow(() -> new ServiceExceptions.NotFoundException("Review workflow not found: " + id));
    }

    public Optional<ReviewWorkflow> findLatestForDocument(UUID documentId) {
        return reviewWorkflowRepository.findByDocumentIdOrderByCreatedOnDesc(documentId).stream().findFirst();
    }

    public List<ReviewStep> getSteps(UUID workflowId) {
        return reviewStepRepository.findByWorkflowIdOrderByStepOrderAsc(workflowId);
    }

    public List<WorkflowEvent> getEvents(UUID workflowId) {
        return workflowEventRepository.findByWorkflowIdOrderByCreatedOnAsc(workflowId);
    }

    public Optional<ReviewStep> getCurrentStep(ReviewWorkflow workflow) {
        return reviewStepRepository.findByWorkflowIdOrderByStepOrderAsc(workflow.getId()).stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING)
                .findFirst();
    }

    public List<ReviewWorkflow> findActionableForUser(UUID userId) {
        return reviewStepRepository.findByReviewerIdAndStatus(userId, StepStatus.PENDING).stream()
                .map(ReviewStep::getWorkflow)
                .filter(w -> w.getStatus() == WorkflowStatus.IN_PROGRESS)
                .filter(w -> getCurrentStep(w).map(s -> s.getReviewer().getId().equals(userId)).orElse(false))
                .toList();
    }

    public boolean isParticipant(ReviewWorkflow workflow, UUID userId) {
        if (workflow.getInitiator().getId().equals(userId)) {
            return true;
        }
        return reviewStepRepository.findByWorkflowIdOrderByStepOrderAsc(workflow.getId()).stream()
                .anyMatch(s -> s.getReviewer().getId().equals(userId));
    }

    public void markRead(UUID workflowId, UUID userId) {
        ReviewWorkflow workflow = findById(workflowId);
        User user = userService.findById(userId);
        WorkflowRead read = workflowReadRepository.findByWorkflowIdAndReaderId(workflowId, userId)
                .orElseGet(() -> WorkflowRead.builder().workflow(workflow).reader(user).build());
        read.setLastReadOn(LocalDateTime.now());
        workflowReadRepository.save(read);
    }

    public void markAllRead(UUID userId) {
        for (ReviewWorkflow workflow : reviewWorkflowRepository.findAll()) {
            if (isParticipant(workflow, userId)) {
                markRead(workflow.getId(), userId);
            }
        }
    }

    public List<WorkflowEvent> findUnreadNotifications(UUID userId) {
        List<WorkflowEvent> notifications = new ArrayList<>();
        for (ReviewWorkflow workflow : reviewWorkflowRepository.findAll()) {
            if (!isParticipant(workflow, userId)) {
                continue;
            }
            LocalDateTime lastRead = workflowReadRepository.findByWorkflowIdAndReaderId(workflow.getId(), userId)
                    .map(WorkflowRead::getLastReadOn)
                    .orElse(workflow.getCreatedOn().minusSeconds(1));
            getEvents(workflow.getId()).stream()
                    .filter(e -> e.getActor() == null || !e.getActor().getId().equals(userId))
                    .filter(e -> e.getCreatedOn().isAfter(lastRead))
                    .forEach(notifications::add);
        }
        return notifications.stream()
                .sorted(Comparator.comparing(WorkflowEvent::getCreatedOn).reversed())
                .toList();
    }

    public boolean hasInvolvement(UUID documentId, UUID userId) {
        return reviewWorkflowRepository.findByDocumentIdOrderByCreatedOnDesc(documentId).stream()
                .anyMatch(w -> isParticipant(w, userId));
    }

    private boolean canManage(ReviewWorkflow workflow, User user) {
        return user.getRole() == Role.ADMIN || workflow.getInitiator().getId().equals(user.getId());
    }

    private void skipRemainingSteps(ReviewWorkflow workflow) {
        for (ReviewStep step : reviewStepRepository.findByWorkflowIdOrderByStepOrderAsc(workflow.getId())) {
            if (step.getStatus() == StepStatus.PENDING) {
                step.setStatus(StepStatus.SKIPPED);
                reviewStepRepository.save(step);
            }
        }
    }

    private void logEvent(ReviewWorkflow workflow, User actor, WorkflowEventType type, String message) {
        WorkflowEvent event = WorkflowEvent.builder()
                .workflow(workflow)
                .actor(actor)
                .eventType(type)
                .message(message)
                .createdOn(LocalDateTime.now())
                .build();
        workflowEventRepository.save(event);
    }
}
