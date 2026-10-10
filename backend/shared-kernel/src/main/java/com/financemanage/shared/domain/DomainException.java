package com.financemanage.shared.domain;

/** Business rule violation. Mapped to HTTP 422 by the global exception handler. */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
