package org.pluribourse.domain.edition.entity;

import java.util.*;

public enum PhaseType {
    PREPARATION,
    DEPOSIT,
    SALE,
    POST_SALE,
    CLOSED;

    public static final List<PhaseType> ACTIVE = List.of(PREPARATION, DEPOSIT, SALE, POST_SALE);
}
