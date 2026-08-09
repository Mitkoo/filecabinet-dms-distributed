package com.filecabinet.web.controller;

import com.filecabinet.category.service.CategoryService;
import com.filecabinet.document.model.Document;
import com.filecabinet.document.model.DocumentStatus;
import com.filecabinet.document.model.DocumentType;
import com.filecabinet.document.service.DocumentService;
import com.filecabinet.user.model.Role;
import com.filecabinet.user.model.User;
import com.filecabinet.user.service.UserService;
import com.filecabinet.web.dto.DocumentFieldForm;
import com.filecabinet.web.dto.UploadForm;
import com.filecabinet.web.interceptor.SessionAuthInterceptor;
import com.filecabinet.workflow.service.WorkflowService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final CategoryService categoryService;
    private final UserService userService;
    private final WorkflowService workflowService;

    private static final int PAGE_SIZE = 10;

    @GetMapping("/documents")
    public String list(@RequestParam(required = false) String q,
                        @RequestParam(required = false) DocumentType type,
                        @RequestParam(required = false) UUID categoryId,
                        @RequestParam(required = false) DocumentStatus status,
                        @RequestParam(required = false) String owner,
                        @RequestParam(defaultValue = "all") String scope,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "uploaded") String sort,
                        @RequestParam(defaultValue = "desc") String dir,
                        HttpSession session, Model model) {
        User currentUser = currentUser(session);
        List<Document> scopedDocuments = scopedDocuments(currentUser, scope);

        List<Document> typeAndCategoryFiltered = scopedDocuments.stream()
                .filter(doc -> q == null || q.isBlank() || doc.getTitle().toLowerCase().contains(q.toLowerCase()))
                .filter(doc -> type == null || doc.getDocumentType() == type)
                .filter(doc -> categoryId == null || doc.getCategory().getId().equals(categoryId))
                .filter(doc -> owner == null || owner.isBlank() || doc.getOwner().getUsername().toLowerCase().contains(owner.toLowerCase()))
                .toList();

        List<Document> statusFiltered = typeAndCategoryFiltered.stream()
                .filter(doc -> status == null || doc.getStatus() == status)
                .toList();

        Comparator<Document> comparator = sortComparator(sort);
        if ("desc".equalsIgnoreCase(dir)) {
            comparator = comparator.reversed();
        }
        List<Document> documents = statusFiltered.stream().sorted(comparator).toList();

        int totalPages = Math.max(1, (int) Math.ceil(documents.size() / (double) PAGE_SIZE));
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        int fromIndex = Math.min((currentPage - 1) * PAGE_SIZE, documents.size());
        int toIndex = Math.min(fromIndex + PAGE_SIZE, documents.size());

        model.addAttribute("documents", documents.subList(fromIndex, toIndex));
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activePage", "dashboard");
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCount", typeAndCategoryFiltered.size());
        model.addAttribute("uploadedCount", countByStatus(typeAndCategoryFiltered, DocumentStatus.UPLOADED));
        model.addAttribute("structuredCount", countByStatus(typeAndCategoryFiltered, DocumentStatus.STRUCTURED));
        model.addAttribute("inReviewCount", countByStatus(typeAndCategoryFiltered, DocumentStatus.IN_REVIEW));
        model.addAttribute("approvedCount", countByStatus(typeAndCategoryFiltered, DocumentStatus.APPROVED));
        model.addAttribute("rejectedCount", countByStatus(typeAndCategoryFiltered, DocumentStatus.REJECTED));
        model.addAttribute("paidCount", countByStatus(typeAndCategoryFiltered, DocumentStatus.PAID));
        model.addAttribute("archivedCount", countByStatus(typeAndCategoryFiltered, DocumentStatus.ARCHIVED));
        return "dashboard";
    }

    private long countByStatus(List<Document> documents, DocumentStatus status) {
        return documents.stream().filter(doc -> doc.getStatus() == status).count();
    }

    private Comparator<Document> sortComparator(String sort) {
        return switch (sort) {
            case "title" -> Comparator.comparing(doc -> doc.getTitle().toLowerCase());
            case "type" -> Comparator.comparing(doc -> doc.getDocumentType().name());
            case "category" -> Comparator.comparing(doc -> doc.getCategory().getName().toLowerCase());
            case "status" -> Comparator.comparing(doc -> doc.getStatus().name());
            case "owner" -> Comparator.comparing(doc -> doc.getOwner().getUsername().toLowerCase());
            default -> Comparator.comparing(Document::getUploadedOn);
        };
    }

    @GetMapping("/documents/{id}")
    public String detail(@PathVariable UUID id, @RequestParam(required = false) boolean edit,
                          HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        Document document = documentService.findById(id);

        if (!canView(currentUser, document)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You don't have access to that document.");
            return "redirect:/documents";
        }

        model.addAttribute("document", document);
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("currentUser", currentUser);
        if (!model.containsAttribute("editForm")) {
            model.addAttribute("editForm", toEditForm(document));
        }
        if (!model.containsAttribute("fieldForm")) {
            model.addAttribute("fieldForm", new DocumentFieldForm());
        }
        model.addAttribute("fields", documentService.findFields(id));
        model.addAttribute("fileExists", Files.exists(Paths.get(document.getFilePath())));
        model.addAttribute("editOpen", edit);
        model.addAttribute("latestWorkflow", workflowService.findLatestForDocument(id).orElse(null));
        model.addAttribute("canDelete", canDelete(document));
        return "document-detail";
    }

    @GetMapping("/documents/{id}/file")
    @ResponseBody
    public ResponseEntity<Resource> file(@PathVariable UUID id, HttpSession session) throws IOException {
        User currentUser = currentUser(session);
        Document document = documentService.findById(id);
        if (!canView(currentUser, document)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Path path = Paths.get(document.getFilePath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(path);
        MediaType mediaType = contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_PDF;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .body(new FileSystemResource(path));
    }

    @PostMapping("/documents/{id}/edit")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute("editForm") UploadForm form, BindingResult bindingResult,
                        HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        Document document = documentService.findById(id);
        if (!canManage(currentUser, document)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You don't have permission to edit this document.");
            return "redirect:/documents/" + id;
        }
        if (bindingResult.hasErrors()) {
            return detail(id, true, session, model, redirectAttributes);
        }

        documentService.update(id, form.getTitle(), form.getDocumentType(), document.getFilePath(), form.getCategoryId());
        return "redirect:/documents/" + id;
    }

    @PostMapping("/documents/{id}/status")
    public String updateStatus(@PathVariable UUID id, @RequestParam DocumentStatus status,
                                HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        if (currentUser.getRole() != Role.ADMIN) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only admins can approve or reject documents.");
            return "redirect:/documents/" + id;
        }

        documentService.updateStatus(id, status);
        redirectAttributes.addFlashAttribute("successMessage", "Document " + status.name().toLowerCase() + ".");
        return "redirect:/documents/" + id;
    }

    @PostMapping("/documents/{id}/restart")
    public String restart(@PathVariable UUID id, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        Document document = documentService.findById(id);

        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only a manager or admin can restart a rejected document for review.");
            return "redirect:/documents/" + id;
        }
        if (document.getStatus() != DocumentStatus.REJECTED) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only rejected documents can be restarted.");
            return "redirect:/documents/" + id;
        }

        documentService.updateStatus(id, DocumentStatus.STRUCTURED);
        redirectAttributes.addFlashAttribute("successMessage", "Document restarted for review.");
        return "redirect:/documents/" + id;
    }

    @PostMapping("/documents/{id}/mark-paid")
    public String markPaid(@PathVariable UUID id, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        Document document = documentService.findById(id);

        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.ACCOUNTANT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only an accountant or admin can mark an invoice as paid.");
            return "redirect:/documents/" + id;
        }
        if (document.getDocumentType() != DocumentType.INVOICE) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only invoices can be marked as paid.");
            return "redirect:/documents/" + id;
        }
        if (document.getStatus() != DocumentStatus.APPROVED) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only approved documents can be marked as paid.");
            return "redirect:/documents/" + id;
        }

        documentService.updateStatus(id, DocumentStatus.PAID);
        redirectAttributes.addFlashAttribute("successMessage", "Document marked as paid.");
        return "redirect:/documents/" + id;
    }

    @PostMapping("/documents/{id}/fields")
    public String addField(@PathVariable UUID id, @Valid @ModelAttribute("fieldForm") DocumentFieldForm form, BindingResult bindingResult,
                            HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        Document document = documentService.findById(id);
        if (!canManage(currentUser, document)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You don't have permission to edit this document.");
            return "redirect:/documents/" + id;
        }
        if (bindingResult.hasErrors()) {
            return detail(id, false, session, model, redirectAttributes);
        }

        documentService.addField(id, form.getFieldName(), form.getFieldValue());
        return "redirect:/documents/" + id;
    }

    @PostMapping("/documents/{id}/fields/{fieldId}/delete")
    public String deleteField(@PathVariable UUID id, @PathVariable UUID fieldId,
                               HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        Document document = documentService.findById(id);
        if (!canManage(currentUser, document)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You don't have permission to edit this document.");
            return "redirect:/documents/" + id;
        }

        documentService.removeField(id, fieldId);
        redirectAttributes.addFlashAttribute("successMessage", "Field removed.");
        return "redirect:/documents/" + id;
    }

    @PostMapping("/documents/{id}/delete")
    public String delete(@PathVariable UUID id, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);
        Document document = documentService.findById(id);

        if (!canManage(currentUser, document)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You don't have permission to delete this document.");
            return "redirect:/documents/" + id;
        }
        if (!canDelete(document)) {
            redirectAttributes.addFlashAttribute("errorMessage", "This document can't be deleted once it has been sent for review or archived.");
            return "redirect:/documents/" + id;
        }

        documentService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Document deleted.");
        return "redirect:/documents";
    }

    @GetMapping("/documents/new")
    public String newDocumentForm(HttpSession session, Model model) {
        if (!model.containsAttribute("uploadForm")) {
            model.addAttribute("uploadForm", new UploadForm());
        }
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("currentUser", currentUser(session));
        model.addAttribute("activePage", "upload");
        return "upload";
    }

    @PostMapping("/documents")
    public String upload(@Valid @ModelAttribute("uploadForm") UploadForm form, BindingResult bindingResult,
                          @RequestParam("file") MultipartFile file, HttpSession session, Model model,
                          RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(session);

        if (file.isEmpty()) {
            bindingResult.reject("file.required", "Please choose a file to upload.");
            model.addAttribute("errorMessage", "Please choose a file to upload.");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", categoryService.findAll());
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("activePage", "upload");
            return "upload";
        }

        String filePath = storeFile(file);
        documentService.create(form.getTitle(), form.getDocumentType(), filePath, currentUser.getId(), form.getCategoryId());

        redirectAttributes.addFlashAttribute("successMessage", "Document uploaded successfully.");
        return "redirect:/documents";
    }

    private UploadForm toEditForm(Document document) {
        UploadForm editForm = new UploadForm();
        editForm.setTitle(document.getTitle());
        editForm.setDocumentType(document.getDocumentType());
        editForm.setCategoryId(document.getCategory().getId());
        return editForm;
    }

    private String storeFile(MultipartFile file) {
        try {
            Path uploadDir = Paths.get("uploads");
            Files.createDirectories(uploadDir);
            String storedName = UUID.randomUUID() + "-" + file.getOriginalFilename();
            Path target = uploadDir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store uploaded file", ex);
        }
    }

    private User currentUser(HttpSession session) {
        UUID userId = (UUID) session.getAttribute(SessionAuthInterceptor.SESSION_USER_ID);
        return userService.findById(userId);
    }

    private boolean canManage(User user, Document document) {
        return user.getRole() == Role.ADMIN || document.getOwner().getId().equals(user.getId());
    }

    private boolean canView(User user, Document document) {
        return canManage(user, document) || canBrowseAll(user) || workflowService.hasInvolvement(document.getId(), user.getId())
                || (user.getRole() == Role.ACCOUNTANT && document.getDocumentType() == DocumentType.INVOICE)
                || (user.getRole() == Role.MANAGER && document.getStatus() == DocumentStatus.REJECTED);
    }

    private boolean canBrowseAll(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.DEMO;
    }

    private List<Document> scopedDocuments(User currentUser, String scope) {
        if (canBrowseAll(currentUser)) {
            return "mine".equals(scope) ? documentService.findByOwner(currentUser.getId()) : documentService.findAll();
        }
        List<Document> involved = involvedDocuments(currentUser);
        if (!"mine".equals(scope)) {
            Set<UUID> seen = involved.stream().map(Document::getId).collect(Collectors.toSet());
            for (Document doc : documentService.findAll()) {
                boolean matches = (currentUser.getRole() == Role.ACCOUNTANT && doc.getDocumentType() == DocumentType.INVOICE)
                        || (currentUser.getRole() == Role.MANAGER && doc.getStatus() == DocumentStatus.REJECTED);
                if (matches && seen.add(doc.getId())) {
                    involved.add(doc);
                }
            }
        }
        return involved;
    }

    private List<Document> involvedDocuments(User user) {
        List<Document> documents = new ArrayList<>(documentService.findByOwner(user.getId()));
        Set<UUID> seen = documents.stream().map(Document::getId).collect(Collectors.toSet());
        for (Document doc : documentService.findAll()) {
            if (seen.add(doc.getId()) && workflowService.hasInvolvement(doc.getId(), user.getId())) {
                documents.add(doc);
            }
        }
        return documents;
    }

    private boolean isLocked(Document document) {
        return document.getStatus() == DocumentStatus.APPROVED
                || document.getStatus() == DocumentStatus.IN_REVIEW
                || document.getStatus() == DocumentStatus.PAID
                || document.getStatus() == DocumentStatus.ARCHIVED;
    }

    private boolean canDelete(Document document) {
        return !isLocked(document) && workflowService.findLatestForDocument(document.getId()).isEmpty();
    }
}
