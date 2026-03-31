package ro.accesa.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistoryEntry {
    private TransactionStatus fromStatus;
    private TransactionStatus toStatus;
    private String reason;
    private String actor;
    private Instant timestamp;
}
