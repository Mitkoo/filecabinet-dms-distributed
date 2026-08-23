package com.filecabinet.document.repository;

import com.filecabinet.document.model.Document;
import com.filecabinet.document.model.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByOwnerId(UUID ownerId);

    @EntityGraph(attributePaths = {"owner", "category"})
    Optional<Document> findWithOwnerAndCategoryById(UUID id);

    @Query("""
            SELECT d.id AS id,
                   d.title AS title,
                   d.documentType AS documentType,
                   d.status AS status,
                   d.uploadedOn AS uploadedOn,
                   c.name AS categoryName,
                   o.username AS ownerUsername
            FROM Document d
            JOIN d.category c
            JOIN d.owner o
            """)
    Page<DocumentListView> findAllAsList(Pageable pageable);

    @Query("""
            SELECT d.id AS id,
                   d.title AS title,
                   d.documentType AS documentType,
                   d.status AS status,
                   d.uploadedOn AS uploadedOn,
                   c.name AS categoryName,
                   o.username AS ownerUsername
            FROM Document d
            JOIN d.category c
            JOIN d.owner o
            WHERE o.id = :ownerId
            """)
    Page<DocumentListView> findListByOwnerId(@Param("ownerId") UUID ownerId, Pageable pageable);

    long countByStatus(DocumentStatus status);

    boolean existsByCategoryId(UUID categoryId);
}
