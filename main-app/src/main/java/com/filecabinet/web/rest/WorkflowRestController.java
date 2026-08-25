package com.filecabinet.web.rest;

import com.filecabinet.shared.exception.ServiceExceptions.InvalidStateException;
import com.filecabinet.shared.security.AppUserDetails;
import com.filecabinet.user.model.Role;
import com.filecabinet.user.service.UserService;
import com.filecabinet.workflow.model.ReviewStep;
import com.filecabinet.workflow.model.ReviewWorkflow;
import com.filecabinet.workflow.model.WorkflowEvent;
import com.filecabinet.workflow.repository.WorkflowInboxView;
import com.filecabinet.workflow.service.WorkflowService;
import com.filecabinet.web.rest.dto.CommentRequest;
import com.filecabinet.web.rest.dto.DecisionRequest;
import com.filecabinet.web.rest.dto.EventResponse;
import com.filecabinet.web.rest.dto.ReviewerOption;
import com.filecabinet.web.rest.dto.StartWorkflowRequest;
import com.filecabinet.web.rest.dto.StepResponse;
import com.filecabinet.web.rest.dto.WorkflowResponse;
import com.filecabinet.web.rest.dto.WorkflowSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowRestController {

    private final WorkflowService workflowService;
    private final UserService userService;

    @GetMapping("/inbox")
    public List<WorkflowSummaryResponse> inbox(@AuthenticationPrincipal AppUserDetails principal) {
        return workflowService.findInbox(principal.getId()).stream().map(this::toSummary).toList();
    }

    @GetMapping("/reviewers")
    public List<ReviewerOption> reviewers() {
        return userService.getReviewerOptions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse start(@AuthenticationPrincipal AppUserDetails principal,
                                  @Valid @RequestBody StartWorkflowRequest request) {
        ReviewWorkflow workflow = workflowService.startWorkflow(
                request.documentId(), principal.getId(), request.reviewerIds(), request.message());
        log.info("User {} started workflow {} for document {}",
                principal.getUsername(), workflow.getId(), request.documentId());
        return toResponse(workflow.getId());
    }

    @GetMapping("/by-document/{documentId}")
    public ResponseEntity<WorkflowResponse> byDocument(@PathVariable UUID documentId) {
        return workflowService.findLatestForDocument(documentId)
                .map(workflow -> ResponseEntity.ok(toResponse(workflow.getId())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public WorkflowResponse detail(@AuthenticationPrincipal AppUserDetails principal, @PathVariable UUID id) {
        ensureCanView(principal, id);
        return toResponse(id);
    }

    private void ensureCanView(AppUserDetails principal, UUID id) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (!workflowService.isParticipant(workflowService.findById(id), principal.getId())) {
            throw new InvalidStateException("You do not have access to this workflow.");
        }
    }

    private WorkflowResponse toResponse(UUID id) {
        ReviewWorkflow workflow = workflowService.findDetailById(id);
        List<StepResponse> steps = workflowService.getSteps(id).stream().map(this::toStep).toList();
        List<EventResponse> events = workflowService.getEvents(id).stream().map(this::toEvent).toList();
        return new WorkflowResponse(
                workflow.getId(),
                workflow.getDocument().getId(),
                workflow.getDocument().getTitle(),
                workflow.getDocument().getDocumentType().name(),
                workflow.getDocument().getStatus().name(),
                workflow.getStatus().name(),
                workflow.getInitiator().getUsername(),
                workflow.getMessage(),
                workflow.getCreatedOn(),
                workflow.getCompletedOn(),
                steps,
                events);
    }

    @PostMapping("/{id}/steps/{stepId}/decision")
    public WorkflowResponse decide(@AuthenticationPrincipal AppUserDetails principal,
                                   @PathVariable UUID id,
                                   @PathVariable UUID stepId,
                                   @Valid @RequestBody DecisionRequest request) {
        workflowService.decide(id, stepId, principal.getId(), request.approve(), request.comment());
        log.info("User {} decided step {} on workflow {} (approve={})",
                principal.getUsername(), stepId, id, request.approve());
        return toResponse(id);
    }

    @PostMapping("/{id}/comments")
    public WorkflowResponse comment(@AuthenticationPrincipal AppUserDetails principal,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody CommentRequest request) {
        workflowService.addComment(id, principal.getId(), request.message());
        return toResponse(id);
    }

    @PostMapping("/{id}/remind")
    public ResponseEntity<Void> remind(@AuthenticationPrincipal AppUserDetails principal, @PathVariable UUID id) {
        workflowService.sendReminder(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public WorkflowResponse cancel(@AuthenticationPrincipal AppUserDetails principal, @PathVariable UUID id) {
        workflowService.cancel(id, principal.getId());
        log.info("User {} cancelled workflow {}", principal.getUsername(), id);
        return toResponse(id);
    }

    private WorkflowSummaryResponse toSummary(WorkflowInboxView view) {
        return new WorkflowSummaryResponse(view.getId(), view.getDocumentId(), view.getDocumentTitle(),
                view.getInitiatorUsername(), view.getStatus().name(), view.getCreatedOn());
    }

    private StepResponse toStep(ReviewStep step) {
        return new StepResponse(step.getId(), step.getStepOrder(), step.getReviewer().getUsername(),
                step.getStatus().name(), step.getComment(), step.getDecidedOn());
    }

    private EventResponse toEvent(WorkflowEvent event) {
        String actor = event.getActor() != null ? event.getActor().getUsername() : null;
        return new EventResponse(event.getId(), event.getEventType().name(), actor,
                event.getMessage(), event.getCreatedOn());
    }
}
