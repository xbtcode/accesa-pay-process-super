package ro.accesa.payment.domain;

import java.util.Map;
import java.util.Set;

public enum TransactionStatus {
    INITIATED,
    QR_GENERATED,
    CUSTOMER_OPENED,
    CUSTOMER_CONFIRMED,
    PROCESSING,
    APPROVED,
    REJECTED,
    FAILED,
    EXPIRED,
    CANCELLED;

    private static final Map<TransactionStatus, Set<TransactionStatus>> ALLOWED_TRANSITIONS = Map.of(
            INITIATED, Set.of(QR_GENERATED, CANCELLED, EXPIRED),
            QR_GENERATED, Set.of(CUSTOMER_OPENED, CANCELLED, EXPIRED),
            CUSTOMER_OPENED, Set.of(CUSTOMER_CONFIRMED, CANCELLED, EXPIRED),
            CUSTOMER_CONFIRMED, Set.of(PROCESSING, FAILED, EXPIRED),
            PROCESSING, Set.of(APPROVED, REJECTED, FAILED)
    );

    public boolean canTransitionTo(TransactionStatus target) {
        Set<TransactionStatus> allowed = ALLOWED_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == FAILED
                || this == EXPIRED || this == CANCELLED;
    }
}
