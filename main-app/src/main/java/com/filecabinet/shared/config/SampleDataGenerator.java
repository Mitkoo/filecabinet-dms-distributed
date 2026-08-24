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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
@Profile("dev")
@RequiredArgsConstructor
public class SampleDataGenerator implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final DocumentService documentService;
    private final WorkflowService workflowService;

    @Value("${filecabinet.seed-password:password123}")
    private String seedPassword;

    @Override
    public void run(String... args) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "seed-admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            seed();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void seed() {
        User admin = userRepository.findByUsername("jane.doe").orElseGet(() -> {
            User created = userService.register("jane.doe", "jane.doe@company.com", seedPassword);
            created.setRole(Role.ADMIN);
            return userRepository.save(created);
        });

        User clerk = userRepository.findByUsername("mike.chen")
                .orElseGet(() -> userService.register("mike.chen", "mike.chen@company.com", seedPassword));

        seedRoleUser("dimi", "dimi@company.com", Role.ADMIN);

        User buyer = seedRoleUser("buyer", "buyer@company.com", Role.BUYER);
        User manager = seedRoleUser("manager", "manager@company.com", Role.MANAGER);
        User accountant = seedRoleUser("accountant", "accountant@company.com", Role.ACCOUNTANT);

        User demo = userRepository.findByUsername("demo").orElseGet(() -> {
            User created = userService.register("demo", "demo@filecabinet.local", seedPassword);
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

            Document supplies = seedInvoiceDocument("Office Supplies Invoice — Brown & Co", categories.get(3), clerk,
                    "invoice_Trudy Brown_15209.pdf");
            documentService.addField(supplies.getId(), "Vendor", "Brown & Co");
            documentService.addField(supplies.getId(), "Amount Due", "512.40 USD");

            Document consulting = seedInvoiceDocument("Consulting Invoice — Blackwell LLC", categories.get(2), admin,
                    "invoice_Troy Blackwell_18864.pdf");
            documentService.addField(consulting.getId(), "Vendor", "Blackwell LLC");
            documentService.addField(consulting.getId(), "Amount Due", "3,120.00 USD");
        }

        if (documentService.findByOwner(demo.getId()).isEmpty()) {
            Document itPurchase = seedInvoiceDocument("IT Equipment Purchase — TechSupply Co", categories.get(0), demo,
                    "invoice_Troy Staebel_25750.pdf");
            documentService.addField(itPurchase.getId(), "Vendor", "TechSupply Co");
            documentService.addField(itPurchase.getId(), "Amount Due", "1,899.00 USD");

            Document maintenance = seedInvoiceDocument("Server Maintenance Invoice — CloudOps Inc", categories.get(2), clerk,
                    "invoice_Troy Staebel_30584.pdf");
            documentService.addField(maintenance.getId(), "Vendor", "CloudOps Inc");
            documentService.addField(maintenance.getId(), "Amount Due", "2,400.00 USD");
            ReviewWorkflow maintenanceWorkflow = workflowService.startWorkflow(maintenance.getId(), admin.getId(),
                    List.of(buyer.getId(), manager.getId(), accountant.getId()), "Please review this maintenance invoice before payment.");
            workflowService.addComment(maintenanceWorkflow.getId(), admin.getId(),
                    "Let me know if you need the vendor's latest SLA before you approve payment.");

            Document rejected = seedInvoiceDocument("Facility Services Invoice — Northside", categories.get(3), admin,
                    "invoice_Tracy Hopkins_21272.pdf");
            documentService.addField(rejected.getId(), "Vendor", "Northside Safety Co.");
            documentService.updateStatus(rejected.getId(), DocumentStatus.REJECTED);
        }
    }

    private User seedRoleUser(String username, String email, Role role) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User created = userService.register(username, email, seedPassword);
            created.setRole(role);
            return userRepository.save(created);
        });
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
