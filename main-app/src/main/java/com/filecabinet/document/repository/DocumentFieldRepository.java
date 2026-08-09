package com.filecabinet.document.repository;

import com.filecabinet.document.model.DocumentField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentFieldRepository extends JpaRepository<DocumentField, UUID> {

    List<DocumentField> findByDocumentId(UUID documentId);
}
