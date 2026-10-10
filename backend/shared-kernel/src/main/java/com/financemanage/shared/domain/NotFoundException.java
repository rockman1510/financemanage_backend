package com.financemanage.shared.domain;

/** Requested resource does not exist or does not belong to the current user. Mapped to HTTP 404. */
public class NotFoundException extends DomainException {

    public NotFoundException(String resource, Object id) {
        super("NOT_FOUND", resource + " " + id + " not found");
    }
}
