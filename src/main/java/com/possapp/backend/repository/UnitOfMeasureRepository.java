package com.possapp.backend.repository;

import com.possapp.backend.entity.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, String> {
    
    List<UnitOfMeasure> findByActiveTrue();
    
    List<UnitOfMeasure> findByTypeAndActiveTrue(UnitOfMeasure.UnitType type);
    
    Optional<UnitOfMeasure> findBySymbolIgnoreCase(String symbol);
    
    boolean existsBySymbol(String symbol);
}
