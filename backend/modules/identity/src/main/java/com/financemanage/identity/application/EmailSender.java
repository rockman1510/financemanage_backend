package com.financemanage.identity.application;

/** Outbound port for sending emails (SMTP in production, a log or an in-memory mailbox in dev/test). */
public interface EmailSender {

    void send(EmailMessage message);

    record EmailMessage(String to, String subject, String body) {
    }
}
