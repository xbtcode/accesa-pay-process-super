package ro.accesa.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultResponse {
    private String transactionRef;
    private String status;
    private String message;
    private Instant completedAt;
}
