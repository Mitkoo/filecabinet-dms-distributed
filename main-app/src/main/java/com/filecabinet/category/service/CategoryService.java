package com.filecabinet.category.service;

import com.filecabinet.category.model.Category;
import com.filecabinet.category.repository.CategoryRepository;
import com.filecabinet.shared.exception.ServiceExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @CacheEvict(value = "categories", allEntries = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Category create(String name, String description) {
        if (categoryRepository.existsByName(name)) {
            throw new ServiceExceptions.DuplicateException("Category already exists: " + name);
        }
        Category category = Category.builder()
                .name(name)
                .description(description)
                .build();
        Category saved = categoryRepository.save(category);
        log.info("Created category {}", saved.getName());
        return saved;
    }

    @Cacheable("categories")
    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categoryRepository.findAllByOrderByNameAsc();
    }
}
