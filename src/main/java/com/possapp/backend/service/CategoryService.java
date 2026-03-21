package com.possapp.backend.service;

import com.possapp.backend.dto.CategoryDto;
import com.possapp.backend.entity.Category;
import com.possapp.backend.exception.ResourceNotFoundException;
import com.possapp.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {
    
    private final CategoryRepository categoryRepository;
    
    @Transactional(readOnly = true)
    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public CategoryDto getCategoryById(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        return mapToDto(category);
    }
    
    @Transactional
    public CategoryDto createCategory(CategoryDto dto) {
        if (categoryRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("Category with this name already exists");
        }
        
        Category category = Category.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .displayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0)
                .active(true)
                .build();
        
        category = categoryRepository.save(category);
        log.info("Created category: {}", category.getName());
        return mapToDto(category);
    }
    
    @Transactional
    public CategoryDto updateCategory(String id, CategoryDto dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        
        // Check if name is being changed and if new name already exists
        if (!category.getName().equals(dto.getName()) && categoryRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("Category with this name already exists");
        }
        
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        if (dto.getDisplayOrder() != null) {
            category.setDisplayOrder(dto.getDisplayOrder());
        }
        
        category = categoryRepository.save(category);
        log.info("Updated category: {}", category.getName());
        return mapToDto(category);
    }
    
    @Transactional
    public void deleteCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        
        category.setActive(false);
        categoryRepository.save(category);
        log.info("Deactivated category: {}", category.getName());
    }
    
    @Transactional
    public CategoryDto reactivateCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        
        category.setActive(true);
        category = categoryRepository.save(category);
        log.info("Reactivated category: {}", category.getName());
        return mapToDto(category);
    }
    
    private CategoryDto mapToDto(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .displayOrder(category.getDisplayOrder())
                .active(category.isActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
