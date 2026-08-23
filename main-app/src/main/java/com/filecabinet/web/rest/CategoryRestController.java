package com.filecabinet.web.rest;

import com.filecabinet.category.model.Category;
import com.filecabinet.category.service.CategoryService;
import com.filecabinet.web.rest.dto.CategoryRequest;
import com.filecabinet.web.rest.dto.CategoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryRestController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.findAll().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        Category category = categoryService.create(request.name(), request.description());
        log.info("Created category {}", category.getName());
        return toResponse(category);
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription());
    }
}
