package com.possapp.backend.repository;

import com.possapp.backend.entity.Category;
import com.possapp.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    
    List<Product> findAllByActiveTrueOrderByNameAsc();
    
    Optional<Product> findByCodeAndActiveTrue(String code);
    
    // Query by category entity - used when you have the Category object
    List<Product> findByCategoryAndActiveTrueOrderByNameAsc(Category category);
    
    // Query by category ID - used when you only have the category ID string
    List<Product> findByCategoryIdAndActiveTrueOrderByNameAsc(String categoryId);
    
    List<Product> findByStockLessThanEqualAndActiveTrue(BigDecimal threshold);
    
    boolean existsByCode(String code);
    
    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.code) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.category.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Product> searchProducts(@Param("query") String query);
    
    // Get distinct category names from products (via category relationship)
    @Query("SELECT DISTINCT p.category.name FROM Product p WHERE p.active = true AND p.category IS NOT NULL ORDER BY p.category.name")
    List<String> findAllCategories();
    
    long countByActiveTrue();
    
    long countByStockLessThanEqualAndActiveTrue(BigDecimal threshold);
}
