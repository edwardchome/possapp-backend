package com.possapp.backend.repository;

import com.possapp.backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    
    List<Category> findByActiveTrueOrderByDisplayOrderAsc();
    
    List<Category> findAllByOrderByDisplayOrderAsc();
    
    List<Category> findByActiveTrueOrderByNameAsc();
    
    Optional<Category> findByName(String name);
    
    boolean existsByName(String name);
}
