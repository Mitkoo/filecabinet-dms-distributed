package com.filecabinet.web.advice;

import com.filecabinet.web.interceptor.SessionAuthInterceptor;
import com.filecabinet.workflow.model.WorkflowEvent;
import com.filecabinet.workflow.service.WorkflowService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.UUID;

@ControllerAdvice
@RequiredArgsConstructor
public class NavigationModelAdvice {

    private final WorkflowService workflowService;

    @ModelAttribute
    public void addNotificationCounts(HttpSession session, Model model) {
        if (!(session.getAttribute(SessionAuthInterceptor.SESSION_USER_ID) instanceof UUID userId)) {
            return;
        }
        model.addAttribute("pendingReviewCount", workflowService.findActionableForUser(userId).size());
        List<WorkflowEvent> unreadNotifications = workflowService.findUnreadNotifications(userId);
        model.addAttribute("unreadNotifications", unreadNotifications.stream().limit(8).toList());
        model.addAttribute("unreadNotificationCount", unreadNotifications.size());
    }
}
