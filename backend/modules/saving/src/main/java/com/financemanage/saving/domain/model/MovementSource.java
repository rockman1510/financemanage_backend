package com.financemanage.saving.domain.model;

public enum MovementSource {
    MANUAL,
    /** Automatic contribution on a schedule */
    AUTO,
    /** Rounding up of an expense, e.g. 47 000 → 50 000 puts 3 000 aside */
    ROUND_UP
}
