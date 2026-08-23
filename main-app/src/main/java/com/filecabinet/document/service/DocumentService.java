package com.filecabinet.document.service;

import com.filecabinet.category.model.Category;
import com.filecabinet.category.repository.CategoryRepository;
import com.filecabinet.shared.exception.ServiceExceptions;
import com.filecabinet.document.model.Document;
import com.filecabinet.document.model.DocumentField;
import com.filecabinet.document.model.DocumentStatus;
import com.filecabinet.document.model.DocumentType;
import com.filecabinet.document.repository.DocumentFieldRepository;
import com.filecabinet.document.repository.DocumentListView;
import com.filecabinet.document.repository.DocumentRepository;
import com.filecabinet.user.model.User;
import com.filecabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentFieldRepository documentFieldRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public Document create(String title, DocumentType documentType, String filePath, UUID ownerId, UUID categoryId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ServiceExceptions.NotFoundException("User not found: " + ownerId));
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ServiceExceptions.NotFoundException("Category not found: " + categoryId));

        Document document = Document.builder()
                .title(title)
                .documentType(documentType)
                .filePath(filePath)
                .status(DocumentStatus.UPLOADED)
                .uploadedOn(LocalDateTime.now())
                .owner(owner)
                .category(category)
                .build();
        return documentRepository.save(document);
    }

    public List<Document> findAll() {
        return documentRepository.findAll();
    }

    public List<Document> findByOwner(UUID ownerId) {
        return documentRepository.findByOwnerId(ownerId);
    }

    @Transactional(readOnly = true)
    public Page<DocumentListView> list(UUID ownerId, boolean all, Pageable pageable) {
        return all
                ? documentRepository.findAllAsList(pageable)
                : documentRepository.findListByOwnerId(ownerId, pageable);
    }

    public Document findById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ServiceExceptions.NotFoundException("Document not found: " + id));
    }

    @Transactional(readOnly = true)
    public Document findDetailById(UUID id) {
        return documentRepository.findWithOwnerAndCategoryById(id)
                .orElseThrow(() -> new ServiceExceptions.NotFoundException("Document not found: " + id));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public Document markPaid(UUID id) {
        Document document = findById(id);
        if (document.getDocumentType() != DocumentType.INVOICE) {
            throw new ServiceExceptions.InvalidStateException("Only invoices can be marked as paid.");
        }
        if (document.getStatus() != DocumentStatus.APPROVED) {
            throw new ServiceExceptions.InvalidStateException("Only approved documents can be marked as paid.");
        }
        document.setStatus(DocumentStatus.PAID);
        return documentRepository.save(document);
    }

    public Document update(UUID id, String title, DocumentType documentType, String filePath, UUID categoryId) {
        Document document = findById(id);
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ServiceExceptions.NotFoundException("Category not found: " + categoryId));

        document.setTitle(title);
        document.setDocumentType(documentType);
        document.setFilePath(filePath);
        document.setCategory(category);
        return documentRepository.save(document);
    }

    public Document updateStatus(UUID id, DocumentStatus status) {
        Document document = findById(id);
        document.setStatus(status);
        return documentRepository.save(document);
    }

    @Transactional
    public void delete(UUID id) {
        if (!documentRepository.existsById(id)) {
            throw new ServiceExceptions.NotFoundException("Document not found: " + id);
        }
        documentFieldRepository.deleteByDocumentId(id);
        documentRepository.deleteById(id);
    }

    public List<DocumentField> findFields(UUID documentId) {
        return documentFieldRepository.findByDocumentIdOrderByFieldName(documentId);
    }

    @Transactional
    public DocumentField addField(UUID documentId, String fieldName, String fieldValue) {
        Document document = findById(documentId);

        DocumentField field = DocumentField.builder()
                .document(document)
                .fieldName(fieldName)
                .fieldValue(fieldValue)
                .build();
        DocumentField saved = documentFieldRepository.save(field);

        if (document.getStatus() == DocumentStatus.UPLOADED) {
            document.setStatus(DocumentStatus.STRUCTURED);
            documentRepository.save(document);
        }
        return saved;
    }

    @Transactional
    public Document applyReviewedFields(UUID documentId, Map<String, String> fields) {
        documentFieldRepository.deleteByDocumentId(documentId);
        Document document = findById(documentId);
        fields.forEach((name, value) -> documentFieldRepository.save(DocumentField.builder()
                .document(document)
                .fieldName(name)
                .fieldValue(value)
                .build()));
        if (document.getStatus() == DocumentStatus.UPLOADED) {
            document.setStatus(DocumentStatus.STRUCTURED);
            documentRepository.save(document);
        }
        return document;
    }

    public void removeField(UUID documentId, UUID fieldId) {
        DocumentField field = documentFieldRepository.findById(fieldId)
                .orElseThrow(() -> new ServiceExceptions.NotFoundException("Field not found: " + fieldId));
        if (!field.getDocument().getId().equals(documentId)) {
            throw new ServiceExceptions.NotFoundException("Field not found: " + fieldId);
        }
        documentFieldRepository.delete(field);
    }
}
