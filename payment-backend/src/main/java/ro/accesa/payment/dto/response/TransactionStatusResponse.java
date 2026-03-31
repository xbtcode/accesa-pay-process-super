package ro.accesa.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatusResponse {
    private String transactionId;
    private String transactionRef;
    private String status;
    private BigDecimal amount;
    private String currency;
    private Instant completedAt;
    private String bankReference;
}
