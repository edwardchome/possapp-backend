package com.possapp.backend.repository;

import com.possapp.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByEmailAndActiveTrue(String email);
    
    boolean existsByEmail(String email);
    
    long countByActiveTrue();
    
    /**
     * Find all users with their branch information eagerly loaded.
     * This prevents LazyInitializationException when accessing branch data.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.branch")
    List<User> findAllWithBranch();
    
    /**
     * Find user by ID with branch eagerly loaded.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.branch WHERE u.id = :id")
    Optional<User> findByIdWithBranch(String id);
}
