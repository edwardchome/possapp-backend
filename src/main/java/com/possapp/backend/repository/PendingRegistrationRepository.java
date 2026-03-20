package com.possapp.backend.repository;

import com.possapp.backend.entity.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, String> {
    
    Optional<PendingRegistration> findByEmail(String email);
    
    Optional<PendingRegistration> findByVerificationToken(String token);
    
    boolean existsByEmail(String email);
    
    List<PendingRegistration> findByEmailVerifiedFalseAndTokenExpiryTimeBefore(LocalDateTime expiryTime);
    
    void deleteByEmail(String email);
}
