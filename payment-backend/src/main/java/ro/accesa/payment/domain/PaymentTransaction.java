package ro.accesa.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("transactions")
public class PaymentTransaction {

    @Id
    private String id;

    @Indexed(unique = true)
    private String transactionRef;

    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private TransactionStatus status;

    private CreditorInfo creditor;
    private DebtorInfo debtor;
    private BankIntegrationInfo bankIntegration;

    @Builder.Default
    private List<StatusHistoryEntry> statusHistory = new ArrayList<>();

    private String qrPayload;
    private String failureReason;

    private Instant initiatedAt;
    private Instant confirmedAt;
    private Instant completedAt;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;

    public void addStatusHistory(TransactionStatus from, TransactionStatus to, String reason, String actor) {
        if (statusHistory == null) {
            statusHistory = new ArrayList<>();
        }
        statusHistory.add(StatusHistoryEntry.builder()
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .actor(actor)
                .timestamp(Instant.now())
                .build());
    }
}
