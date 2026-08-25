package com.filecabinet.extraction.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filecabinet.extraction.job.service.ExtractionJobService;
import com.filecabinet.extraction.shared.exception.ExtractionExceptions.JobNotFoundException;
import com.filecabinet.extraction.web.dto.CreateExtractionRequest;
import com.filecabinet.extraction.web.dto.ExtractionJobResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExtractionController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExtractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExtractionJobService jobService;

    private ExtractionJobResponse sampleResponse(String status) {
        return new ExtractionJobResponse(UUID.randomUUID(), UUID.randomUUID(), "mistral",
                status, 0, LocalDateTime.now(), null, false, List.of(), List.of(), List.of());
    }

    @Test
    void queueReturns201() throws Exception {
        when(jobService.queue(any())).thenReturn(sampleResponse("QUEUED"));
        var body = new CreateExtractionRequest(UUID.randomUUID(), "C:/invoice.pdf");

        mockMvc.perform(post("/api/extractions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void queueWithMissingDocumentIdReturns400() throws Exception {
        String body = "{\"sourcePath\":\"C:/invoice.pdf\"}";
        mockMvc.perform(post("/api/extractions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reprocessReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.reprocess(id)).thenReturn(sampleResponse("QUEUED"));
        mockMvc.perform(put("/api/extractions/{id}/reprocess", id))
                .andExpect(status().isOk());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/extractions/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    void getByDocumentReturns200() throws Exception {
        UUID docId = UUID.randomUUID();
        when(jobService.getByDocument(docId)).thenReturn(sampleResponse("COMPLETED"));
        mockMvc.perform(get("/api/extractions/by-document/{id}", docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getByUnknownDocumentReturns404() throws Exception {
        UUID docId = UUID.randomUUID();
        when(jobService.getByDocument(docId)).thenThrow(new JobNotFoundException("nope"));
        mockMvc.perform(get("/api/extractions/by-document/{id}", docId))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateFieldReturns200() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID fieldId = UUID.randomUUID();
        when(jobService.updateFieldValue(eq(docId), eq(fieldId), eq("USD"))).thenReturn(sampleResponse("COMPLETED"));
        mockMvc.perform(put("/api/extractions/by-document/{d}/fields/{f}", docId, fieldId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fieldValue\":\"USD\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateFieldWithBlankValueReturns400() throws Exception {
        mockMvc.perform(put("/api/extractions/by-document/{d}/fields/{f}", UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fieldValue\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateLineItemReturns200() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID lineItemId = UUID.randomUUID();
        when(jobService.updateLineItem(eq(docId), eq(lineItemId), any())).thenReturn(sampleResponse("COMPLETED"));
        mockMvc.perform(put("/api/extractions/by-document/{d}/line-items/{l}", docId, lineItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Corrected\",\"quantity\":2,\"totalAmount\":5}"))
                .andExpect(status().isOk());
    }
}
