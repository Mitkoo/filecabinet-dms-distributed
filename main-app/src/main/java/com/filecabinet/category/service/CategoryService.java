package com.filecabinet.category.service;

import com.filecabinet.category.model.Category;
import com.filecabinet.category.repository.CategoryRepository;
import com.filecabinet.shared.exception.ServiceExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public Category create(String name, String description) {
        if (categoryRepository.findByName(name).isPresent()) {
            throw new ServiceExceptions.DuplicateException("Category already exists: " + name);
        }
        Category category = Category.builder()
                .name(name)
                .description(description)
                .build();
        return categoryRepository.save(category);
    }

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Category findById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ServiceExceptions.NotFoundException("Category not found: " + id));
    }

}
