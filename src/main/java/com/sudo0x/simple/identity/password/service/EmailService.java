package com.sudo0x.simple.identity.password.service;

/**
 * Abstraction for email delivery.
 * Swap implementations to integrate with a Notification Service or SMTP provider.
 */
public interface EmailService {

    void sendPasswordResetEmail(String toEmail, String username, String resetToken);

    void sendEmailVerificationEmail(String toEmail, String username, String verificationToken);
}
