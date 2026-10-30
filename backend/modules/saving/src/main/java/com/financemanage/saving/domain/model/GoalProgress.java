package com.financemanage.saving.domain.model;

import com.financemanage.shared.domain.Money;

/**
 * How far a goal is. {@code monthlySuggestion} = what is left / months until the deadline (at least one), null without
 * a deadline; {@code daysLeft} is null without a deadline.
 */
public record GoalProgress(int percent, Money remaining, Money monthlySuggestion, Long daysLeft, boolean reached) {
}
