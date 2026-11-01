package com.financemanage.ledger.api;

import java.util.UUID;

/** A category as other modules see it; {@code type} is "INCOME" or "EXPENSE". */
public record CategoryView(UUID id, String name, String type, UUID parentId) {
}
