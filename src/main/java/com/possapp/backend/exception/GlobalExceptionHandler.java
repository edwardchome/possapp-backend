package com.possapp.backend.exception;

import com.possapp.backend.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(TenantException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantException(TenantException e, HttpServletRequest request) {
        log.error("Tenant exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .error("Tenant Error")
                .message(e.getMessage())
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    @ExceptionHandler(UserException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserException(UserException e, HttpServletRequest request) {
        log.error("User exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .error("User Error")
                .message(e.getMessage())
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    @ExceptionHandler(ProductException.class)
    public ResponseEntity<ApiResponse<Void>> handleProductException(ProductException e, HttpServletRequest request) {
        log.error("Product exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .error("Product Error")
                .message(e.getMessage())
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    @ExceptionHandler(ReceiptException.class)
    public ResponseEntity<ApiResponse<Void>> handleReceiptException(ReceiptException e, HttpServletRequest request) {
        log.error("Receipt exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .error("Receipt Error")
                .message(e.getMessage())
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e, HttpServletRequest request) {
        log.error("Authentication failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .error("Authentication Failed")
                .message("Invalid email or password")
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.error("Access denied: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .error("Access Denied")
                .message("You don't have permission to access this resource")
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.<Map<String, String>>builder()
                .success(false)
                .error("Validation Error")
                .data(errors)
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    @ExceptionHandler(SubscriptionLimitExceededException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleSubscriptionLimitExceeded(
            SubscriptionLimitExceededException e, HttpServletRequest request) {
        log.warn("Subscription limit exceeded: {}", e.getMessage());
        
        // Build comprehensive error message
        String formattedMessage = buildDetailedSubscriptionMessage(e);
        
        Map<String, Object> details = new HashMap<>();
        details.put("limitType", e.getLimitType().name());
        details.put("currentUsage", e.getCurrentUsage());
        details.put("limit", e.getLimit());
        details.put("currentPlan", e.getCurrentPlan().name());
        details.put("currentPlanDisplay", e.getCurrentPlan().getDisplayName());
        details.put("suggestedPlan", e.getSuggestedPlan().name());
        details.put("suggestedPlanDisplay", e.getSuggestedPlan().getDisplayName());
        details.put("errorCode", e.getErrorCode());
        details.put("formattedMessage", formattedMessage);
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.<Map<String, Object>>builder()
                .success(false)
                .error("Subscription Limit Reached")
                .message(formattedMessage)
                .data(details)
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    private String buildDetailedSubscriptionMessage(SubscriptionLimitExceededException e) {
        StringBuilder message = new StringBuilder();
        String resourceName = e.getLimitType().getDisplayName();
        
        message.append("🚫 You've reached your ").append(resourceName.toLowerCase()).append(" limit!\n\n");
        message.append("📊 Usage: ").append(e.getCurrentUsage()).append(" / ").append(e.getLimit()).append("\n");
        message.append("💳 Current Plan: ").append(e.getCurrentPlan().getDisplayName()).append("\n\n");
        
        message.append("✅ Your plan includes:\n");
        switch (e.getCurrentPlan()) {
            case STARTER -> message.append("   • 2 users, 1 branch\n   • Basic POS features\n\n");
            case BUSINESS -> message.append("   • 5 users, 3 branches\n   • Reports & Barcode\n\n");
            case ENTERPRISE -> message.append("   • Unlimited everything\n   • All premium features\n\n");
        }
        
        message.append("🚀 Upgrade to ").append(e.getSuggestedPlan().getDisplayName()).append(" for more!\n");
        message.append("📧 Contact: sales@possapp.com");
        
        return message.toString();
    }
    
    @ExceptionHandler(com.possapp.backend.aspect.SubscriptionAspect.SubscriptionAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleSubscriptionAccessDenied(
            com.possapp.backend.aspect.SubscriptionAspect.SubscriptionAccessDeniedException e, HttpServletRequest request) {
        log.warn("Subscription access denied: {}", e.getMessage());
        
        Map<String, String> details = new HashMap<>();
        details.put("requiredPlan", e.getRequiredPlan().name());
        details.put("currentPlan", e.getCurrentPlan().name());
        details.put("errorCode", e.getErrorCode());
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.<Map<String, String>>builder()
                .success(false)
                .error("Subscription Required")
                .message(e.getMessage())
                .data(details)
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    @ExceptionHandler(com.possapp.backend.aspect.SubscriptionAspect.FeatureAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleFeatureAccessDenied(
            com.possapp.backend.aspect.SubscriptionAspect.FeatureAccessDeniedException e, HttpServletRequest request) {
        log.warn("Feature access denied: {}", e.getMessage());
        
        // Build comprehensive feature error message
        String formattedMessage = buildFeatureErrorMessage(e);
        
        Map<String, String> details = new HashMap<>();
        details.put("feature", e.getFeature().name());
        details.put("featureDisplay", e.getFeature().getDisplayName());
        details.put("currentPlan", e.getCurrentPlan().name());
        details.put("currentPlanDisplay", e.getCurrentPlan().getDisplayName());
        details.put("errorCode", e.getErrorCode());
        details.put("formattedMessage", formattedMessage);
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.<Map<String, String>>builder()
                .success(false)
                .error("Premium Feature Required")
                .message(formattedMessage)
                .data(details)
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e, HttpServletRequest request) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please try again later.")
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    private String buildFeatureErrorMessage(com.possapp.backend.aspect.SubscriptionAspect.FeatureAccessDeniedException e) {
        StringBuilder message = new StringBuilder();
        
        message.append("🔒 Premium Feature: ").append(e.getFeature().getDisplayName()).append("\n\n");
        message.append("This feature is not available on your ")
               .append(e.getCurrentPlan().getDisplayName())
               .append(" Plan.\n\n");
        
        message.append("✨ Upgrade to unlock:\n");
        switch (e.getFeature()) {
            case REPORTS -> message.append("   📈 Sales & Inventory Reports\n   📊 Business Analytics\n\n");
            case BARCODE_SCANNING -> message.append("   📱 Barcode Scanning\n   ⚡ Faster Checkout\n\n");
            case MULTI_BRANCH -> message.append("   🏪 Multiple Branch Support\n   📍 Location Management\n\n");
            case ANALYTICS -> message.append("   📊 Advanced Analytics\n   📈 Business Insights\n\n");
            case API_ACCESS -> message.append("   🔌 API Access\n   🔗 Third-party Integrations\n\n");
            default -> message.append("   ✨ Premium Features\n\n");
        }
        
        message.append("💳 Available on: Business or Enterprise plans\n");
        message.append("📧 Contact: sales@possapp.com");
        
        return message.toString();
    }
}
