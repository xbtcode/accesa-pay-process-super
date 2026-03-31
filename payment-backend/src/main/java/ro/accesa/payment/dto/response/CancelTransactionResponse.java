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
public class CancelTransactionResponse {
    private String transactionId;
    private String status;
    private Instant cancelledAt;
}
