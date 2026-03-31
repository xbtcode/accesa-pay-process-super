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
public class BankIntegrationInfo {
    private String painMessageId;
    private String bankReference;
    private String bankResponseCode;
    private String bankResponseReason;
    private Instant submittedAt;
    private Instant respondedAt;
}
