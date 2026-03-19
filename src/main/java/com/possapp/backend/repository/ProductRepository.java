package com.possapp.backend.repository;

import com.possapp.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    
    List<Product> findAllByActiveTrueOrderByNameAsc();
    
    Optional<Product> findByCodeAndActiveTrue(String code);
    
    List<Product> findByCategoryAndActiveTrueOrderByNameAsc(String category);
    
    List<Product> findByStockLessThanEqualAndActiveTrue(Integer threshold);
    
    boolean existsByCode(String code);
    
    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.code) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.category) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Product> searchProducts(@Param("query") String query);
    
    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.active = true ORDER BY p.category")
    List<String> findAllCategories();
    
    long countByActiveTrue();
    
    long countByStockLessThanEqualAndActiveTrue(Integer threshold);
}
