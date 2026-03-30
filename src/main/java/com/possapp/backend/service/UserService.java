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
        
        // Build authorities including role and custom permissions
        var authorities = new java.util.ArrayList<org.springframework.security.core.authority.SimpleGrantedAuthority>();
        authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole()));
        
        // Add custom permissions as authorities
        if (user.isCanManageInventory()) {
            authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("CAN_MANAGE_INVENTORY"));
        }
        if (user.isCanManageProducts()) {
            authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("CAN_MANAGE_PRODUCTS"));
        }
        
        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getEmail())
            .password(user.getPassword())
            .authorities(authorities)
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
        // Find user by token (exact match or token starts with provided code)
        User user = userRepository.findAll().stream()
            .filter(u -> {
                String storedToken = u.getPasswordResetToken();
                if (storedToken == null) return false;
                // Allow exact match OR stored token starts with provided token (for short code)
                return token.equals(storedToken) || storedToken.startsWith(token);
            })
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
            .filter(u -> {
                String storedToken = u.getPasswordResetToken();
                if (storedToken == null) return false;
                // Allow exact match OR stored token starts with provided token (for short code)
                return token.equals(storedToken) || storedToken.startsWith(token);
            })
            .anyMatch(User::isPasswordResetTokenValid);
    }
    
    public UserDto mapToDto(User user) {
        UserDto.UserDtoBuilder builder = UserDto.builder()
            .id(user.getId())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .fullName(user.getFullName())
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole())
            .canManageProducts(user.isCanManageProducts())
            .canManageInventory(user.isCanManageInventory())
            .active(user.isActive())
            .emailVerified(user.isEmailVerified())
            .passwordChangeRequired(user.isPasswordChangeRequired())
            .permissionsVersion(user.getPermissionsVersion())
            .createdAt(user.getCreatedAt())
            .lastLoginAt(user.getLastLoginAt());
        
        // Include branch information if assigned
        if (user.getBranch() != null) {
            builder.branchId(user.getBranch().getId());
            builder.branchName(user.getBranch().getName());
        }
        
        return builder.build();
    }
    
    @Transactional
    public void incrementPermissionsVersion(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserException("User not found: " + userId));
        user.setPermissionsVersion(user.getPermissionsVersion() + 1);
        userRepository.save(user);
        log.info("Permissions version incremented for user: {} to version: {}", user.getEmail(), user.getPermissionsVersion());
    }
    
    @Transactional(readOnly = true)
    public Long getPermissionsVersion(String userId) {
        return userRepository.findById(userId)
            .map(User::getPermissionsVersion)
            .orElse(null);
    }
}
