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
public class PaymentDetailsResponse {
    private String transactionRef;
    private String merchantName;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String creditorName;
    private String status;
    private Instant expiresAt;
}
