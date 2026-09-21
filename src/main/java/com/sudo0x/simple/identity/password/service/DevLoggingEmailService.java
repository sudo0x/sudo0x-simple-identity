package com.sudo0x.simple.identity.password.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Development email implementation: logs tokens to the console.
 * NEVER use in production — tokens would appear in logs.
 * Replace with a real email provider (SMTP, SendGrid, SES, etc.) for production.
 */
@Service
@Slf4j
public class DevLoggingEmailService implements EmailService {

    @Override
    public void sendPasswordResetEmail(String toEmail, String username, String resetToken) {
        log.info("""
                [DEV EMAIL - Password Reset]
                To:       {}
                Username: {}
                Token:    {}
                Link:     http://localhost:3000/reset-password?token={}
                """, toEmail, username, resetToken, resetToken);
    }

    @Override
    public void sendEmailVerificationEmail(String toEmail, String username, String verificationToken) {
        log.info("""
                [DEV EMAIL - Email Verification]
                To:       {}
                Username: {}
                Token:    {}
                Link:     http://localhost:3000/verify-email?token={}
                """, toEmail, username, verificationToken, verificationToken);
    }
}
