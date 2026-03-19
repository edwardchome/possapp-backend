package com.possapp.backend.repository;

import com.possapp.backend.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {
    
    Optional<Tenant> findBySchemaName(String schemaName);
    
    Optional<Tenant> findByAdminEmail(String adminEmail);
    
    boolean existsBySchemaName(String schemaName);
    
    boolean existsByAdminEmail(String adminEmail);
    
    @Query("SELECT t FROM Tenant t WHERE t.schemaName = :schemaName AND t.active = true")
    Optional<Tenant> findActiveBySchemaName(@Param("schemaName") String schemaName);
}
