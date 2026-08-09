package com.filecabinet.shared.config;

import com.filecabinet.category.model.Category;
import com.filecabinet.category.service.CategoryService;
import com.filecabinet.document.model.Document;
import com.filecabinet.document.model.DocumentStatus;
import com.filecabinet.document.model.DocumentType;
import com.filecabinet.document.service.DocumentService;
import com.filecabinet.user.model.Role;
import com.filecabinet.user.model.User;
import com.filecabinet.user.repository.UserRepository;
import com.filecabinet.user.service.UserService;
import com.filecabinet.workflow.model.ReviewWorkflow;
import com.filecabinet.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SampleDataGenerator implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final DocumentService documentService;
    private final WorkflowService workflowService;

    @Override
    public void run(String... args) {
        User admin = userRepository.findByUsername("jane.doe").orElseGet(() -> {
            User created = userService.register("jane.doe", "jane.doe@company.com", "password123");
            created.setRole(Role.ADMIN);
            return userRepository.save(created);
        });

        User clerk = userRepository.findByUsername("mike.chen")
                .orElseGet(() -> userService.register("mike.chen", "mike.chen@company.com", "password123"));

        User dimi = userRepository.findByUsername("dimi").orElseGet(() -> {
            User created = userService.register("dimi", "dimi@company.com", "pass123");
            created.setRole(Role.ADMIN);
            return userRepository.save(created);
        });

        User demo = userRepository.findByUsername("demo").orElseGet(() -> {
            User created = userService.register("demo", "demo@filecabinet.local", "demo1234");
            created.setRole(Role.DEMO);
            return userRepository.save(created);
        });
        if (demo.getRole() != Role.DEMO) {
            demo.setRole(Role.DEMO);
            demo = userRepository.save(demo);
        }
        if (demo.getFullName() == null) {
            userService.updateProfile(demo.getId(), "Demo User", "+1 555 0100", "Operations Lead",
                    "FileCabinet Demo Co.", "1 Demo Way, Springfield");
        }

        if (categoryService.findAll().isEmpty()) {
            categoryService.create("Vendor Invoices", "Invoices received from suppliers and vendors");
            categoryService.create("Lease Agreements", "Office and equipment lease contracts");
            categoryService.create("Vendor Contracts", "Signed agreements with vendors and contractors");
            categoryService.create("Office Costs", "Receipts for general office expenses");
        }
        List<Category> categories = categoryService.findAll();

        if (documentService.findAll().isEmpty()) {
            Document invoice = seedInvoiceDocument("Q3 Vendor Invoice — Acme Corp", categories.get(0), admin,
                    "invoice_Tracy Blumstein_28215.pdf");
            documentService.addField(invoice.getId(), "Vendor", "Acme Corp");
            documentService.addField(invoice.getId(), "Amount Due", "4,250.00 USD");
            documentService.addField(invoice.getId(), "Due Date", "2026-08-15");
            documentService.updateStatus(invoice.getId(), DocumentStatus.APPROVED);

            seedDocument("Lease Agreement — Downtown Office", DocumentType.CONTRACT, categories.get(1), clerk);
            seedDocument("Consulting Agreement — Nova LLC", DocumentType.CONTRACT, categories.get(2), admin);
            seedDocument("Receipt — Office Supplies", DocumentType.RECEIPT, categories.get(3), clerk);
            seedDocument("Employment Contract — J. Smith", DocumentType.CONTRACT, categories.get(1), admin);
        }

        if (documentService.findByOwner(demo.getId()).isEmpty()) {
            Document itPurchase = seedInvoiceDocument("IT Equipment Purchase — TechSupply Co", categories.get(0), demo,
                    "invoice_Troy Staebel_25750.pdf");
            documentService.addField(itPurchase.getId(), "Vendor", "TechSupply Co");
            documentService.addField(itPurchase.getId(), "Amount Due", "1,899.00 USD");

            Document maintenance = seedDocument("Server Maintenance Contract — CloudOps Inc", DocumentType.CONTRACT, categories.get(2), clerk);
            documentService.addField(maintenance.getId(), "Vendor", "CloudOps Inc");
            ReviewWorkflow maintenanceWorkflow = workflowService.startWorkflow(maintenance.getId(), admin.getId(),
                    List.of(demo.getId(), dimi.getId()), "Please review this maintenance contract before renewal.");
            workflowService.addComment(maintenanceWorkflow.getId(), admin.getId(),
                    "Let me know if you need the vendor's latest SLA before you decide.");

            Document inspection = seedDocument("Facility Inspection Report — Northside Warehouse", DocumentType.OTHER, categories.get(3), admin);
            documentService.addField(inspection.getId(), "Inspector", "Northside Safety Co.");
            documentService.updateStatus(inspection.getId(), DocumentStatus.REJECTED);
        }
    }

    private Document seedDocument(String title, DocumentType type, Category category, User owner) {
        return documentService.create(title, type, "sample-files/" + UUID.randomUUID() + ".pdf", owner.getId(), category.getId());
    }

    private Document seedInvoiceDocument(String title, Category category, User owner, String sampleFileName) {
        String filePath = copySampleInvoice(sampleFileName);
        return documentService.create(title, DocumentType.INVOICE, filePath, owner.getId(), category.getId());
    }

    private String copySampleInvoice(String sampleFileName) {
        try {
            Path uploadDir = Paths.get("uploads");
            Files.createDirectories(uploadDir);
            String storedName = UUID.randomUUID() + "-" + sampleFileName;
            Path target = uploadDir.resolve(storedName);
            try (var in = new ClassPathResource("sample-invoices/" + sampleFileName).getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target.toString();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to copy sample invoice: " + sampleFileName, ex);
        }
    }
}
