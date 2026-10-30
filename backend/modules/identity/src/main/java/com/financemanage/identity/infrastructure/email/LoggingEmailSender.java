package com.financemanage.identity.infrastructure.email;

import com.financemanage.identity.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fallback when no SMTP server is configured: nothing is sent and the body (with its code) is not logged. */
class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    LoggingEmailSender() {
        log.warn("No SMTP server configured (spring.mail.host): verification emails are NOT sent");
    }

    @Override
    public void send(EmailMessage message) {
        log.info("Email not sent (no SMTP configured) to {}: {}", message.to(), message.subject());
    }
}
