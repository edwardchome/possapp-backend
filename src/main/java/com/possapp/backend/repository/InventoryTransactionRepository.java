package com.possapp.backend.repository;

import com.possapp.backend.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, String> {
    
    List<InventoryTransaction> findByProductCodeOrderByCreatedAtDesc(String productCode);
    
    List<InventoryTransaction> findByTypeOrderByCreatedAtDesc(InventoryTransaction.TransactionType type);
    
    @Query("SELECT it FROM InventoryTransaction it WHERE it.createdAt >= :startDate AND it.createdAt <= :endDate ORDER BY it.createdAt DESC")
    List<InventoryTransaction> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT it FROM InventoryTransaction it WHERE it.productCode = :productCode AND it.createdAt >= :startDate AND it.createdAt <= :endDate ORDER BY it.createdAt DESC")
    List<InventoryTransaction> findByProductCodeAndDateRange(@Param("productCode") String productCode, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(it.quantity) FROM InventoryTransaction it WHERE it.type = 'STOCK_IN' AND it.createdAt >= :startDate AND it.createdAt <= :endDate")
    Integer getTotalStockInByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(it.totalCost) FROM InventoryTransaction it WHERE it.type = 'STOCK_IN' AND it.createdAt >= :startDate AND it.createdAt <= :endDate")
    java.math.BigDecimal getTotalInventoryValueByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
