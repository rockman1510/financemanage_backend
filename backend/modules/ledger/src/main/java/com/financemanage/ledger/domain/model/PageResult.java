package com.financemanage.ledger.domain.model;

import java.util.List;

/** One page of a query result and the total number of matching items. */
public record PageResult<T>(List<T> items, long total) {
}
