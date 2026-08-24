package com.filecabinet.workflow.service;

import com.filecabinet.document.model.Document;
import com.filecabinet.document.model.DocumentStatus;
import com.filecabinet.document.model.DocumentType;
import com.filecabinet.document.service.DocumentService;
import com.filecabinet.shared.exception.ServiceExceptions.InvalidStateException;
import com.filecabinet.user.model.Role;
import com.filecabinet.user.model.User;
import com.filecabinet.user.service.UserService;
import com.filecabinet.workflow.model.ReviewStep;
import com.filecabinet.workflow.model.ReviewWorkflow;
import com.filecabinet.workflow.model.StepStatus;
import com.filecabinet.workflow.model.WorkflowStatus;
import com.filecabinet.workflow.repository.ReviewStepRepository;
import com.filecabinet.workflow.repository.ReviewWorkflowRepository;
import com.filecabinet.workflow.repository.WorkflowEventRepository;
import com.filecabinet.workflow.repository.WorkflowReadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock private ReviewWorkflowRepository reviewWorkflowRepository;
    @Mock private ReviewStepRepository reviewStepRepository;
    @Mock private WorkflowEventRepository workflowEventRepository;
    @Mock private WorkflowReadRepository workflowReadRepository;
    @Mock private DocumentService documentService;
    @Mock private UserService userService;

    @InjectMocks
    private WorkflowService service;

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).username(role.name().toLowerCase()).role(role).build();
    }

    private Document structuredDoc(DocumentType type) {
        return Document.builder().id(UUID.randomUUID()).title("Doc").documentType(type)
                .status(DocumentStatus.STRUCTURED).build();
    }

    private ReviewWorkflow workflow(Document doc, User initiator) {
        return ReviewWorkflow.builder().id(UUID.randomUUID()).document(doc).initiator(initiator)
                .status(WorkflowStatus.IN_PROGRESS).build();
    }

    private ReviewStep step(ReviewWorkflow wf, User reviewer, int order) {
        return ReviewStep.builder().id(UUID.randomUUID()).workflow(wf).reviewer(reviewer)
                .stepOrder(order).status(StepStatus.PENDING).build();
    }

    @Test
    void decideRejectsWrongReviewer() {
        ReviewWorkflow wf = workflow(structuredDoc(DocumentType.CONTRACT), user(Role.ADMIN));
        User assigned = user(Role.MANAGER);
        ReviewStep step = step(wf, assigned, 1);
        when(reviewWorkflowRepository.findById(wf.getId())).thenReturn(Optional.of(wf));
        when(reviewStepRepository.findById(step.getId())).thenReturn(Optional.of(step));
        when(reviewStepRepository.findCurrentStep(wf.getId())).thenReturn(Optional.of(step));

        assertThatThrownBy(() -> service.decide(wf.getId(), step.getId(), UUID.randomUUID(), true, null))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void decideRejectsOutOfTurnStep() {
        ReviewWorkflow wf = workflow(structuredDoc(DocumentType.CONTRACT), user(Role.ADMIN));
        User r1 = user(Role.MANAGER);
        User r2 = user(Role.ACCOUNTANT);
        ReviewStep step1 = step(wf, r1, 1);
        ReviewStep step2 = step(wf, r2, 2);
        when(reviewWorkflowRepository.findById(wf.getId())).thenReturn(Optional.of(wf));
        when(reviewStepRepository.findById(step2.getId())).thenReturn(Optional.of(step2));
        when(reviewStepRepository.findCurrentStep(wf.getId())).thenReturn(Optional.of(step1));

        assertThatThrownBy(() -> service.decide(wf.getId(), step2.getId(), r2.getId(), true, null))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void decideApproveNonFinalKeepsWorkflowInProgress() {
        ReviewWorkflow wf = workflow(structuredDoc(DocumentType.CONTRACT), user(Role.ADMIN));
        User r1 = user(Role.MANAGER);
        User r2 = user(Role.ACCOUNTANT);
        ReviewStep step1 = step(wf, r1, 1);
        ReviewStep step2 = step(wf, r2, 2);
        when(reviewWorkflowRepository.findById(wf.getId())).thenReturn(Optional.of(wf));
        when(reviewStepRepository.findById(step1.getId())).thenReturn(Optional.of(step1));
        when(reviewStepRepository.findCurrentStep(wf.getId()))
                .thenReturn(Optional.of(step1), Optional.of(step2));

        service.decide(wf.getId(), step1.getId(), r1.getId(), true, "ok");

        assertThat(step1.getStatus()).isEqualTo(StepStatus.APPROVED);
        assertThat(wf.getStatus()).isEqualTo(WorkflowStatus.IN_PROGRESS);
    }

    @Test
    void decideApproveFinalCompletesAndApprovesDocument() {
        Document doc = structuredDoc(DocumentType.CONTRACT);
        ReviewWorkflow wf = workflow(doc, user(Role.ADMIN));
        User r1 = user(Role.MANAGER);
        ReviewStep step1 = step(wf, r1, 1);
        when(reviewWorkflowRepository.findById(wf.getId())).thenReturn(Optional.of(wf));
        when(reviewStepRepository.findById(step1.getId())).thenReturn(Optional.of(step1));
        when(reviewStepRepository.findCurrentStep(wf.getId()))
                .thenReturn(Optional.of(step1), Optional.empty());

        service.decide(wf.getId(), step1.getId(), r1.getId(), true, "done");

        assertThat(wf.getStatus()).isEqualTo(WorkflowStatus.APPROVED);
        verify(documentService).updateStatus(doc.getId(), DocumentStatus.APPROVED);
    }

    @Test
    void decideRejectTerminatesWorkflowAndRejectsDocument() {
        Document doc = structuredDoc(DocumentType.CONTRACT);
        ReviewWorkflow wf = workflow(doc, user(Role.ADMIN));
        User r1 = user(Role.MANAGER);
        ReviewStep step1 = step(wf, r1, 1);
        when(reviewWorkflowRepository.findById(wf.getId())).thenReturn(Optional.of(wf));
        when(reviewStepRepository.findById(step1.getId())).thenReturn(Optional.of(step1));
        when(reviewStepRepository.findCurrentStep(wf.getId())).thenReturn(Optional.of(step1));

        service.decide(wf.getId(), step1.getId(), r1.getId(), false, "no good");

        assertThat(wf.getStatus()).isEqualTo(WorkflowStatus.REJECTED);
        verify(documentService).updateStatus(doc.getId(), DocumentStatus.REJECTED);
    }

    @Test
    void startWorkflowRejectsNonStructuredDocument() {
        Document doc = Document.builder().id(UUID.randomUUID()).status(DocumentStatus.UPLOADED)
                .documentType(DocumentType.CONTRACT).build();
        when(documentService.findById(doc.getId())).thenReturn(doc);

        assertThatThrownBy(() -> service.startWorkflow(doc.getId(), UUID.randomUUID(), List.of(UUID.randomUUID()), "m"))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void startWorkflowRejectsEmptyReviewers() {
        Document doc = structuredDoc(DocumentType.CONTRACT);
        when(documentService.findById(doc.getId())).thenReturn(doc);

        assertThatThrownBy(() -> service.startWorkflow(doc.getId(), UUID.randomUUID(), List.of(), "m"))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void startWorkflowRejectsDuplicateReviewers() {
        Document doc = structuredDoc(DocumentType.CONTRACT);
        UUID reviewer = UUID.randomUUID();
        when(documentService.findById(doc.getId())).thenReturn(doc);

        assertThatThrownBy(() -> service.startWorkflow(doc.getId(), UUID.randomUUID(), List.of(reviewer, reviewer), "m"))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void startWorkflowRejectsInvalidInvoicePipeline() {
        Document doc = structuredDoc(DocumentType.INVOICE);
        User initiator = user(Role.ADMIN);
        User wrong1 = user(Role.CLERK);
        User wrong2 = user(Role.CLERK);
        when(documentService.findById(doc.getId())).thenReturn(doc);
        when(userService.findById(initiator.getId())).thenReturn(initiator);
        when(userService.findById(wrong1.getId())).thenReturn(wrong1);
        when(userService.findById(wrong2.getId())).thenReturn(wrong2);

        assertThatThrownBy(() -> service.startWorkflow(doc.getId(), initiator.getId(),
                List.of(wrong1.getId(), wrong2.getId()), "m"))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void startWorkflowCreatesWorkflowAndMovesDocumentToReview() {
        Document doc = structuredDoc(DocumentType.CONTRACT);
        User initiator = user(Role.ADMIN);
        User r1 = user(Role.MANAGER);
        User r2 = user(Role.ACCOUNTANT);
        when(documentService.findById(doc.getId())).thenReturn(doc);
        when(userService.findById(initiator.getId())).thenReturn(initiator);
        when(userService.findById(r1.getId())).thenReturn(r1);
        when(userService.findById(r2.getId())).thenReturn(r2);
        when(reviewWorkflowRepository.save(any(ReviewWorkflow.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewWorkflow created = service.startWorkflow(doc.getId(), initiator.getId(),
                List.of(r1.getId(), r2.getId()), "please review");

        assertThat(created.getStatus()).isEqualTo(WorkflowStatus.IN_PROGRESS);
        verify(reviewStepRepository, org.mockito.Mockito.times(2)).save(any(ReviewStep.class));
        verify(documentService).updateStatus(eq(doc.getId()), eq(DocumentStatus.IN_REVIEW));
    }

    @Test
    void cancelByInitiatorSetsCancelledAndRestoresDocument() {
        Document doc = structuredDoc(DocumentType.CONTRACT);
        User initiator = user(Role.MANAGER);
        ReviewWorkflow wf = workflow(doc, initiator);
        when(reviewWorkflowRepository.findById(wf.getId())).thenReturn(Optional.of(wf));
        when(userService.findById(initiator.getId())).thenReturn(initiator);

        service.cancel(wf.getId(), initiator.getId());

        assertThat(wf.getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
        verify(documentService).updateStatus(doc.getId(), DocumentStatus.STRUCTURED);
    }

    @Test
    void cancelByNonManagerThrows() {
        ReviewWorkflow wf = workflow(structuredDoc(DocumentType.CONTRACT), user(Role.MANAGER));
        User outsider = user(Role.CLERK);
        when(reviewWorkflowRepository.findById(wf.getId())).thenReturn(Optional.of(wf));
        when(userService.findById(outsider.getId())).thenReturn(outsider);

        assertThatThrownBy(() -> service.cancel(wf.getId(), outsider.getId()))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void addCommentByNonParticipantThrows() {
        ReviewWorkflow wf = workflow(structuredDoc(DocumentType.CONTRACT), user(Role.MANAGER));
        User outsider = user(Role.CLERK);
        when(reviewWorkflowRepository.findById(wf.getId())).thenReturn(Optional.of(wf));
        when(userService.findById(outsider.getId())).thenReturn(outsider);
        when(reviewStepRepository.existsByWorkflowIdAndReviewerId(wf.getId(), outsider.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.addComment(wf.getId(), outsider.getId(), "hi"))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void addCommentByInitiatorSucceeds() {
        ReviewWorkflow wf = workflow(structuredDoc(DocumentType.CONTRACT), user(Role.MANAGER));
        User initiator = wf.getInitiator();
        when(reviewWorkflowRepository.findById(wf.getId())).thenReturn(Optional.of(wf));
        when(userService.findById(initiator.getId())).thenReturn(initiator);

        service.addComment(wf.getId(), initiator.getId(), "looks good");

        verify(workflowEventRepository).save(any());
    }
}
