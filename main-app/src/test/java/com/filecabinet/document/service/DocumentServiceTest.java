package com.filecabinet.document.service;

import com.filecabinet.category.model.Category;
import com.filecabinet.category.repository.CategoryRepository;
import com.filecabinet.document.model.Document;
import com.filecabinet.document.model.DocumentField;
import com.filecabinet.document.model.DocumentStatus;
import com.filecabinet.document.model.DocumentType;
import com.filecabinet.document.repository.DocumentFieldRepository;
import com.filecabinet.document.repository.DocumentRepository;
import com.filecabinet.shared.exception.ServiceExceptions.InvalidStateException;
import com.filecabinet.shared.exception.ServiceExceptions.NotFoundException;
import com.filecabinet.user.model.Role;
import com.filecabinet.user.model.User;
import com.filecabinet.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentFieldRepository documentFieldRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DocumentService service;

    private Document document(DocumentType type, DocumentStatus status) {
        return Document.builder()
                .id(UUID.randomUUID())
                .title("Doc")
                .documentType(type)
                .filePath("C:/doc.pdf")
                .status(status)
                .uploadedOn(LocalDateTime.now())
                .owner(User.builder().id(UUID.randomUUID()).username("owner").role(Role.CLERK).build())
                .category(Category.builder().id(UUID.randomUUID()).name("Cat").build())
                .build();
    }

    @Test
    void createStoresDocumentAsUploaded() {
        UUID ownerId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(User.builder().id(ownerId).build()));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(Category.builder().id(categoryId).build()));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        Document created = service.create("Title", DocumentType.INVOICE, "C:/x.pdf", ownerId, categoryId);

        assertThat(created.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(created.getTitle()).isEqualTo("Title");
    }

    @Test
    void createWithUnknownOwnerThrows() {
        UUID ownerId = UUID.randomUUID();
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create("t", DocumentType.OTHER, "p", ownerId, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addFieldPromotesUploadedToStructured() {
        Document doc = document(DocumentType.INVOICE, DocumentStatus.UPLOADED);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(documentFieldRepository.save(any(DocumentField.class))).thenAnswer(inv -> inv.getArgument(0));

        service.addField(doc.getId(), "Vendor", "Acme");

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.STRUCTURED);
        verify(documentRepository).save(doc);
    }

    @Test
    void addFieldKeepsStatusWhenAlreadyStructured() {
        Document doc = document(DocumentType.INVOICE, DocumentStatus.STRUCTURED);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(documentFieldRepository.save(any(DocumentField.class))).thenAnswer(inv -> inv.getArgument(0));

        service.addField(doc.getId(), "Vendor", "Acme");

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.STRUCTURED);
    }

    @Test
    void markPaidRequiresInvoice() {
        Document doc = document(DocumentType.CONTRACT, DocumentStatus.APPROVED);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        assertThatThrownBy(() -> service.markPaid(doc.getId())).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void markPaidRequiresApprovedStatus() {
        Document doc = document(DocumentType.INVOICE, DocumentStatus.STRUCTURED);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        assertThatThrownBy(() -> service.markPaid(doc.getId())).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void markPaidSetsStatusToPaid() {
        Document doc = document(DocumentType.INVOICE, DocumentStatus.APPROVED);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markPaid(doc.getId());

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PAID);
    }

    @Test
    void applyReviewedFieldsReplacesFieldsAndStructuresDocument() {
        Document doc = document(DocumentType.INVOICE, DocumentStatus.UPLOADED);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(documentFieldRepository.save(any(DocumentField.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("currency", "EUR");
        fields.put("total_net", "100.00");
        service.applyReviewedFields(doc.getId(), fields);

        verify(documentFieldRepository).deleteByDocumentId(doc.getId());
        verify(documentFieldRepository, times(2)).save(any(DocumentField.class));
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.STRUCTURED);
    }

    @Test
    void deleteUnknownDocumentThrows() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsById(id)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesFieldsAndDocument() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsById(id)).thenReturn(true);
        service.delete(id);
        verify(documentFieldRepository).deleteByDocumentId(id);
        verify(documentRepository).deleteById(id);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void findByIdUnknownThrows() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void findDetailByIdUnknownThrows() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findWithOwnerAndCategoryById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findDetailById(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateReplacesFieldsAndCategory() {
        Document doc = document(DocumentType.INVOICE, DocumentStatus.STRUCTURED);
        UUID categoryId = UUID.randomUUID();
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(Category.builder().id(categoryId).name("New").build()));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        Document updated = service.update(doc.getId(), "Renamed", DocumentType.CONTRACT, "C:/new.pdf", categoryId);

        assertThat(updated.getTitle()).isEqualTo("Renamed");
        assertThat(updated.getDocumentType()).isEqualTo(DocumentType.CONTRACT);
        assertThat(updated.getCategory().getName()).isEqualTo("New");
    }

    @Test
    void updateWithUnknownCategoryThrows() {
        Document doc = document(DocumentType.INVOICE, DocumentStatus.STRUCTURED);
        UUID categoryId = UUID.randomUUID();
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(doc.getId(), "x", DocumentType.OTHER, "p", categoryId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateStatusSetsStatus() {
        Document doc = document(DocumentType.INVOICE, DocumentStatus.STRUCTURED);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(doc.getId(), DocumentStatus.ARCHIVED);

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.ARCHIVED);
    }

    @Test
    void changeStatusAsAdminAllowsAnyTarget() {
        authenticateAs("ADMIN");
        Document doc = document(DocumentType.CONTRACT, DocumentStatus.IN_REVIEW);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changeStatus(doc.getId(), DocumentStatus.ARCHIVED);

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.ARCHIVED);
    }

    @Test
    void changeStatusAsManagerCanReject() {
        authenticateAs("MANAGER");
        Document doc = document(DocumentType.CONTRACT, DocumentStatus.IN_REVIEW);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changeStatus(doc.getId(), DocumentStatus.REJECTED);

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.REJECTED);
    }

    @Test
    void changeStatusAsManagerCannotMarkPaid() {
        authenticateAs("MANAGER");
        Document doc = document(DocumentType.INVOICE, DocumentStatus.APPROVED);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.changeStatus(doc.getId(), DocumentStatus.PAID))
                .isInstanceOf(InvalidStateException.class);
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void changeStatusAsAccountantCanMarkApprovedInvoicePaid() {
        authenticateAs("ACCOUNTANT");
        Document doc = document(DocumentType.INVOICE, DocumentStatus.APPROVED);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changeStatus(doc.getId(), DocumentStatus.PAID);

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PAID);
    }

    @Test
    void removeFieldDeletesWhenBelongingToDocument() {
        Document doc = document(DocumentType.INVOICE, DocumentStatus.STRUCTURED);
        UUID fieldId = UUID.randomUUID();
        DocumentField field = DocumentField.builder().id(fieldId).document(doc).fieldName("Vendor").build();
        when(documentFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));

        service.removeField(doc.getId(), fieldId);

        verify(documentFieldRepository).delete(field);
    }

    @Test
    void removeFieldFromAnotherDocumentThrows() {
        Document doc = document(DocumentType.INVOICE, DocumentStatus.STRUCTURED);
        UUID fieldId = UUID.randomUUID();
        DocumentField field = DocumentField.builder().id(fieldId)
                .document(document(DocumentType.INVOICE, DocumentStatus.STRUCTURED)).fieldName("Vendor").build();
        when(documentFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));

        assertThatThrownBy(() -> service.removeField(doc.getId(), fieldId)).isInstanceOf(NotFoundException.class);
        verify(documentFieldRepository, never()).delete(any(DocumentField.class));
    }

    @Test
    void findByOwnerDelegatesToRepository() {
        UUID ownerId = UUID.randomUUID();
        Document doc = document(DocumentType.INVOICE, DocumentStatus.UPLOADED);
        when(documentRepository.findByOwnerId(ownerId)).thenReturn(List.of(doc));

        assertThat(service.findByOwner(ownerId)).containsExactly(doc);
    }
}
