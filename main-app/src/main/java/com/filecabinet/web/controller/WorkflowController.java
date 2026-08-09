package com.filecabinet.web.controller;

import com.filecabinet.document.model.Document;
import com.filecabinet.document.model.DocumentStatus;
import com.filecabinet.document.model.DocumentType;
import com.filecabinet.document.service.DocumentService;
import com.filecabinet.shared.exception.ServiceExceptions;
import com.filecabinet.user.model.Role;
import com.filecabinet.user.model.User;
import com.filecabinet.user.service.UserService;
import com.filecabinet.web.interceptor.SessionAuthInterceptor;
import com.filecabinet.workflow.model.ReviewStep;
import com.filecabinet.workflow.model.ReviewWorkflow;
import com.filecabinet.workflow.model.WorkflowEvent;
import com.filecabinet.workflow.service.WorkflowService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final DocumentService documentService;
    private final UserService userService;

    @GetMapping("/documents/{documentId}/workflow/new")
    public String newWorkflowForm(@PathVariable UUID documentId, HttpSession session, Model model,
                                   RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        Document document = documentService.findById(documentId);

        if (!canInitiate(currentUser, document)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only the document owner or an admin can start a review.");
            return "redirect:/documents/" + documentId;
        }
        if (document.getStatus() != DocumentStatus.STRUCTURED) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only structured documents can be sent for review.");
            return "redirect:/documents/" + documentId;
        }

        model.addAttribute("document", document);
        model.addAttribute("currentUser", currentUser);

        boolean fixedPipeline = document.getDocumentType() == DocumentType.INVOICE;
        model.addAttribute("fixedPipeline", fixedPipeline);
        if (fixedPipeline) {
            model.addAttribute("buyers", userService.findByRole(Role.BUYER));
            model.addAttribute("managers", userService.findByRole(Role.MANAGER));
            model.addAttribute("accountant", userService.findByRole(Role.ACCOUNTANT).stream().findFirst().orElse(null));
        } else {
            model.addAttribute("users", userService.findAll());
        }
        return "workflow-new";
    }

    @PostMapping("/documents/{documentId}/workflow")
    public String startWorkflow(@PathVariable UUID documentId,
                                 @RequestParam List<UUID> reviewerIds,
                                 @RequestParam(required = false) String message,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        Document document = documentService.findById(documentId);

        if (!canInitiate(currentUser, document)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only the document owner or an admin can start a review.");
            return "redirect:/documents/" + documentId;
        }

        try {
            ReviewWorkflow workflow = workflowService.startWorkflow(documentId, currentUser.getId(), reviewerIds, message);
            redirectAttributes.addFlashAttribute("successMessage", "Review workflow started.");
            return "redirect:/workflows/" + workflow.getId();
        } catch (ServiceExceptions.InvalidStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/documents/" + documentId + "/workflow/new";
        }
    }

    @GetMapping("/workflows/{id}")
    public String detail(@PathVariable UUID id, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        ReviewWorkflow workflow = workflowService.findById(id);

        if (currentUser.getRole() != Role.ADMIN && !workflowService.isParticipant(workflow, currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You don't have access to that review workflow.");
            return "redirect:/documents";
        }

        workflowService.markRead(id, currentUser.getId());

        List<ReviewStep> steps = workflowService.getSteps(id);
        Optional<ReviewStep> currentStep = workflowService.getCurrentStep(workflow);

        model.addAttribute("workflow", workflow);
        model.addAttribute("steps", steps);
        model.addAttribute("events", workflowService.getEvents(id));
        model.addAttribute("currentStep", currentStep.orElse(null));
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isCurrentReviewer",
                currentStep.map(s -> s.getReviewer().getId().equals(currentUser.getId())).orElse(false));
        model.addAttribute("canManage",
                currentUser.getRole() == Role.ADMIN || workflow.getInitiator().getId().equals(currentUser.getId()));
        List<WorkflowEvent> unreadNotifications = workflowService.findUnreadNotifications(currentUser.getId());
        model.addAttribute("unreadNotifications", unreadNotifications.stream().limit(8).toList());
        model.addAttribute("unreadNotificationCount", unreadNotifications.size());
        return "workflow-detail";
    }

    @PostMapping("/workflows/{id}/steps/{stepId}/decision")
    public String decide(@PathVariable UUID id, @PathVariable UUID stepId,
                          @RequestParam boolean approve, @RequestParam(required = false) String comment,
                          HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        try {
            workflowService.decide(id, stepId, currentUser.getId(), approve, comment);
            redirectAttributes.addFlashAttribute("successMessage", approve ? "Step approved." : "Step rejected.");
        } catch (ServiceExceptions.InvalidStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/workflows/" + id;
    }

    @PostMapping("/workflows/{id}/comments")
    public String comment(@PathVariable UUID id, @RequestParam String message,
                           HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        try {
            workflowService.addComment(id, currentUser.getId(), message);
        } catch (ServiceExceptions.InvalidStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/workflows/" + id;
    }

    @PostMapping("/workflows/{id}/remind")
    public String remind(@PathVariable UUID id, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        try {
            workflowService.sendReminder(id, currentUser.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Reminder sent.");
        } catch (ServiceExceptions.InvalidStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/workflows/" + id;
    }

    @PostMapping("/workflows/{id}/cancel")
    public String cancel(@PathVariable UUID id, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        try {
            workflowService.cancel(id, currentUser.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Workflow cancelled.");
        } catch (ServiceExceptions.InvalidStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/workflows/" + id;
    }

    @GetMapping("/workflows/inbox")
    public String inbox(HttpSession session, Model model) {
        User currentUser = currentUser(session);
        workflowService.markAllRead(currentUser.getId());
        model.addAttribute("workflows", workflowService.findActionableForUser(currentUser.getId()));
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activePage", "inbox");
        model.addAttribute("unreadNotifications", List.<WorkflowEvent>of());
        model.addAttribute("unreadNotificationCount", 0);
        return "workflow-inbox";
    }

    private boolean canInitiate(User user, Document document) {
        return user.getRole() == Role.ADMIN || document.getOwner().getId().equals(user.getId());
    }

    private User currentUser(HttpSession session) {
        UUID userId = (UUID) session.getAttribute(SessionAuthInterceptor.SESSION_USER_ID);
        return userService.findById(userId);
    }
}
