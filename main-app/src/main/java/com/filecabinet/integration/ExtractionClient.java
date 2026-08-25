package com.filecabinet.integration;

import com.filecabinet.integration.dto.ExtractionJobDto;
import com.filecabinet.integration.dto.QueueExtractionRequest;
import com.filecabinet.integration.dto.UpdateFieldRequest;
import com.filecabinet.integration.dto.UpdateLineItemRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "extraction-svc", url = "${extraction.service.url}", configuration = FeignClientConfig.class)
public interface ExtractionClient {

    @PostMapping("/api/extractions")
    ExtractionJobDto queue(@RequestBody QueueExtractionRequest request);

    @DeleteMapping("/api/extractions/{id}")
    void delete(@PathVariable("id") UUID id);

    @GetMapping("/api/extractions/by-document/{documentId}")
    ExtractionJobDto getByDocument(@PathVariable("documentId") UUID documentId);

    @PutMapping("/api/extractions/by-document/{documentId}/fields/{fieldId}")
    ExtractionJobDto updateField(@PathVariable("documentId") UUID documentId,
                                 @PathVariable("fieldId") UUID fieldId,
                                 @RequestBody UpdateFieldRequest request);

    @PutMapping("/api/extractions/by-document/{documentId}/line-items/{lineItemId}")
    ExtractionJobDto updateLineItem(@PathVariable("documentId") UUID documentId,
                                    @PathVariable("lineItemId") UUID lineItemId,
                                    @RequestBody UpdateLineItemRequest request);
}
