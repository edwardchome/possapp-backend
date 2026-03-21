package com.possapp.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    @Value("${app.email.from:possapp@gmail.com}")
    private String fromEmail;
    
    /**
     * Send a simple text email
     */
    @Async
    public void sendSimpleEmail(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            
            mailSender.send(message);
            log.info("Simple email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
    
    /**
     * Send HTML email
     */
    @Async
    public void sendHtmlEmail(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            
            mailSender.send(message);
            log.info("HTML email sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send HTML email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
    
    /**
     * Send verification email
     */
    public void sendVerificationEmail(String toEmail, String tenantId, String token) {
        String subject = "Verify Your Email - PossApp";
        String verificationLink = buildVerificationLink(token, tenantId);
        String htmlBody = buildVerificationEmailBody(verificationLink, tenantId);
        
        sendHtmlEmail(toEmail, subject, htmlBody);
    }
    
    private String buildVerificationLink(String token, String tenantId) {
        // In production, this should come from configuration
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        return baseUrl + "/api/v1/auth/verify-email?token=" + token + "&tenantId=" + tenantId;
    }
    
    private String buildVerificationEmailBody(String verificationLink, String tenantId) {
        return String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "    <meta charset=\"UTF-8\">" +
            "    <style>" +
            "        body { font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }" +
            "        .container { max-width: 600px; margin: 0 auto; background-color: white; border-radius: 10px; padding: 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
            "        .header { text-align: center; margin-bottom: 30px; }" +
            "        .header h1 { color: #4a6cf7; margin: 0; }" +
            "        .content { margin-bottom: 30px; }" +
            "        .button { display: inline-block; background-color: #4a6cf7; color: #ffffff !important; font-weight: bold; padding: 15px 30px; text-decoration: none; border-radius: 5px; margin: 20px 0; -webkit-text-fill-color: #ffffff !important; }" +
            "        .info { background-color: #f8f9fa; padding: 15px; border-radius: 5px; margin: 15px 0; word-break: break-all; }" +
            "        .footer { text-align: center; color: #666; font-size: 12px; margin-top: 30px; }" +
            "    </style>" +
            "</head>" +
            "<body>" +
            "    <div class=\"container\">" +
            "        <div class=\"header\">" +
            "            <h1>Welcome to PossApp!</h1>" +
            "        </div>" +
            "        <div class=\"content\">" +
            "            <h2>Verify your email address</h2>" +
            "            <p>Thank you for creating an account. Please click the button below to verify your email address:</p>" +
            "            <center><a href=\"%s\" style=\"background-color: #4a6cf7; border: 12px solid #4a6cf7; border-radius: 5px; color: #ffffff !important; display: inline-block; font-family: Arial, sans-serif; font-size: 16px; font-weight: bold; line-height: 1; text-decoration: none; text-align: center; mso-hide: all; padding: 3px 18px;\"><span style=\"color: #ffffff !important; font-family: Arial, sans-serif; font-size: 16px; font-weight: bold; line-height: 1; text-decoration: none; mso-line-height-rule: exactly;\">Verify Email & Continue</span></a></center>" +
            "            <p>Or copy and paste this link into your browser:</p>" +
            "            <div class=\"info\">%s</div>" +
            "            <p><strong>Business ID:</strong> %s</p>" +
            "            <p>This link will expire in 24 hours.</p>" +
            "        </div>" +
            "        <div class=\"footer\">" +
            "            <p>If you didn't create this account, you can safely ignore this email.</p>" +
            "            <p>&copy; 2025 PossApp. All rights reserved.</p>" +
            "        </div>" +
            "    </div>" +
            "</body>" +
            "</html>",
            verificationLink, verificationLink, tenantId
        );
    }
    
    /**
     * Send password reset email
     */
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String subject = "Password Reset - PossApp";
        String resetLink = buildPasswordResetLink(resetToken);
        String htmlBody = buildPasswordResetEmailBody(resetLink);
        
        sendHtmlEmail(toEmail, subject, htmlBody);
    }
    
    private String buildPasswordResetLink(String token) {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        return baseUrl + "/api/v1/auth/reset-password?token=" + token;
    }
    
    private String buildPasswordResetEmailBody(String resetLink) {
        return String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "    <meta charset=\"UTF-8\">" +
            "    <style>" +
            "        body { font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }" +
            "        .container { max-width: 600px; margin: 0 auto; background-color: white; border-radius: 10px; padding: 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
            "        .header { text-align: center; margin-bottom: 30px; }" +
            "        .header h1 { color: #4a6cf7; margin: 0; }" +
            "        .content { margin-bottom: 30px; }" +
            "        .button { display: inline-block; background-color: #4a6cf7; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; margin: 20px 0; }" +
            "        .info { background-color: #f8f9fa; padding: 15px; border-radius: 5px; margin: 15px 0; word-break: break-all; }" +
            "        .footer { text-align: center; color: #666; font-size: 12px; margin-top: 30px; }" +
            "        .warning { background-color: #fff3cd; border: 1px solid #ffc107; padding: 10px; border-radius: 5px; margin: 15px 0; }" +
            "    </style>" +
            "</head>" +
            "<body>" +
            "    <div class=\"container\">" +
            "        <div class=\"header\">" +
            "            <h1>Password Reset Request</h1>" +
            "        </div>" +
            "        <div class=\"content\">" +
            "            <h2>Reset your password</h2>" +
            "            <p>You recently requested to reset your password. Click the button below to reset it:</p>" +
            "            <center><a href=\"%s\" class=\"button\">Reset Password</a></center>" +
            "            <p>Or copy and paste this link into your browser:</p>" +
            "            <div class=\"info\">%s</div>" +
            "            <div class=\"warning\">" +
            "                <strong>Important:</strong> This link will expire in 1 hour for security reasons." +
            "            </div>" +
            "        </div>" +
            "        <div class=\"footer\">" +
            "            <p>If you didn't request a password reset, you can safely ignore this email.</p>" +
            "            <p>&copy; 2025 PossApp. All rights reserved.</p>" +
            "        </div>" +
            "    </div>" +
            "</body>" +
            "</html>",
            resetLink, resetLink
        );
    }
    
    /**
     * Send welcome email to new user with login credentials
     */
    public void sendWelcomeEmail(String toEmail, String plainPassword, String tenantId, String firstName) {
        String subject = "Welcome to PossApp - Your Account Details";
        String loginUrl = buildLoginUrl();
        String htmlBody = buildWelcomeEmailBody(toEmail, plainPassword, tenantId, firstName, loginUrl);
        
        sendHtmlEmail(toEmail, subject, htmlBody);
    }
    
    private String buildLoginUrl() {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        return baseUrl;
    }
    
    private String buildWelcomeEmailBody(String email, String password, String tenantId, String firstName, String loginUrl) {
        String name = firstName != null && !firstName.isEmpty() ? firstName : email;
        return String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "    <meta charset=\"UTF-8\">" +
            "    <style>" +
            "        body { font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }" +
            "        .container { max-width: 600px; margin: 0 auto; background-color: white; border-radius: 10px; padding: 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
            "        .header { text-align: center; margin-bottom: 30px; }" +
            "        .header h1 { color: #4a6cf7; margin: 0; }" +
            "        .content { margin-bottom: 30px; }" +
            "        .credentials { background-color: #e8f4f8; border-left: 4px solid #4a6cf7; padding: 15px; margin: 15px 0; }" +
            "        .credentials p { margin: 8px 0; }" +
            "        .credentials strong { color: #333; }" +
            "        .warning { background-color: #fff3cd; border: 1px solid #ffc107; padding: 15px; border-radius: 5px; margin: 15px 0; color: #856404; }" +
            "        .button { display: inline-block; background-color: #4a6cf7; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; margin: 20px 0; }" +
            "        .footer { text-align: center; color: #666; font-size: 12px; margin-top: 30px; }" +
            "    </style>" +
            "</head>" +
            "<body>" +
            "    <div class=\"container\">" +
            "        <div class=\"header\">" +
            "            <h1>Welcome to PossApp!</h1>" +
            "        </div>" +
            "        <div class=\"content\">" +
            "            <h2>Hello %s,</h2>" +
            "            <p>An admin has created an account for you. Here are your login details:</p>" +
            "            <div class=\"credentials\">" +
            "                <p><strong>Email:</strong> %s</p>" +
            "                <p><strong>Password:</strong> %s</p>" +
            "                <p><strong>Business ID:</strong> %s</p>" +
            "            </div>" +
            "            <div class=\"warning\">" +
            "                <strong>⚠️ Important:</strong> You will be required to change your password when you first log in for security reasons." +
            "            </div>" +
            "            <center><a href=\"%s\" class=\"button\">Login to PossApp</a></center>" +
            "            <p>Or copy and paste this URL into your browser:</p>" +
            "            <div style=\"background-color: #f8f9fa; padding: 10px; border-radius: 5px; word-break: break-all;\">%s</div>" +
            "        </div>" +
            "        <div class=\"footer\">" +
            "            <p>If you have any questions, please contact your administrator.</p>" +
            "            <p>&copy; 2025 PossApp. All rights reserved.</p>" +
            "        </div>" +
            "    </div>" +
            "</body>" +
            "</html>",
            name, email, password, tenantId, loginUrl, loginUrl
        );
    }
    
    public void sendPasswordResetEmail(String toEmail, String resetToken, String firstName, String tenantId) {
        String subject = "Reset Your PossApp Password";
        String name = firstName != null && !firstName.isEmpty() ? firstName : toEmail;
        
        // Format token for easier copying (first 8 chars)
        String tokenCode = resetToken.substring(0, Math.min(8, resetToken.length()));
        
        String htmlBody = String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "    <meta charset=\"UTF-8\">" +
            "    <style>" +
            "        body { font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }" +
            "        .container { max-width: 600px; margin: 0 auto; background-color: white; border-radius: 10px; padding: 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
            "        .header { text-align: center; margin-bottom: 30px; }" +
            "        .header h1 { color: #4a6cf7; margin: 0; }" +
            "        .content { margin-bottom: 30px; }" +
            "        .code-box { background-color: #f8f9fa; border: 2px solid #4a6cf7; padding: 20px; border-radius: 8px; margin: 20px 0; text-align: center; }" +
            "        .code { font-family: monospace; font-size: 24px; font-weight: bold; color: #4a6cf7; letter-spacing: 2px; }" +
            "        .warning { background-color: #fff3cd; border: 1px solid #ffc107; padding: 15px; border-radius: 5px; margin: 15px 0; color: #856404; }" +
            "        .footer { text-align: center; color: #666; font-size: 12px; margin-top: 30px; }" +
            "    </style>" +
            "</head>" +
            "<body>" +
            "    <div class=\"container\">" +
            "        <div class=\"header\">" +
            "            <h1>Password Reset Request</h1>" +
            "        </div>" +
            "        <div class=\"content\">" +
            "            <h2>Hello %s,</h2>" +
            "            <p>We received a request to reset your PossApp password. Use the code below in the mobile app to complete the reset:</p>" +
            "            <div class=\"code-box\">" +
            "                <div class=\"code\">%s</div>" +
            "            </div>" +
            "            <p>Or copy the full token:</p>" +
            "            <div style=\"background-color: #f8f9fa; padding: 10px; border-radius: 5px; word-break: break-all; font-family: monospace; font-size: 12px;\">%s</div>" +
            "            <div class=\"warning\">" +
            "                <strong>⚠️ Important:</strong> This code will expire in 1 hour for security reasons." +
            "            </div>" +
            "            <p>If you didn't request this password reset, please ignore this email. Your password will remain unchanged.</p>" +
            "        </div>" +
            "        <div class=\"footer\">" +
            "            <p>If you have any questions, please contact your administrator.</p>" +
            "            <p>&copy; 2025 PossApp. All rights reserved.</p>" +
            "        </div>" +
            "    </div>" +
            "</body>" +
            "</html>",
            name, tokenCode, resetToken
        );
        
        sendHtmlEmail(toEmail, subject, htmlBody);
    }
}
