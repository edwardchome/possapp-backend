package com.possapp.backend.repository;

import com.possapp.backend.entity.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, String> {
    
    Optional<Receipt> findByIdAndVoidedFalse(String id);
    
    @Query("SELECT r FROM Receipt r LEFT JOIN FETCH r.branch WHERE r.id = :id AND r.voided = false")
    Optional<Receipt> findByIdAndVoidedFalseWithBranch(@Param("id") String id);
    
    Page<Receipt> findAllByVoidedFalseOrderByTimestampDesc(Pageable pageable);
    
    List<Receipt> findAllByVoidedFalseOrderByTimestampDesc();
    
    @Query("SELECT r FROM Receipt r LEFT JOIN FETCH r.branch WHERE r.voided = false ORDER BY r.timestamp DESC")
    List<Receipt> findAllByVoidedFalseOrderByTimestampDescWithBranch();
    
    @Query("SELECT r FROM Receipt r WHERE r.voided = false AND " +
           "r.timestamp BETWEEN :startDate AND :endDate " +
           "ORDER BY r.timestamp DESC")
    List<Receipt> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT r FROM Receipt r LEFT JOIN FETCH r.branch WHERE r.voided = false AND " +
           "r.timestamp BETWEEN :startDate AND :endDate " +
           "ORDER BY r.timestamp DESC")
    List<Receipt> findByDateRangeWithBranch(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT r FROM Receipt r LEFT JOIN FETCH r.branch WHERE r.voided = false AND " +
           "r.timestamp BETWEEN :startDate AND :endDate AND " +
           "r.branch.id = :branchId " +
           "ORDER BY r.timestamp DESC")
    List<Receipt> findByDateRangeAndBranch(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("branchId") String branchId
    );
    
    @Query("SELECT r FROM Receipt r LEFT JOIN FETCH r.branch WHERE r.voided = false AND " +
           "r.branch.id = :branchId " +
           "ORDER BY r.timestamp DESC")
    List<Receipt> findByBranchId(@Param("branchId") String branchId);
    
    @Query("SELECT r FROM Receipt r WHERE r.voided = false AND " +
           "(LOWER(r.customerName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(r.customerEmail) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "r.id LIKE CONCAT('%', :query, '%'))")
    List<Receipt> searchReceipts(@Param("query") String query);
    
    @Query("SELECT SUM(r.total) FROM Receipt r WHERE r.voided = false AND " +
           "r.timestamp BETWEEN :startDate AND :endDate")
    Double calculateTotalSales(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT COUNT(r) FROM Receipt r WHERE r.voided = false AND " +
           "r.timestamp BETWEEN :startDate AND :endDate")
    Long countReceipts(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    long countByVoidedFalse();
}
