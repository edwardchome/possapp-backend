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
    
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.branch WHERE p.active = true ORDER BY p.name ASC")
    List<Product> findAllByActiveTrueOrderByNameAscWithBranch();
    
    Optional<Product> findByCodeAndActiveTrue(String code);
    
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.branch WHERE p.code = :code AND p.active = true")
    Optional<Product> findByCodeAndActiveTrueWithBranch(@Param("code") String code);
    
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
    
    // Branch-scoped queries - returns products for a specific branch plus shared products (no branch)
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.branch WHERE p.active = true AND " +
           "(p.branch.id = :branchId OR p.branch IS NULL) ORDER BY p.name ASC")
    List<Product> findAllByActiveTrueAndBranchIdOrderByNameAsc(@Param("branchId") String branchId);
    
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.branch WHERE p.active = true AND " +
           "(p.branch.id = :branchId OR p.branch IS NULL) AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.code) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.category.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Product> searchProductsByBranchId(@Param("query") String query, @Param("branchId") String branchId);
    
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.branch WHERE p.code = :code AND p.active = true AND " +
           "(p.branch.id = :branchId OR p.branch IS NULL)")
    Optional<Product> findByCodeAndActiveTrueAndBranchId(@Param("code") String code, @Param("branchId") String branchId);
}
