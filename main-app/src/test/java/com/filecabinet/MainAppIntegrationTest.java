package com.filecabinet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filecabinet.category.model.Category;
import com.filecabinet.category.repository.CategoryRepository;
import com.filecabinet.integration.ExtractionClient;
import com.filecabinet.integration.dto.ExtractionFieldDto;
import com.filecabinet.integration.dto.ExtractionJobDto;
import com.filecabinet.user.model.Role;
import com.filecabinet.user.model.User;
import com.filecabinet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MainAppIntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:mainapp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.cache.type", () -> "none");
        registry.add("filecabinet.upload-dir", () -> "target/test-uploads");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private ExtractionClient extractionClient;

    private Category category;
    private UUID buyerId;
    private UUID managerId;
    private UUID accountantId;

    @BeforeEach
    void seed() {
        userRepository.save(User.builder().username("admin").email("admin@x.com")
                .passwordHash(passwordEncoder.encode("adminpass")).role(Role.ADMIN).createdOn(LocalDateTime.now()).build());
        buyerId = createUser("buyer", Role.BUYER);
        managerId = createUser("manager", Role.MANAGER);
        accountantId = createUser("accountant", Role.ACCOUNTANT);
        category = categoryRepository.save(Category.builder().name("Invoices").description("Vendor invoices").build());
    }

    private UUID createUser(String username, Role role) {
        return userRepository.save(User.builder().username(username).email(username + "@x.com")
                .passwordHash(passwordEncoder.encode("pass1234")).role(role).createdOn(LocalDateTime.now()).build()).getId();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UUID uploadInvoice(String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf",
                MediaType.APPLICATION_PDF_VALUE, new byte[]{1, 2, 3});
        MvcResult result = mockMvc.perform(multipart("/api/documents").file(file)
                        .param("title", "Test Invoice").param("documentType", "INVOICE")
                        .param("categoryId", category.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json(result).get("id").asText());
    }

    private void approve(UUID workflowId, UUID stepId, String token) throws Exception {
        mockMvc.perform(post("/api/workflows/{id}/steps/{sid}/decision", workflowId, stepId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"comment\":\"ok\"}"))
                .andExpect(status().isOk());
    }

    private String token(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    @Test
    void registerThenLoginReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"clerk1\",\"email\":\"clerk1@x.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"clerk1\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void documentsListReturnsOkForAuthenticatedUser() throws Exception {
        String token = token("admin", "adminpass");
        mockMvc.perform(get("/api/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void categoryCreateIsAdminOnly() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"clerk2\",\"email\":\"clerk2@x.com\",\"password\":\"secret123\"}"));
        String clerkToken = token("clerk2", "secret123");
        mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + clerkToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Blocked\"}"))
                .andExpect(status().isForbidden());

        String adminToken = token("admin", "adminpass");
        mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Contracts\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void uploadInvoiceQueuesExtractionThroughFeign() throws Exception {
        String token = token("admin", "adminpass");
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf",
                MediaType.APPLICATION_PDF_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "Test Invoice")
                        .param("documentType", "INVOICE")
                        .param("categoryId", category.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        verify(extractionClient).queue(any());
    }

    @Test
    void fullInvoiceReviewFlowEndsPaid() throws Exception {
        String admin = token("admin", "adminpass");
        UUID docId = uploadInvoice(admin);

        mockMvc.perform(post("/api/documents/{id}/fields", docId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fieldName\":\"Vendor\",\"fieldValue\":\"Acme\"}"))
                .andExpect(status().isCreated());

        String workflowBody = String.format(
                "{\"documentId\":\"%s\",\"reviewerIds\":[\"%s\",\"%s\",\"%s\"],\"message\":\"go\"}",
                docId, buyerId, managerId, accountantId);
        MvcResult workflowResult = mockMvc.perform(post("/api/workflows").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(workflowBody))
                .andExpect(status().isCreated()).andReturn();
        JsonNode workflow = json(workflowResult);
        UUID workflowId = UUID.fromString(workflow.get("id").asText());
        UUID step1 = null, step2 = null, step3 = null;
        for (JsonNode step : workflow.get("steps")) {
            UUID stepId = UUID.fromString(step.get("id").asText());
            switch (step.get("stepOrder").asInt()) {
                case 1 -> step1 = stepId;
                case 2 -> step2 = stepId;
                default -> step3 = stepId;
            }
        }

        approve(workflowId, step1, token("buyer", "pass1234"));
        approve(workflowId, step2, token("manager", "pass1234"));
        approve(workflowId, step3, token("accountant", "pass1234"));

        mockMvc.perform(get("/api/documents/{id}", docId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/documents/{id}/mark-paid", docId)
                        .header("Authorization", "Bearer " + token("accountant", "pass1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void profileUsersReviewersAndDocumentCrud() throws Exception {
        String admin = token("admin", "adminpass");

        mockMvc.perform(get("/api/profile/me").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
        mockMvc.perform(put("/api/profile").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Admin User\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/users/{id}/role", buyerId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"CLERK\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workflows/reviewers").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/workflows/inbox").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        UUID docId = uploadInvoice(admin);
        mockMvc.perform(put("/api/documents/{id}", docId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"title\":\"Renamed\",\"documentType\":\"CONTRACT\",\"categoryId\":\"%s\"}",
                                category.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/documents/{id}", docId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void applyExtractionCopiesFieldsAndStructuresDocument() throws Exception {
        String admin = token("admin", "adminpass");
        UUID docId = uploadInvoice(admin);
        ExtractionJobDto job = new ExtractionJobDto(UUID.randomUUID(), docId, "mistral", "COMPLETED", 1,
                LocalDateTime.now(), LocalDateTime.now(), false, List.of(),
                List.of(new ExtractionFieldDto(UUID.randomUUID(), "currency", "EUR", 1.0, null)),
                List.of());
        when(extractionClient.getByDocument(docId)).thenReturn(job);

        mockMvc.perform(post("/api/documents/{id}/apply-extraction", docId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STRUCTURED"))
                .andExpect(jsonPath("$.fields[0].fieldName").value("currency"));
    }

    @Test
    void updateLineItemDelegatesToExtractionService() throws Exception {
        String admin = token("admin", "adminpass");
        UUID docId = uploadInvoice(admin);
        UUID lineItemId = UUID.randomUUID();
        when(extractionClient.updateLineItem(eq(docId), eq(lineItemId), any())).thenReturn(null);

        mockMvc.perform(put("/api/documents/{id}/extraction/line-items/{l}", docId, lineItemId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"X\",\"totalAmount\":5}"))
                .andExpect(status().isOk());
        verify(extractionClient).updateLineItem(eq(docId), eq(lineItemId), any());
    }

    @Test
    void correctExtractedFieldDelegatesToExtractionService() throws Exception {
        String admin = token("admin", "adminpass");
        UUID docId = uploadInvoice(admin);
        UUID fieldId = UUID.randomUUID();
        when(extractionClient.updateField(eq(docId), eq(fieldId), any())).thenReturn(null);

        mockMvc.perform(put("/api/documents/{id}/extraction/fields/{fid}", docId, fieldId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fieldValue\":\"USD\"}"))
                .andExpect(status().isOk());
        verify(extractionClient).updateField(eq(docId), eq(fieldId), any());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownDocumentReturns404() throws Exception {
        String admin = token("admin", "adminpass");
        mockMvc.perform(get("/api/documents/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void registerWithInvalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"email\":\"notanemail\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateUsernameRegistrationReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"email\":\"other@x.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isBadRequest());
    }
}
