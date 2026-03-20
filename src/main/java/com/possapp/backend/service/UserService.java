package com.possapp.backend.service;

import com.possapp.backend.dto.UserDto;
import com.possapp.backend.entity.User;
import com.possapp.backend.exception.UserException;
import com.possapp.backend.repository.UserRepository;
import com.possapp.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String currentTenant = TenantContext.getCurrentTenant();
        log.info("Loading user by username: {} in tenant: {}", email, currentTenant);
        
        User user = userRepository.findByEmailAndActiveTrue(email)
            .orElseThrow(() -> {
                log.error("User not found: {} in tenant: {}", email, currentTenant);
                return new UsernameNotFoundException("User not found: " + email);
            });
        
        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getEmail())
            .password(user.getPassword())
            .roles(user.getRole())
            .accountLocked(!user.isActive())
            .build();
    }
    
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmailAndActiveTrue(email)
            .orElseThrow(() -> new UserException("User not found"));
    }
    
    @Transactional
    public User createAdminUser(String email, String encodedPassword) {
        User user = User.builder()
            .email(email)
            .password(encodedPassword)
            .role("ADMIN")
            .active(true)
            .emailVerified(true)
            .build();
        
        return userRepository.save(user);
    }
    
    @Transactional
    public User createUser(String email, String encodedPassword, String firstName, String lastName) {
        if (userRepository.existsByEmail(email)) {
            throw new UserException("Email already registered");
        }
        
        User user = User.builder()
            .email(email)
            .password(encodedPassword)
            .firstName(firstName)
            .lastName(lastName)
            .role("USER")
            .active(true)
            .build();
        
        return userRepository.save(user);
    }
    
    @Transactional
    public void updateLastLogin(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }
    
    @Transactional(readOnly = true)
    public UserDto getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        
        String email = authentication.getName();
        return userRepository.findByEmail(email)
            .map(this::mapToDto)
            .orElse(null);
    }
    
    @Transactional(readOnly = true)
    public UserDto getUserProfile(String email) {
        User user = findByEmail(email);
        return mapToDto(user);
    }
    
    @Transactional
    public UserDto updateUserProfile(String email, Map<String, Object> profileData) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UserException("User not found"));
        
        if (profileData.containsKey("firstName")) {
            user.setFirstName((String) profileData.get("firstName"));
        }
        if (profileData.containsKey("lastName")) {
            user.setLastName((String) profileData.get("lastName"));
        }
        if (profileData.containsKey("phoneNumber")) {
            user.setPhoneNumber((String) profileData.get("phoneNumber"));
        }
        
        user = userRepository.save(user);
        return mapToDto(user);
    }
    
    @Transactional
    public void deleteUser(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UserException("User not found"));
        user.setActive(false);
        userRepository.save(user);
    }
    
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UserException("User not found"));
        
        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new UserException("Current password is incorrect");
        }
        
        // Update password and clear passwordChangeRequired flag
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        userRepository.save(user);
    }
    
    @Transactional
    public String generatePasswordResetToken(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UserException("User not found"));
        
        // Generate random token
        String token = java.util.UUID.randomUUID().toString();
        
        // Set token expiry (1 hour)
        user.setPasswordResetToken(token);
        user.setPasswordResetExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);
        
        return token;
    }
    
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findAll().stream()
            .filter(u -> token.equals(u.getPasswordResetToken()))
            .findFirst()
            .orElseThrow(() -> new UserException("Invalid or expired token"));
        
        if (!user.isPasswordResetTokenValid()) {
            throw new UserException("Token has expired");
        }
        
        // Update password and clear token
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiry(null);
        user.setPasswordChangeRequired(false);
        userRepository.save(user);
    }
    
    @Transactional(readOnly = true)
    public boolean isPasswordResetTokenValid(String token) {
        return userRepository.findAll().stream()
            .filter(u -> token.equals(u.getPasswordResetToken()))
            .anyMatch(User::isPasswordResetTokenValid);
    }
    
    public UserDto mapToDto(User user) {
        return UserDto.builder()
            .id(user.getId())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .fullName(user.getFullName())
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole())
            .active(user.isActive())
            .emailVerified(user.isEmailVerified())
            .passwordChangeRequired(user.isPasswordChangeRequired())
            .createdAt(user.getCreatedAt())
            .lastLoginAt(user.getLastLoginAt())
            .build();
    }
}
