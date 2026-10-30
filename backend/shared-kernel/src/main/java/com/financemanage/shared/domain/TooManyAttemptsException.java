package com.financemanage.shared.domain;

/** Too many attempts in a short time (locked account, resend cooldown…). Mapped to HTTP 429. */
public class TooManyAttemptsException extends DomainException {

    public TooManyAttemptsException(String code, String message) {
        super(code, message);
    }
}
